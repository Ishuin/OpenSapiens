package org.opensapien.core.transcription

import java.io.DataOutputStream
import java.io.File
import java.io.FileOutputStream
import java.nio.ByteBuffer
import java.nio.ByteOrder

/** Minimal 16 kHz mono PCM16 WAV read/write. */
object WavIo {

    fun readPcm16Mono(wav: File): ShortArray {
        val bytes = wav.readBytes()
        require(bytes.size > 44) { "not a wav: ${wav.name}" }
        val bb = ByteBuffer.wrap(bytes, 44, bytes.size - 44).order(ByteOrder.LITTLE_ENDIAN)
        val out = ShortArray((bytes.size - 44) / 2)
        bb.asShortBuffer().get(out)
        return out
    }

    fun writePcm16Mono(pcm: ShortArray, sampleRate: Int, dest: File) {
        val dataLen = pcm.size * 2
        DataOutputStream(FileOutputStream(dest).buffered()).use { o ->
            o.writeBytes("RIFF"); o.writeIntLe(36 + dataLen); o.writeBytes("WAVE")
            o.writeBytes("fmt "); o.writeIntLe(16); o.writeShortLe(1); o.writeShortLe(1)
            o.writeIntLe(sampleRate); o.writeIntLe(sampleRate * 2)
            o.writeShortLe(2); o.writeShortLe(16)
            o.writeBytes("data"); o.writeIntLe(dataLen)
            val buf = ByteBuffer.allocate(dataLen).order(ByteOrder.LITTLE_ENDIAN)
            buf.asShortBuffer().put(pcm)
            o.write(buf.array())
        }
    }

    private fun DataOutputStream.writeIntLe(v: Int) {
        write(v); write(v shr 8); write(v shr 16); write(v shr 24)
    }

    private fun DataOutputStream.writeShortLe(v: Int) {
        write(v); write(v shr 8)
    }
}
