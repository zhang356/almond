package app.almondally.network

data class ElevenLabsRequestBodyVoiceSettings(
    var stability: Double = 0.5,
    var similarity_boost: Double = 0.5
)
data class ElevenLabsRequestBody(
    var text:String?,
    var model_id: String? = "eleven_monolingual_v1",
    var voice_settings: ElevenLabsRequestBodyVoiceSettings? = ElevenLabsRequestBodyVoiceSettings()
)
