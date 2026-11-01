package com.example.hearingaidapp

import android.content.Context
import android.media.AudioFormat
import android.media.AudioRecord
import android.media.MediaRecorder
import android.util.Log
import java.io.BufferedOutputStream
import java.io.ByteArrayOutputStream
import java.io.DataOutputStream
import java.io.File
import java.io.FileOutputStream
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/**
 * Captures microphone audio and saves it as a WAV file.
 * Runs on a background thread. Safe to call start/stop from any thread.
 */
class RecordingManager(private val context: Context) {

    companion object {
        private const val TAG = "RecordingManager"
        private const val SAMPLE_RATE = 44100
        private const val CHANNEL_CONFIG = AudioFormat.CHANNEL_IN_MONO
        private const val AUDIO_FORMAT = AudioFormat.ENCODING_PCM_16BIT
    }

    interface RecordingListener {
        fun onRecordingStarted()
        fun onRecordingStopped(file: File)
        fun onRecordingError(message: String)
    }

    private var audioRecord: AudioRecord? = null
    private var recordingThread: Thread? = null
    @Volatile private var isRecording = false
    private var listener: RecordingListener? = null
    private var outputFile: File? = null

    val isActive: Boolean get() = isRecording

    fun setListener(l: RecordingListener) { listener = l }

    fun startRecording() {
        if (isRecording) return
        val bufferSize = AudioRecord.getMinBufferSize(SAMPLE_RATE, CHANNEL_CONFIG, AUDIO_FORMAT)
        if (bufferSize == AudioRecord.ERROR_BAD_VALUE || bufferSize == AudioRecord.ERROR) {
            listener?.onRecordingError("Audio recording not supported on this device")
            return
        }

        try {
            val ar = AudioRecord(
                MediaRecorder.AudioSource.VOICE_RECOGNITION,
                SAMPLE_RATE, CHANNEL_CONFIG, AUDIO_FORMAT, bufferSize * 4
            )
            if (ar.state != AudioRecord.STATE_INITIALIZED) {
                ar.release()
                listener?.onRecordingError("Cannot initialize audio recorder")
                return
            }
            audioRecord = ar

            val timestamp = SimpleDateFormat("yyyyMMdd_HHmmss", Locale.getDefault()).format(Date())
            val dir = File(context.filesDir, "recordings")
            dir.mkdirs()
            val file = File(dir, "meeting_$timestamp.wav")
            outputFile = file

            isRecording = true
            ar.startRecording()
            listener?.onRecordingStarted()

            recordingThread = Thread({
                writeWavFile(ar, bufferSize, file)
            }, "RecordingThread").apply { start() }

        } catch (e: SecurityException) {
            listener?.onRecordingError("Microphone permission denied")
        } catch (e: Exception) {
            listener?.onRecordingError("Recording error: ${e.message}")
        }
    }

    fun stopRecording() {
        if (!isRecording) return
        isRecording = false
        recordingThread?.join(2000)
        audioRecord?.apply {
            try { stop() } catch (_: Exception) {}
            release()
        }
        audioRecord = null
        outputFile?.let { if (it.exists()) listener?.onRecordingStopped(it) }
        outputFile = null
    }

    private fun writeWavFile(ar: AudioRecord, bufferSize: Int, file: File) {
        val buffer = ShortArray(bufferSize)
        val rawBytes = ByteArrayOutputStream()

        while (isRecording) {
            val read = ar.read(buffer, 0, buffer.size)
            if (read > 0) {
                for (i in 0 until read) {
                    val s = buffer[i].toInt()
                    rawBytes.write(s and 0xFF)
                    rawBytes.write((s shr 8) and 0xFF)
                }
            }
        }

        val pcmData = rawBytes.toByteArray()
        writeWavHeader(file, pcmData)
        Log.d(TAG, "Saved recording: ${file.absolutePath} (${pcmData.size} bytes)")
    }

    private fun writeWavHeader(file: File, pcmData: ByteArray) {
        val channels = 1
        val bitsPerSample = 16
        val byteRate = SAMPLE_RATE * channels * bitsPerSample / 8
        val dataSize = pcmData.size
        val fileSize = dataSize + 36

        DataOutputStream(BufferedOutputStream(FileOutputStream(file))).use { out ->
            out.writeBytes("RIFF")
            out.writeIntLE(fileSize)
            out.writeBytes("WAVEfmt ")
            out.writeIntLE(16)       // PCM chunk size
            out.writeShortLE(1)      // PCM format
            out.writeShortLE(channels.toShort())
            out.writeIntLE(SAMPLE_RATE)
            out.writeIntLE(byteRate)
            out.writeShortLE((channels * bitsPerSample / 8).toShort())
            out.writeShortLE(bitsPerSample.toShort())
            out.writeBytes("data")
            out.writeIntLE(dataSize)
            out.write(pcmData)
        }
    }

    private fun DataOutputStream.writeIntLE(v: Int) {
        write(v and 0xFF)
        write((v shr 8) and 0xFF)
        write((v shr 16) and 0xFF)
        write((v shr 24) and 0xFF)
    }

    private fun DataOutputStream.writeShortLE(v: Short) {
        val i = v.toInt()
        write(i and 0xFF)
        write((i shr 8) and 0xFF)
    }
}

