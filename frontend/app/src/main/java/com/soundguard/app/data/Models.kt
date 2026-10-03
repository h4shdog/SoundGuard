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
// Demo / placeholder data — replaced by real ML inference later
// ─────────────────────────────────────────────────────────────
object DemoData {

    val bestModelMetrics = ModelMetrics(0.968f, 0.952f, 0.961f, 0.956f, 12)

    val history = listOf(
        AlertRecord(1,  SoundClass.FIRE_ALARM, ModelType.BEST_MODEL, 0.974f, 12, "Today 14:14"),
        AlertRecord(2,  SoundClass.SIREN,      ModelType.BEST_MODEL, 0.921f,  8, "Today 13:52"),
        AlertRecord(3,  SoundClass.FIRE_ALARM, ModelType.BEST_MODEL, 0.987f, 11, "Today 11:08"),
        AlertRecord(4,  SoundClass.SIREN,      ModelType.BEST_MODEL, 0.896f, 13, "Today 09:33"),
        AlertRecord(5,  SoundClass.FIRE_ALARM, ModelType.BEST_MODEL, 0.952f,  8, "Yesterday 18:45"),
        AlertRecord(6,  SoundClass.SIREN,      ModelType.BEST_MODEL, 0.934f,  7, "Yesterday 14:07"),
        AlertRecord(7,  SoundClass.FIRE_ALARM, ModelType.BEST_MODEL, 0.968f, 12, "Sep 25  09:12"),
        AlertRecord(8,  SoundClass.SIREN,      ModelType.BEST_MODEL, 0.910f, 14, "Sep 25  06:30"),
        AlertRecord(9,  SoundClass.FIRE_ALARM, ModelType.BEST_MODEL, 0.945f,  8, "Sep 24  20:15"),
        AlertRecord(10, SoundClass.SIREN,      ModelType.BEST_MODEL, 0.889f, 13, "Sep 24  08:44")
    )
}
