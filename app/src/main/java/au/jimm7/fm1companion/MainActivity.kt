package au.jimm7.fm1companion

import android.bluetooth.BluetoothAdapter
import android.content.Intent
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp

class MainActivity : ComponentActivity() {
    private lateinit var midi: BleMidiController

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        midi = BleMidiController(this)
        setContent {
            val enableBt = rememberLauncherForActivityResult(
                ActivityResultContracts.StartActivityForResult()
            ) {
                if (midi.bluetoothEnabled()) midi.scan()
                else midi.report("Bluetooth was not enabled.")
            }
            val permissions = rememberLauncherForActivityResult(
                ActivityResultContracts.RequestMultiplePermissions()
            ) {
                if (!midi.hasPermissions()) midi.report("Nearby devices permission denied.")
                else if (!midi.bluetoothEnabled())
                    enableBt.launch(Intent(BluetoothAdapter.ACTION_REQUEST_ENABLE))
                else midi.scan()
            }
            MaterialTheme(colorScheme = darkColorScheme()) {
                Surface(modifier = Modifier.fillMaxSize()) {
                    CompanionScreen(midi) {
                        when {
                            !midi.hasPermissions() -> permissions.launch(midi.requiredPermissions())
                            !midi.bluetoothEnabled() ->
                                enableBt.launch(Intent(BluetoothAdapter.ACTION_REQUEST_ENABLE))
                            else -> midi.scan()
                        }
                    }
                }
            }
        }
    }

    override fun onDestroy() {
        midi.close()
        super.onDestroy()
    }
}

@Composable
private fun CompanionScreen(midi: BleMidiController, requestScan: () -> Unit) {
    val labels = remember {
        listOf("C4", "C♯4", "D4", "D♯4", "E4", "F4", "F♯4",
            "G4", "G♯4", "A4", "A♯4", "B4", "C5")
    }
    Column(
        modifier = Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(18.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp)
    ) {
        Text("FM-1 Companion", style = MaterialTheme.typography.headlineMedium,
            fontWeight = FontWeight.Bold)
        Text("Bluetooth MIDI diagnostic · v0.1.0", style = MaterialTheme.typography.bodySmall)
        Card(modifier = Modifier.fillMaxWidth()) {
            Column(Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                Text("Connection", style = MaterialTheme.typography.titleLarge)
                Text(midi.status)
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    Button(onClick = requestScan) { Text("Scan") }
                    OutlinedButton(onClick = {
                        midi.stopScan()
                        midi.disconnect()
                        midi.report("Disconnected.")
                    }) { Text("Disconnect") }
                }
                Text("Bluetooth hardware alone does not guarantee that this firmware exposes BLE MIDI.",
                    style = MaterialTheme.typography.bodySmall)
            }
        }
        if (midi.nearby.isNotEmpty()) {
            Text("Nearby BLE devices", style = MaterialTheme.typography.titleLarge)
            val sorted = midi.nearby.sortedWith(
                compareByDescending<BleCandidate> { it.midiAdvertised }
                    .thenByDescending { it.name.contains("FM", ignoreCase = true) }
                    .thenBy { it.name }
            )
            sorted.forEach { candidate ->
                Card(Modifier.fillMaxWidth()) {
                    Column(Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                        Text(candidate.name, fontWeight = FontWeight.SemiBold)
                        Text((if (candidate.midiAdvertised) "BLE-MIDI advertised" else "BLE device") +
                            " · " + candidate.address, style = MaterialTheme.typography.bodySmall)
                        OutlinedButton(onClick = { midi.connect(candidate) }) {
                            Text("Try MIDI connection")
                        }
                    }
                }
            }
        }
        HorizontalDivider()
        Text("MIDI keyboard", style = MaterialTheme.typography.titleLarge)
        Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            OutlinedButton(onClick = { midi.setChannel(-1) }, enabled = midi.channel > 1) {
                Text("−")
            }
            Text("Channel " + midi.channel, modifier = Modifier.padding(top = 12.dp))
            OutlinedButton(onClick = { midi.setChannel(1) }, enabled = midi.channel < 16) {
                Text("+")
            }
        }
        Text("Tap to send a short note after MIDI connects.",
            style = MaterialTheme.typography.bodySmall)
        Row(modifier = Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()),
            horizontalArrangement = Arrangement.spacedBy(5.dp)) {
            labels.forEachIndexed { index, name ->
                if (name.contains("♯"))
                    OutlinedButton(onClick = { midi.play(60 + index) }, enabled = midi.connected) {
                        Text(name)
                    }
                else
                    Button(onClick = { midi.play(60 + index) }, enabled = midi.connected) {
                        Text(name)
                    }
            }
        }
        HorizontalDivider()
        Text("Diagnostics", style = MaterialTheme.typography.titleLarge)
        midi.events.forEach { event ->
            Text(event, style = MaterialTheme.typography.bodySmall)
        }
        Text("No firmware writes, cloud service, logging to disk, or login.",
            style = MaterialTheme.typography.bodySmall)
    }
}
