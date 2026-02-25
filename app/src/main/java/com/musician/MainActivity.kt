package com.musician

import android.Manifest
import android.content.pm.PackageManager
import android.os.Bundle
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat
import com.musician.databinding.ActivityMainBinding

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
                startRecording()
            } else {
                Toast.makeText(this, getString(R.string.permission_denied), Toast.LENGTH_LONG).show()
            }
        }
    }

    private fun startRecording() {
        isRecording = true
        binding.btnRecord.text = getString(R.string.stop_recording)
        binding.tvResults.text = getString(R.string.analyzing)

        audioAnalyzer = AudioAnalyzer { note, scale, bpm ->
            runOnUiThread {
                binding.tvNote.text  = getString(R.string.result_note,  note)
                binding.tvScale.text = getString(R.string.result_scale, scale)
                binding.tvBpm.text   = getString(R.string.result_bpm,   bpm)
                binding.tvResults.text = ""
            }
        }
        audioAnalyzer?.start()
    }

    private fun stopRecording() {
        isRecording = false
        binding.btnRecord.text = getString(R.string.start_recording)
        audioAnalyzer?.stop()
        audioAnalyzer = null
    }

    override fun onDestroy() {
        super.onDestroy()
        audioAnalyzer?.stop()
    }
}
