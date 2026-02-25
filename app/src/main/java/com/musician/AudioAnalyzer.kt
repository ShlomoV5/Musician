package com.musician

import be.tarsos.dsp.AudioDispatcher
import be.tarsos.dsp.io.android.AudioDispatcherFactory
import be.tarsos.dsp.onsets.ComplexOnsetDetector
import be.tarsos.dsp.onsets.OnsetHandler
import be.tarsos.dsp.pitch.PitchDetectionHandler
import be.tarsos.dsp.pitch.PitchProcessor
import be.tarsos.dsp.pitch.PitchProcessor.PitchEstimationAlgorithm
import kotlin.math.log2
import kotlin.math.roundToInt

/**
 * AudioAnalyzer uses TarsosDSP to perform on-device analysis of microphone audio.
 *
 * Detects:
 *  - Pitch / musical note (YIN algorithm)
 *  - Scale (matched against major / modal patterns from accumulated notes)
 *  - Tempo / BPM (derived from inter-onset intervals via ComplexOnsetDetector)
 *
 * @param onResultsUpdated callback invoked on each analysis update with (note, scale, bpm) strings.
 */
class AudioAnalyzer(private val onResultsUpdated: (note: String, scale: String, bpm: String) -> Unit) {

    private var dispatcher: AudioDispatcher? = null

    // Ring buffers – capped to avoid unbounded memory growth
    private val detectedPitchClasses = ArrayDeque<Int>(MAX_NOTES)
    private val onsetTimesMs = ArrayDeque<Long>(MAX_ONSETS)

    companion object {
        private const val SAMPLE_RATE = 22050
        private const val BUFFER_SIZE = 1024
        private const val BUFFER_OVERLAP = 0

        private const val MAX_NOTES = 100
        private const val MAX_ONSETS = 32
        private const val MIN_NOTES_FOR_SCALE = 5
        private const val MIN_ONSETS_FOR_BPM = 4

        private val NOTE_NAMES = arrayOf("C", "C#", "D", "D#", "E", "F", "F#", "G", "G#", "A", "A#", "B")

        // Scale patterns expressed as semitone intervals from the root
        private val SCALES = linkedMapOf(
            "Major"      to intArrayOf(0, 2, 4, 5, 7, 9, 11),
            "Minor"      to intArrayOf(0, 2, 3, 5, 7, 8, 10),
            "Dorian"     to intArrayOf(0, 2, 3, 5, 7, 9, 10),
            "Phrygian"   to intArrayOf(0, 1, 3, 5, 7, 8, 10),
            "Lydian"     to intArrayOf(0, 2, 4, 6, 7, 9, 11),
            "Mixolydian" to intArrayOf(0, 2, 4, 5, 7, 9, 10),
            "Locrian"    to intArrayOf(0, 1, 3, 5, 6, 8, 10)
        )
    }

    /** Start capturing audio from the default microphone and analyzing it. */
    fun start() {
        detectedPitchClasses.clear()
        onsetTimesMs.clear()

        dispatcher = AudioDispatcherFactory.fromDefaultMicrophone(SAMPLE_RATE, BUFFER_SIZE, BUFFER_OVERLAP)

        // --- Pitch detection (YIN) ---
        val pitchHandler = PitchDetectionHandler { result, _ ->
            val hz = result.pitch
            if (hz > 0f) {
                val midi = hzToMidi(hz)
                val pitchClass = ((midi % 12) + 12) % 12
                if (detectedPitchClasses.size >= MAX_NOTES) detectedPitchClasses.removeFirst()
                detectedPitchClasses.addLast(pitchClass)

                val note  = midiToNoteName(midi)
                val scale = estimateScale()
                val bpm   = estimateBpm()
                onResultsUpdated(note, scale, bpm)
            }
        }
        dispatcher?.addAudioProcessor(
            PitchProcessor(PitchEstimationAlgorithm.YIN, SAMPLE_RATE.toFloat(), BUFFER_SIZE, pitchHandler)
        )

        // --- Onset detection for BPM ---
        val onsetDetector = ComplexOnsetDetector(BUFFER_SIZE)
        onsetDetector.setHandler(OnsetHandler { _, _ ->
            val now = System.currentTimeMillis()
            if (onsetTimesMs.size >= MAX_ONSETS) onsetTimesMs.removeFirst()
            onsetTimesMs.addLast(now)
        })
        dispatcher?.addAudioProcessor(onsetDetector)

        Thread(dispatcher, "AudioAnalyzer").start()
    }

    /** Stop audio capture and release resources. */
    fun stop() {
        dispatcher?.stop()
        dispatcher = null
    }

    // -------------------------------------------------------------------------
    // Frequency / note helpers
    // -------------------------------------------------------------------------

    /** Convert frequency in Hz to the nearest MIDI note number. */
    private fun hzToMidi(hz: Float): Int =
        (12.0 * log2(hz / 440.0) + 69.0).roundToInt()

    /** Convert a MIDI note number to a human-readable name, e.g. "A4". */
    private fun midiToNoteName(midi: Int): String {
        val noteIndex = ((midi % 12) + 12) % 12
        val octave    = midi / 12 - 1
        return "${NOTE_NAMES[noteIndex]}$octave"
    }

    // -------------------------------------------------------------------------
    // Scale estimation
    // -------------------------------------------------------------------------

    /**
     * Matches the accumulated pitch-class histogram against all known scales × 12
     * possible roots and returns the best-scoring "Root ScaleName" string.
     */
    private fun estimateScale(): String {
        if (detectedPitchClasses.size < MIN_NOTES_FOR_SCALE) return "Detecting…"

        // Build pitch-class histogram from the last MAX_NOTES detections
        val histogram = IntArray(12)
        detectedPitchClasses.forEach { histogram[it]++ }

        var bestScore = -1
        var bestRoot  = 0
        var bestName  = "Unknown"

        for (root in 0 until 12) {
            for ((scaleName, pattern) in SCALES) {
                val score = pattern.sumOf { interval -> histogram[(root + interval) % 12] }
                if (score > bestScore) {
                    bestScore = score
                    bestRoot  = root
                    bestName  = scaleName
                }
            }
        }

        return "${NOTE_NAMES[bestRoot]} $bestName"
    }

    // -------------------------------------------------------------------------
    // BPM estimation
    // -------------------------------------------------------------------------

    /**
     * Estimates BPM from the median inter-onset interval of the most recent
     * [MAX_ONSETS] detected onsets.
     */
    private fun estimateBpm(): String {
        if (onsetTimesMs.size < MIN_ONSETS_FOR_BPM) return "Detecting…"

        val times = onsetTimesMs.toList()
        val intervals = (1 until times.size).map { times[it] - times[it - 1] }.sorted()

        // Use the median interval to be robust against outliers
        val median = intervals[intervals.size / 2].toDouble()
        if (median <= 0) return "Detecting…"

        val bpm = (60_000.0 / median).roundToInt()
        return if (bpm in 30..300) "$bpm BPM" else "Detecting…"
    }
}
