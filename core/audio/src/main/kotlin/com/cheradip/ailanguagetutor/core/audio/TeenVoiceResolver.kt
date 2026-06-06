package com.cheradip.ailanguagetutor.core.audio

import android.speech.tts.Voice
import java.util.Locale

internal data class ResolvedTeenVoice(
    val voice: Voice?,
    val pitch: Float,
    val usePitchFallback: Boolean,
)

internal object TeenVoiceResolver {

    private val maleHints = listOf(
        "male", "-m-", "_m_", "#male", " man", "man-", "-man", "boy", "david", "james", "john",
    )
    private val femaleHints = listOf(
        "female", "-f-", "_f_", "#female", " woman", "woman-", "-woman", "girl", "zira", "samantha",
    )

    fun resolve(voices: Set<Voice>, languageCode: String, gender: TeenVoiceGender): ResolvedTeenVoice {
        val localeVoices = voices.filter { matchesLanguage(it, languageCode) }
            .sortedBy { it.name }
        if (localeVoices.isEmpty()) {
            return ResolvedTeenVoice(voice = voices.firstOrNull(), pitch = pitchFor(gender), usePitchFallback = true)
        }

        val classified = localeVoices.map { it to classifyGender(it) }
        val genderMatch = classified.firstOrNull { it.second == gender }?.first
        if (genderMatch != null) {
            return ResolvedTeenVoice(voice = genderMatch, pitch = 1.0f, usePitchFallback = false)
        }

        val opposite = if (gender == TeenVoiceGender.MALE) TeenVoiceGender.FEMALE else TeenVoiceGender.MALE
        val oppositeVoice = classified.firstOrNull { it.second == opposite }?.first
        if (oppositeVoice != null) {
            return ResolvedTeenVoice(voice = oppositeVoice, pitch = pitchFor(gender), usePitchFallback = true)
        }

        val neutralVoices = classified.filter { it.second == null }.map { it.first }
        if (neutralVoices.size >= 2) {
            val index = if (gender == TeenVoiceGender.MALE) 0 else 1
            return ResolvedTeenVoice(
                voice = neutralVoices.getOrElse(index) { neutralVoices.first() },
                pitch = pitchFor(gender),
                usePitchFallback = true,
            )
        }

        return ResolvedTeenVoice(
            voice = localeVoices.first(),
            pitch = pitchFor(gender),
            usePitchFallback = true,
        )
    }

    fun previewPhrase(languageCode: String): String = when (languageCode.lowercase()) {
        "fr" -> "Bonjour, je suis votre tuteur de langue."
        "es" -> "Hola, soy tu tutor de idiomas."
        "de" -> "Hallo, ich bin dein Sprachtutor."
        "it" -> "Ciao, sono il tuo tutor di lingua."
        "pt" -> "Olá, sou o seu tutor de idiomas."
        "hi" -> "नमस्ते, मैं आपका भाषा शिक्षक हूँ।"
        "ja" -> "こんにちは、語学のチューターです。"
        "ko" -> "안녕하세요, 언어 튜터입니다."
        "zh" -> "你好，我是你的语言导师。"
        else -> "Hello, I am your language tutor."
    }

    fun localeFor(code: String): Locale = when (code.lowercase()) {
        "en" -> Locale.US
        "fr" -> Locale.FRENCH
        "es" -> Locale("es")
        "de" -> Locale.GERMAN
        "it" -> Locale.ITALIAN
        "pt" -> Locale("pt", "BR")
        "hi" -> Locale("hi", "IN")
        "ja" -> Locale.JAPANESE
        "ko" -> Locale.KOREAN
        "zh" -> Locale.CHINESE
        else -> Locale.forLanguageTag(code)
    }

    private fun pitchFor(gender: TeenVoiceGender): Float = when (gender) {
        TeenVoiceGender.MALE -> 0.82f
        TeenVoiceGender.FEMALE -> 1.18f
    }

    private fun matchesLanguage(voice: Voice, languageCode: String): Boolean {
        val tag = languageCode.lowercase()
        val locale = voice.locale ?: return false
        val voiceLang = locale.language.lowercase()
        if (voiceLang == tag) return true
        if (tag.length >= 2 && voiceLang.startsWith(tag.take(2))) return true
        return locale.toLanguageTag().lowercase().startsWith(tag)
    }

    private fun classifyGender(voice: Voice): TeenVoiceGender? {
        val haystack = buildString {
            append(voice.name.lowercase())
            append(' ')
            voice.features.orEmpty().joinTo(this, " ") { it.lowercase() }
        }
        val maleScore = maleHints.count { haystack.contains(it) }
        val femaleScore = femaleHints.count { haystack.contains(it) }
        return when {
            maleScore > femaleScore && maleScore > 0 -> TeenVoiceGender.MALE
            femaleScore > maleScore && femaleScore > 0 -> TeenVoiceGender.FEMALE
            else -> null
        }
    }
}
