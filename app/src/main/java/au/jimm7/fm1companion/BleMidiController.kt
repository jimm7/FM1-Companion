package au.jimm7.fm1companion

import android.Manifest
import android.annotation.SuppressLint
import android.bluetooth.BluetoothDevice
import android.bluetooth.BluetoothGatt
import android.bluetooth.BluetoothGattCallback
import android.bluetooth.BluetoothProfile
import android.bluetooth.BluetoothManager
import android.bluetooth.le.ScanCallback
import android.bluetooth.le.ScanResult
import android.bluetooth.le.ScanSettings
import android.content.Context
import android.content.pm.PackageManager
import android.media.midi.MidiDevice
import android.media.midi.MidiInputPort
import android.media.midi.MidiManager
import android.media.midi.MidiOutputPort
import android.media.midi.MidiReceiver
import android.os.Build
import android.os.Handler
import android.os.Looper
import android.os.ParcelUuid
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import java.io.IOException
import java.util.UUID
import java.util.concurrent.atomic.AtomicBoolean

data class BleCandidate(
    val device: BluetoothDevice,
    val name: String,
    val address: String,
    val midiAdvertised: Boolean
)

/** Uses Android's BLE MIDI API. Does not assume Caravel firmware implements BLE MIDI. */
@SuppressLint("MissingPermission")
class BleMidiController(private val context: Context) {
    private val main = Handler(Looper.getMainLooper())
    private val bt = context.getSystemService(BluetoothManager::class.java)?.adapter
    private val midiManager = context.getSystemService(MidiManager::class.java)
    private val midiUuid = ParcelUuid.fromString("03B80E5A-EDE8-4B33-A751-6CE34EC4C700")
    private var scanCallback: ScanCallback? = null
    private var verificationGatt: BluetoothGatt? = null
    private var openedDevice: MidiDevice? = null
    private var sendPort: MidiInputPort? = null
    private var receivePort: MidiOutputPort? = null
    @Volatile private var session = 0
    private var receivedScanPackets = 0

    val nearby = mutableStateListOf<BleCandidate>()
    val events = mutableStateListOf<String>()
    var status by mutableStateOf("Ready. Scan Bluetooth to find your FM-1.")
        private set
    var connected by mutableStateOf(false)
        private set
    var channel by mutableIntStateOf(1)
        private set

