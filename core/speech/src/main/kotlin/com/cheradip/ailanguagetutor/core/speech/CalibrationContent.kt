package com.cheradip.ailanguagetutor.core.speech

enum class CalibrationTier { WORD, SENTENCE, PARAGRAPH }

data class CalibrationPack(
    val languageCode: String,
    val words: List<String>,
    val sentences: List<String>,
    val paragraph: String,
)

object CalibrationContent {
    private val packs = listOf(
        CalibrationPack(
            languageCode = "en",
            words = listOf(
                "hello", "thank", "please", "good", "morning",
                "learn", "practice", "speak", "listen", "language",
            ),
            sentences = listOf(
                "Good morning, how are you today?",
                "I am learning a new language every day.",
                "Please speak slowly and clearly for me.",
            ),
            paragraph = "Every morning I wake up early and practice speaking aloud in a calm clear voice. " +
                "I read each word carefully, listen to myself, and repeat until the sentence feels natural and easy.",
        ),
        CalibrationPack(
            languageCode = "fr",
            words = listOf(
                "bonjour", "merci", "s'il", "vous", "plait",
                "apprendre", "parler", "ecouter", "lire", "francais",
            ),
            sentences = listOf(
                "Bonjour, comment allez vous aujourd'hui?",
                "J'apprends une nouvelle langue chaque jour.",
                "Parlez lentement et clairement, s'il vous plait.",
            ),
            paragraph = "Chaque matin je me leve tot et je m'entraine a parler a voix haute avec calme. " +
                "Je lis chaque mot avec attention, je m'ecoute, et je repete jusqu'a ce que la phrase paraisse naturelle.",
        ),
        CalibrationPack(
            languageCode = "es",
            words = listOf(
                "hola", "gracias", "por", "favor", "buenos",
                "aprender", "hablar", "escuchar", "leer", "espanol",
            ),
            sentences = listOf(
                "Hola, como estas hoy?",
                "Aprendo un idioma nuevo cada dia.",
                "Por favor habla despacio y con claridad.",
            ),
            paragraph = "Cada manana me levanto temprano y practico hablar en voz alta con calma y claridad. " +
                "Leo cada palabra con cuidado, me escucho, y repito hasta que la frase suena natural y facil.",
        ),
        CalibrationPack(
            languageCode = "de",
            words = listOf(
                "hallo", "danke", "bitte", "guten", "morgen",
                "lernen", "sprechen", "horen", "lesen", "deutsch",
            ),
            sentences = listOf(
                "Guten Morgen, wie geht es Ihnen heute?",
                "Ich lerne jeden Tag eine neue Sprache.",
                "Bitte sprechen Sie langsam und deutlich.",
            ),
            paragraph = "Jeden Morgen stehe ich fruh auf und ube lautes Sprechen mit ruhiger klarer Stimme. " +
                "Ich lese jedes Wort sorgfaltig, hore mir zu, und wiederhole bis der Satz naturlich klingt.",
        ),
    ).associateBy { it.languageCode.lowercase() }

    fun packFor(languageCode: String): CalibrationPack =
        packs[languageCode.lowercase()] ?: packs.getValue("en")

    fun prompt(pack: CalibrationPack, tier: CalibrationTier, index: Int): String? =
        when (tier) {
            CalibrationTier.WORD -> pack.words.getOrNull(index)
            CalibrationTier.SENTENCE -> pack.sentences.getOrNull(index)
            CalibrationTier.PARAGRAPH -> if (index == 0) pack.paragraph else null
        }

    fun itemCount(tier: CalibrationTier): Int = when (tier) {
        CalibrationTier.WORD -> 10
        CalibrationTier.SENTENCE -> 3
        CalibrationTier.PARAGRAPH -> 1
    }

    fun tierLabel(tier: CalibrationTier): String = when (tier) {
        CalibrationTier.WORD -> "Words"
        CalibrationTier.SENTENCE -> "Sentences"
        CalibrationTier.PARAGRAPH -> "Paragraph"
    }
}
