package com.musician

import kotlin.math.log2
import kotlin.math.roundToInt

/**
 * AudioAnalyzer currently provides placeholder analysis values.
 *
 * @param onResultsUpdated callback invoked with (note, scale, bpm) strings.
 */
class AudioAnalyzer(
    private val onResultsUpdated: (note: String, scale: String, bpm: String) -> Unit,
    private val onStatusUpdated: (status: String) -> Unit = {}
) {

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

    /** Starts the analyzer placeholder and emits initial "Detecting…" values. */
    fun start() {
        dispatcher?.stop()
        detectedPitchClasses.clear()
        onsetTimesMs.clear()
        onResultsUpdated("Detecting…", "Detecting…", "Detecting…")
    }

    /** Stops the analyzer placeholder; there are no running resources to release. */
    fun stop() {
        // No active audio dispatcher in placeholder implementation.
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