    fun requiredPermissions(): Array<String> =
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S)
            arrayOf(Manifest.permission.BLUETOOTH_SCAN, Manifest.permission.BLUETOOTH_CONNECT)
        else arrayOf(Manifest.permission.ACCESS_FINE_LOCATION)

    fun hasPermissions() = requiredPermissions().all {
        context.checkSelfPermission(it) == PackageManager.PERMISSION_GRANTED
    }

    fun bluetoothEnabled() = try { bt?.isEnabled == true }
        catch (_: SecurityException) { false }

    fun report(message: String) {
        if (Looper.myLooper() != Looper.getMainLooper()) {
            main.post { report(message) }
            return
        }
        status = message
        events.add(0, message)
        if (events.size > 40) events.removeAt(events.lastIndex)
    }

    fun adjustChannel(delta: Int) { channel = (channel + delta).coerceIn(1, 16) }

    fun scan() {
        if (!hasPermissions()) { report("Nearby devices permission required."); return }
        if (!bluetoothEnabled()) { report("Enable Bluetooth first."); return }
        val scanner = bt?.bluetoothLeScanner
        if (scanner == null) { report("BLE scanner unavailable."); return }
        stopScan()
        nearby.clear()
        receivedScanPackets = 0
        val callback = object : ScanCallback() {
            override fun onScanResult(callbackType: Int, result: ScanResult) {
                main.post { onResult(result) }
            }
            override fun onBatchScanResults(results: MutableList<ScanResult>) {
                main.post { results.forEach(::onResult) }
            }
            override fun onScanFailed(errorCode: Int) {
                main.post { stopScan(); report("Scan failed: Android code " + errorCode) }
            }
        }
        scanCallback = callback
        try {
            scanner.startScan(null, ScanSettings.Builder()
                .setScanMode(ScanSettings.SCAN_MODE_LOW_LATENCY).build(), callback)
        } catch (e: Exception) {
            scanCallback = null
            report("Scanning failed: " + e.message)
            return
        }
        report("Scanning for 12 seconds (Android API " + Build.VERSION.SDK_INT + "; Nearby devices permission granted)…")
        main.postDelayed({
            if (scanCallback === callback) {
                stopScan()
                report("Scan completed: " + nearby.size + " unique device(s), " + receivedScanPackets + " advertisement callback(s)." +
                    if (receivedScanPackets == 0) " No scan results reached the app; compare with nRF Connect." else "")
            }
        }, 12_000L)
    }

    private fun onResult(result: ScanResult) {
        if (scanCallback == null) return
        receivedScanPackets++
        val device = result.device
        val address = device.address
        val name = result.scanRecord?.deviceName
            ?: try { device.name } catch (_: SecurityException) { null }
            ?: "Unnamed BLE device"
        val midi = result.scanRecord?.serviceUuids?.contains(midiUuid) == true
        val index = nearby.indexOfFirst { it.address == address }
        if (index >= 0) {
            val old = nearby[index]
            val newMidi = old.midiAdvertised || midi
            if (name != old.name || newMidi != old.midiAdvertised)
                nearby[index] = BleCandidate(device, name, address, newMidi)
        } else if (nearby.size < 50) {
            nearby.add(BleCandidate(device, name, address, midi))
        }
    }

    fun stopScan() {
        val callback = scanCallback ?: return
        scanCallback = null
        try {
            if (hasPermissions()) bt?.bluetoothLeScanner?.stopScan(callback)
        } catch (_: Exception) { }
    }

    /** Confirm actual GATT MIDI service/characteristic before opening Android MIDI. */
    fun connect(candidate: BleCandidate) {
        if (!hasPermissions()) { report("Bluetooth permission missing."); return }
        if (midiManager == null) { report("Android MIDI is unavailable."); return }
        stopScan()
        disconnect()
        val currentSession = ++session
        val finished = AtomicBoolean(false)
        report("Checking BLE services on " + candidate.name + "…")
        fun complete(gatt: BluetoothGatt, verified: Boolean, message: String) {
            if (!finished.compareAndSet(false, true)) return
            try { gatt.disconnect() } catch (_: Exception) { }
            try { gatt.close() } catch (_: Exception) { }
            main.post {
                if (currentSession != session) return@post
                verificationGatt = null
                report(message)
                if (verified) main.postDelayed({
                    if (currentSession == session) openMidiPort(candidate, currentSession)
                }, 400L)
            }
        }
        val callback = object : BluetoothGattCallback() {
            override fun onConnectionStateChange(gatt: BluetoothGatt, status: Int, newState: Int) {
                if (finished.get()) return
                if (currentSession != session) {
                    complete(gatt, false, "Verification cancelled.")
                } else if (status != BluetoothGatt.GATT_SUCCESS) {
                    complete(gatt, false, "GATT connection failed: " + status)
                } else if (newState == BluetoothProfile.STATE_CONNECTED) {
                    try {
                        if (!gatt.discoverServices()) complete(gatt, false, "Cannot discover BLE services.")
                    } catch (e: Exception) {
                        complete(gatt, false, "BLE discovery error: " + e.message)
                    }
                } else if (newState == BluetoothProfile.STATE_DISCONNECTED) {
                    complete(gatt, false, "Disconnected before BLE verification.")
                }
            }

            override fun onServicesDiscovered(gatt: BluetoothGatt, status: Int) {
                if (finished.get()) return
                if (status != BluetoothGatt.GATT_SUCCESS) {
                    complete(gatt, false, "Service discovery failed: " + status)
                    return
                }
                val midiService = gatt.getService(midiUuid.uuid)
                val characteristic = UUID.fromString("7772E5DB-3868-4112-A1A9-F2669D106BF3")
                val isMidi = midiService?.getCharacteristic(characteristic) != null
                val found = gatt.services.joinToString(", ") { it.uuid.toString().take(8) }
                if (isMidi) {
                    complete(gatt, true, "Standard BLE-MIDI service verified.")
                } else {
                    complete(gatt, false, "No BLE-MIDI service on " + candidate.name +
                        ". GATT services: " + found.ifBlank { "none" })
                }
            }
        }
        try {
            verificationGatt = candidate.device.connectGatt(
                context, false, callback, BluetoothDevice.TRANSPORT_LE
            )
            if (verificationGatt == null) {
                finished.set(true)
                report("BLE verification did not start.")
                return
            }
        } catch (e: Exception) {
            finished.set(true)
            report("BLE verification failed: " + e.message)
            return
        }
        main.postDelayed({
            if (currentSession == session && !finished.get()) {
                verificationGatt?.let { complete(it, false, "BLE verification timed out.") }
            }
        }, 12_000L)
    }

    private fun openMidiPort(candidate: BleCandidate, currentSession: Int) {
        val manager = midiManager ?: return
        try {
            manager.openBluetoothDevice(candidate.device, { device ->
                main.post {
                    if (session != currentSession) {
                        device?.close()
                    } else if (device == null) {
                        report("BLE MIDI not available. This firmware may lack the service.")
                    } else {
                        val port = device.openInputPort(0)
                        if (port == null) {
                            device.close()
                            report("MIDI device opened, but no writable MIDI port exists.")
                        } else {
                            openedDevice = device
                            sendPort = port
                            connected = true
                            report("Verified BLE-MIDI; MIDI port open: " + candidate.name)
                            if (device.info.outputPortCount > 0) {
                                try {
                                    val rx = device.openOutputPort(0)
                                    rx?.connect(object : MidiReceiver() {
                                        override fun onSend(
                                            msg: ByteArray, offset: Int, count: Int, timestamp: Long
                                        ) {
                                            val hex = msg.copyOfRange(offset, offset + count)
                                                .joinToString(" ") {
                                                    "%02X".format(it.toInt() and 0xFF)
                                                }
                                            main.post {
                                                events.add(0, "MIDI RX: " + hex)
                                                if (events.size > 40) events.removeAt(events.lastIndex)
                                            }
                                        }
                                    })
                                    receivePort = rx
                                } catch (e: IOException) {
                                    report("MIDI receive monitoring unavailable: " + e.message)
                                }
                            }
                        }
                    }
                }
            }, main)
        } catch (e: Exception) {
            report("MIDI connection failed: " + e.message)
        }
    }

    private fun send(message: ByteArray) {
        try { sendPort?.send(message, 0, message.size) }
        catch (e: IOException) { report("MIDI send failed: " + e.message) }
    }

    fun play(note: Int) {
        if (!connected) return
        val sendChannel = channel
        val currentSession = session
        send(MidiMessages.noteOn(sendChannel, note))
        main.postDelayed({
            if (session == currentSession) send(MidiMessages.noteOff(sendChannel, note))
        }, 220L)
    }

    fun disconnect() {
        session++
        try { verificationGatt?.disconnect() } catch (_: Exception) { }
        try { verificationGatt?.close() } catch (_: Exception) { }
        verificationGatt = null
        if (sendPort != null) {
            for (ch in 1..16) send(MidiMessages.allNotesOff(ch))
        }
        try { receivePort?.close() } catch (_: IOException) { }
        try { sendPort?.close() } catch (_: IOException) { }
        try { openedDevice?.close() } catch (_: IOException) { }
        receivePort = null
        sendPort = null
        openedDevice = null
        connected = false
    }

    fun close() { stopScan(); disconnect() }
}
