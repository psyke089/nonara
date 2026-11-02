package app.nonara.notes

/** Single dictation backend and its fresh-install defaults. */
object DictationDefaults {
    const val LANGUAGE = "de-DE"
    const val MODEL = "base"
    const val CAPTURE_MODE = "sherpa"

    fun language(stored: String?) = stored ?: LANGUAGE
    fun model(stored: String?) = stored ?: MODEL
}
