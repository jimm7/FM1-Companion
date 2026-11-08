package au.jimm7.fm1companion

/** Encodes standard MIDI 1.0 byte-stream channel messages. */
object MidiMessages {
    fun noteOn(channel: Int, note: Int, velocity: Int = 100): ByteArray {
        validate(channel, note, velocity)
        return byteArrayOf((0x90 or (channel - 1)).toByte(), note.toByte(), velocity.toByte())
    }

    fun noteOff(channel: Int, note: Int): ByteArray {
        validate(channel, note, 0)
        return byteArrayOf((0x80 or (channel - 1)).toByte(), note.toByte(), 0)
    }

    fun allNotesOff(channel: Int): ByteArray {
        require(channel in 1..16)
        return byteArrayOf((0xB0 or (channel - 1)).toByte(), 123, 0)
    }

    private fun validate(channel: Int, note: Int, velocity: Int) {
        require(channel in 1..16)
        require(note in 0..127)
        require(velocity in 0..127)
    }
}
