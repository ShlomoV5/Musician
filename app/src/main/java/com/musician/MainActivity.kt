package com.musician

import android.Manifest
import android.content.pm.PackageManager
import android.os.Bundle
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat
import com.musician.databinding.ActivityMainBinding
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/**
 * MainActivity hosts a single "Record / Stop" toggle button and a results panel
 * that shows the detected Note, Scale, and BPM in real time.
 *
 * Audio recording permission is requested at runtime before the first recording
 * session starts.
 */
class MainActivity : AppCompatActivity() {

    private lateinit var binding: ActivityMainBinding
    private var audioAnalyzer: AudioAnalyzer? = null
    private var isRecording = false
    private val logTimeFormat = SimpleDateFormat("HH:mm:ss", Locale.getDefault())
    private val maxLogLines = 6

    companion object {
        private const val REQUEST_RECORD_AUDIO = 200
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityMainBinding.inflate(layoutInflater)
        setContentView(binding.root)

        binding.btnRecord.setOnClickListener {
            if (isRecording) stopRecording() else checkPermissionAndRecord()
        }
        binding.tvStatus.text = getString(R.string.status_ready)
        binding.tvResults.text = getString(R.string.press_record)
        appendLog("App ready.")
    }

    // -------------------------------------------------------------------------
    // Recording lifecycle
    // -------------------------------------------------------------------------

    private fun checkPermissionAndRecord() {
        if (ContextCompat.checkSelfPermission(this, Manifest.permission.RECORD_AUDIO)
            == PackageManager.PERMISSION_GRANTED
        ) {
            startRecording()
        } else {
            appendLog("Requesting microphone permission.")
            ActivityCompat.requestPermissions(
                this,
                arrayOf(Manifest.permission.RECORD_AUDIO),
                REQUEST_RECORD_AUDIO
            )
        }
    }

    override fun onRequestPermissionsResult(
        requestCode: Int,
        permissions: Array<String>,
        grantResults: IntArray
    ) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults)
        if (requestCode == REQUEST_RECORD_AUDIO) {
            if (grantResults.isNotEmpty() && grantResults[0] == PackageManager.PERMISSION_GRANTED) {
                appendLog("Microphone permission granted.")
                startRecording()
            } else {
                appendLog("Microphone permission denied.")
                Toast.makeText(this, getString(R.string.permission_denied), Toast.LENGTH_LONG).show()
            }
        }
    }

    private fun startRecording() {
        isRecording = true
        binding.btnRecord.text = getString(R.string.stop_recording)
        binding.tvStatus.text = getString(R.string.status_listening)
        binding.tvResults.text = getString(R.string.analyzing)
        appendLog("Recording started.")

        audioAnalyzer = AudioAnalyzer(
            onResultsUpdated = { note, scale, bpm ->
                runOnUiThread {
                    binding.tvNote.text  = getString(R.string.result_note,  note)
                    binding.tvScale.text = getString(R.string.result_scale, scale)
                    binding.tvBpm.text   = getString(R.string.result_bpm,   bpm)
                    binding.tvStatus.text = getString(R.string.analyzing)
                    binding.tvResults.text = ""
                }
            },
            onStatusUpdated = { status ->
                runOnUiThread {
                    appendLog(status)
                }
            }
        )
        audioAnalyzer?.start()
    }

    private fun stopRecording() {
        isRecording = false
        binding.btnRecord.text = getString(R.string.start_recording)
        audioAnalyzer?.stop()
        audioAnalyzer = null
        binding.tvStatus.text = getString(R.string.status_stopped)
        binding.tvResults.text = getString(R.string.press_record)
        appendLog("Recording stopped.")
    }

    override fun onDestroy() {
        super.onDestroy()
        audioAnalyzer?.stop()
    }

    private fun appendLog(message: String) {
        val timestamp = logTimeFormat.format(Date())
        val lines = binding.tvLog.text
            ?.toString()
            .orEmpty()
            .lineSequence()
            .filter { it.isNotBlank() }
            .toMutableList()
        lines.add(0, "[$timestamp] $message")
        if (lines.size > maxLogLines) {
            lines.subList(maxLogLines, lines.size).clear()
        }
        binding.tvLog.text = lines.joinToString("\n")
    }
}
