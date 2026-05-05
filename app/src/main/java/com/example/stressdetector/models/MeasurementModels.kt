package com.example.stressdetector.models

import com.example.stressdetector.preprocessing.StressResult
import com.google.gson.annotations.SerializedName

data class MeasurementRequest(
    val probability: Float,
    @SerializedName("probability_median") val probabilityMedian: Float,
    @SerializedName("probability_min") val probabilityMin: Float,
    @SerializedName("probability_max") val probabilityMax: Float,
    @SerializedName("probability_std") val probabilityStd: Float,
    val label: String,
    @SerializedName("is_stressed") val isStressed: Boolean,
    @SerializedName("stress_level") val stressLevel: String,
    @SerializedName("stress_pattern") val stressPattern: String,
    @SerializedName("total_windows") val totalWindows: Int,
    @SerializedName("stressed_windows") val stressedWindows: Int,
    @SerializedName("stress_percentage") val stressPercentage: Float,
    @SerializedName("segments_used") val segmentsUsed: Int,
    @SerializedName("total_duration_s") val totalDurationS: Float,
    @SerializedName("processing_time_ms") val processingTimeMs: Long,
    @SerializedName("quality_score") val qualityScore: Float,
    @SerializedName("file_name") val fileName: String,
    @SerializedName("hr_bpm") val hrBpm: Float?,
    @SerializedName("hr_ecg_bpm") val hrEcgBpm: Float?,
    @SerializedName("hr_bvp_bpm") val hrBvpBpm: Float?,
    val rmssd: Float?,
    val sdnn: Float?,
    val pnn50: Float?,
    @SerializedName("num_peaks_ecg") val numPeaksEcg: Int?,
    @SerializedName("num_peaks_bvp") val numPeaksBvp: Int?,
    @SerializedName("windows_rejected") val windowsRejected: Int,
    @SerializedName("duplicates_detected") val duplicatesDetected: Int,
    @SerializedName("window_results") val windowResults: List<WindowResultRequest>
)

data class WindowResultRequest(
    @SerializedName("segment_id") val segmentId: Int,
    @SerializedName("window_index") val windowIndex: Int,
    @SerializedName("start_s") val startS: Float,
    val probability: Float,
    @SerializedName("is_stressed") val isStressed: Boolean
)

fun StressResult.toMeasurementRequest(fileName: String): MeasurementRequest {
    fun Float.nanToNull(): Float? = if (this.isNaN()) null else this

    return MeasurementRequest(
        probability = this.probability,
        probabilityMedian = this.probabilityMedian,
        probabilityMin = this.probabilityMin,
        probabilityMax = this.probabilityMax,
        probabilityStd = this.probabilityStd,
        label = this.label,
        isStressed = this.isStressed,
        stressLevel = this.stressLevel,
        stressPattern = this.stressPattern(),
        totalWindows = this.totalWindows,
        stressedWindows = this.stressedWindows,
        stressPercentage = this.stressPercentage,
        segmentsUsed = this.segmentsUsed,
        totalDurationS = this.totalDurationS,
        processingTimeMs = this.processingTimeMs,
        qualityScore = this.quality.score,
        fileName = fileName,
        hrBpm = this.hrvMetrics.hrBpm.nanToNull(),
        hrEcgBpm = this.hrvMetrics.hrEcgBpm.nanToNull(),
        hrBvpBpm = this.hrvMetrics.hrBvpBpm.nanToNull(),
        rmssd = this.hrvMetrics.rmssd.nanToNull(),
        sdnn = this.hrvMetrics.sdnn.nanToNull(),
        pnn50 = this.hrvMetrics.pnn50.nanToNull(),
        numPeaksEcg = this.hrvMetrics.numPeaksEcg,
        numPeaksBvp = this.hrvMetrics.numPeaksBvp,
        windowsRejected = this.windowsRejected,
        duplicatesDetected = this.duplicatesDetected,
        windowResults = this.windowResults.map {
            WindowResultRequest(
                segmentId = it.segmentId,
                windowIndex = it.windowIndex,
                startS = it.startS,
                probability = it.probability,
                isStressed = it.isStressed
            )
        }
    )
}

// --- Response models ---

data class MeasurementResponse(val message: String, val id: Int)

data class MeasurementListResponse(
    val measurements: List<MeasurementSummary>,
    val total: Int,
    val pages: Int,
    val page: Int
)

data class MeasurementSummary(
    val id: Int,
    val timestamp: String,
    val label: String,
    @SerializedName("stress_level") val stressLevel: String,
    val probability: Float,
    @SerializedName("is_stressed") val isStressed: Boolean,
    @SerializedName("hr_bpm") val hrBpm: Float?,
    @SerializedName("total_duration_s") val totalDurationS: Float?,
    @SerializedName("file_name") val fileName: String?
)

data class MeasurementDetail(
    val id: Int,
    val timestamp: String,
    val probability: Float,
    @SerializedName("probability_median") val probabilityMedian: Float?,
    @SerializedName("probability_min") val probabilityMin: Float?,
    @SerializedName("probability_max") val probabilityMax: Float?,
    @SerializedName("probability_std") val probabilityStd: Float?,
    val label: String,
    @SerializedName("is_stressed") val isStressed: Boolean,
    @SerializedName("stress_level") val stressLevel: String?,
    @SerializedName("stress_pattern") val stressPattern: String?,
    @SerializedName("total_windows") val totalWindows: Int?,
    @SerializedName("stressed_windows") val stressedWindows: Int?,
    @SerializedName("stress_percentage") val stressPercentage: Float?,
    @SerializedName("segments_used") val segmentsUsed: Int?,
    @SerializedName("total_duration_s") val totalDurationS: Float?,
    @SerializedName("processing_time_ms") val processingTimeMs: Long?,
    @SerializedName("quality_score") val qualityScore: Float?,
    @SerializedName("file_name") val fileName: String?,
    @SerializedName("hr_bpm") val hrBpm: Float?,
    @SerializedName("hr_ecg_bpm") val hrEcgBpm: Float?,
    @SerializedName("hr_bvp_bpm") val hrBvpBpm: Float?,
    val rmssd: Float?,
    val sdnn: Float?,
    val pnn50: Float?,
    @SerializedName("num_peaks_ecg") val numPeaksEcg: Int?,
    @SerializedName("num_peaks_bvp") val numPeaksBvp: Int?,
    @SerializedName("windows_rejected") val windowsRejected: Int?,
    @SerializedName("duplicates_detected") val duplicatesDetected: Int?,
    @SerializedName("window_results") val windowResults: List<WindowResultDetail>?
)

data class WindowResultDetail(
    @SerializedName("segment_id") val segmentId: Int,
    @SerializedName("window_index") val windowIndex: Int,
    @SerializedName("start_s") val startS: Float,
    val probability: Float,
    @SerializedName("is_stressed") val isStressed: Boolean
)
