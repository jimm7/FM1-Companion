package au.jimm7.fm1companion

import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertThrows
import org.junit.Test

class MidiMessagesTest {
    @Test fun channel1NoteOn() {
        assertArrayEquals(byteArrayOf(0x90.toByte(), 60, 100),
            MidiMessages.noteOn(1, 60))
    }
    @Test fun channel10NoteOff() {
        assertArrayEquals(byteArrayOf(0x89.toByte(), 64, 0),
            MidiMessages.noteOff(10, 64))
    }
    @Test fun channel16Panic() {
        assertArrayEquals(byteArrayOf(0xBF.toByte(), 123, 0),
            MidiMessages.allNotesOff(16))
    }
    @Test fun invalidChannel() {
        assertThrows(IllegalArgumentException::class.java) {
            MidiMessages.noteOn(0, 60)
        }
    }
    @Test fun invalidNote() {
        assertThrows(IllegalArgumentException::class.java) {
            MidiMessages.noteOn(1, 128)
        }
    }
}
