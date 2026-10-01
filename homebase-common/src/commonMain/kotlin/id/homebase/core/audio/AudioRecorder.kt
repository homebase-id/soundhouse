package id.homebase.core.audio

interface AudioRecorder {

    fun getAudioFileExtension(): String
    fun startRecording(fileName: String)
    fun stopRecording(): String?

    /** Input level since the previous call, 0..1; 0 where the platform can't measure it. */
    fun currentLevel(): Float = 0f
}