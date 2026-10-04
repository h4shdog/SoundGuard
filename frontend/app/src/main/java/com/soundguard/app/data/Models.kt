package com.soundguard.app.data

// ─────────────────────────────────────────────────────────────
// Domain models
// ─────────────────────────────────────────────────────────────

enum class SoundClass(val label: String, val emoji: String, val dbLabel: Int) {
    FIRE_ALARM("Fire Alarm",  "🔥", 1),
    SIREN     ("Siren",       "🚨", 2),
    BACKGROUND("Background",  "🔊", 3)
}

enum class ModelType(val displayName: String, val fileName: String) {
    BEST_MODEL("BestModel", "BestModel.tflite")
}

data class DetectionResult(
    val soundClass  : SoundClass    = SoundClass.BACKGROUND,
    val confidence  : Float         = 0f,
    val inferenceMs : Int           = 0,
    val model       : ModelType     = ModelType.BEST_MODEL,
    val isEmergency : Boolean       = false,
    // Real per-class scores from the model — Keras alphabetical order:
    // [0]=Fire Alarm, [1]=Background (noise), [2]=Siren
    val allScores   : FloatArray    = FloatArray(3)
)

data class AlertRecord(
    val id          : Int,
    val soundClass  : SoundClass,
    val model       : ModelType,
    val confidence  : Float,
    val inferenceMs : Int,
    val timestamp   : String,
    val dismissed   : Boolean = false
)

data class ModelMetrics(
    val accuracy   : Float,
    val precision  : Float,
    val recall     : Float,
    val f1         : Float,
    val inferenceMs: Int
)

// ─────────────────────────────────────────────────────────────
// Demo / placeholder data used before real ML inference results
// are available. Metrics sourced from EfficientNetB0 training notebook.
// ─────────────────────────────────────────────────────────────
object DemoData {
    // Metrics placeholders — will be replaced with real values from the model
    // once the training results CSV (efficientnet_results.csv) is available.
    // Architecture: EfficientNetB0, pretrained on ImageNet, fine-tuned on
    // 3-class log-mel spectrogram dataset (fire_alarm / noise / siren).
    val bestModelMetrics = ModelMetrics(0.968f, 0.952f, 0.961f, 0.956f, 12)
}
