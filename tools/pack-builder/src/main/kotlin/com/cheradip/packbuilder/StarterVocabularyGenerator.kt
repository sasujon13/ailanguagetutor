package com.cheradip.packbuilder

import com.cheradip.ailanguagetutor.core.model.LanguageCatalogEntry
import com.cheradip.packbuilder.model.LanguagePackSeed
import com.cheradip.packbuilder.model.PhraseTranslation
import com.cheradip.packbuilder.model.QaPair
import com.cheradip.packbuilder.model.WordSeed
import com.cheradip.packbuilder.model.WordTranslation

object StarterVocabularyGenerator {
    private data class Concept(
        val lemma: String,
        val englishMeaning: String,
        val baseFreq: Double,
    )

    private val concepts = listOf(
        Concept("hello", "a greeting", 9800.0),
        Concept("thank", "express gratitude", 9700.0),
        Concept("yes", "affirmative response", 9600.0),
        Concept("no", "negative response", 9500.0),
        Concept("language", "system of communication", 9400.0),
        Concept("word", "single unit of language", 9300.0),
        Concept("book", "written work", 9200.0),
        Concept("read", "comprehend written text", 9100.0),
        Concept("learn", "gain knowledge", 9000.0),
        Concept("write", "form letters or words", 8900.0),
        Concept("speak", "use spoken language", 8800.0),
        Concept("good", "of high quality", 8700.0),
        Concept("morning", "early part of the day", 8600.0),
        Concept("water", "clear liquid essential for life", 8500.0),
        Concept("food", "substance eaten for nourishment", 8400.0),
        Concept("friend", "person you know and like", 8300.0),
        Concept("family", "group of related people", 8200.0),
        Concept("school", "place of learning", 8100.0),
        Concept("teacher", "person who teaches", 8000.0),
        Concept("student", "person who studies", 7900.0),
        Concept("please", "polite request word", 7800.0),
        Concept("sorry", "expression of regret", 7700.0),
        Concept("name", "word by which someone is known", 7600.0),
        Concept("day", "24-hour period", 7500.0),
        Concept("night", "dark part of the day", 7400.0),
        Concept("time", "measurable duration", 7300.0),
        Concept("question", "sentence asking for information", 7200.0),
        Concept("answer", "response to a question", 7100.0),
        Concept("world", "the earth; human society", 7000.0),
        Concept("country", "nation or territory", 6900.0),
    )

    /** Greetings and core lemmas for extended languages beyond tier-1. */
    private val extendedForms: Map<String, Map<String, String>> = mapOf(
        "ru" to mapOf(
            "hello" to "привет", "thank" to "спасибо", "yes" to "да", "no" to "нет",
            "language" to "язык", "word" to "слово", "book" to "книга", "read" to "читать",
            "learn" to "учить", "write" to "писать", "speak" to "говорить", "good" to "хороший",
            "morning" to "утро", "water" to "вода", "food" to "еда", "friend" to "друг",
            "family" to "семья", "school" to "школа", "teacher" to "учитель", "student" to "студент",
            "please" to "пожалуйста", "sorry" to "извините", "name" to "имя", "day" to "день",
            "night" to "ночь", "time" to "время", "question" to "вопрос", "answer" to "ответ",
            "world" to "мир", "country" to "страна",
        ),
        "it" to mapOf(
            "hello" to "ciao", "thank" to "grazie", "yes" to "sì", "no" to "no",
            "language" to "lingua", "word" to "parola", "book" to "libro", "read" to "leggere",
            "learn" to "imparare", "write" to "scrivere", "speak" to "parlare", "good" to "buono",
            "morning" to "mattina", "water" to "acqua", "food" to "cibo", "friend" to "amico",
            "family" to "famiglia", "school" to "scuola", "teacher" to "insegnante", "student" to "studente",
            "please" to "per favore", "sorry" to "scusa", "name" to "nome", "day" to "giorno",
            "night" to "notte", "time" to "tempo", "question" to "domanda", "answer" to "risposta",
            "world" to "mondo", "country" to "paese",
        ),
        "ko" to mapOf(
            "hello" to "안녕하세요", "thank" to "감사합니다", "yes" to "네", "no" to "아니요",
            "language" to "언어", "word" to "단어", "book" to "책", "read" to "읽다",
            "learn" to "배우다", "write" to "쓰다", "speak" to "말하다", "good" to "좋은",
            "morning" to "아침", "water" to "물", "food" to "음식", "friend" to "친구",
            "family" to "가족", "school" to "학교", "teacher" to "선생님", "student" to "학생",
            "please" to "제발", "sorry" to "미안", "name" to "이름", "day" to "날",
            "night" to "밤", "time" to "시간", "question" to "질문", "answer" to "답",
            "world" to "세계", "country" to "나라",
        ),
        "tr" to mapOf(
            "hello" to "merhaba", "thank" to "teşekkürler", "yes" to "evet", "no" to "hayır",
            "language" to "dil", "word" to "kelime", "book" to "kitap", "read" to "okumak",
            "learn" to "öğrenmek", "write" to "yazmak", "speak" to "konuşmak", "good" to "iyi",
            "morning" to "sabah", "water" to "su", "food" to "yemek", "friend" to "arkadaş",
            "family" to "aile", "school" to "okul", "teacher" to "öğretmen", "student" to "öğrenci",
            "please" to "lütfen", "sorry" to "özür dilerim", "name" to "isim", "day" to "gün",
            "night" to "gece", "time" to "zaman", "question" to "soru", "answer" to "cevap",
            "world" to "dünya", "country" to "ülke",
        ),
        "nl" to mapOf(
            "hello" to "hallo", "thank" to "dank je", "yes" to "ja", "no" to "nee",
            "language" to "taal", "word" to "woord", "book" to "boek", "read" to "lezen",
            "learn" to "leren", "write" to "schrijven", "speak" to "spreken", "good" to "goed",
            "morning" to "ochtend", "water" to "water", "food" to "eten", "friend" to "vriend",
            "family" to "familie", "school" to "school", "teacher" to "leraar", "student" to "student",
            "please" to "alsjeblieft", "sorry" to "sorry", "name" to "naam", "day" to "dag",
            "night" to "nacht", "time" to "tijd", "question" to "vraag", "answer" to "antwoord",
            "world" to "wereld", "country" to "land",
        ),
        "pl" to mapOf(
            "hello" to "cześć", "thank" to "dziękuję", "yes" to "tak", "no" to "nie",
            "language" to "język", "word" to "słowo", "book" to "książka", "read" to "czytać",
            "learn" to "uczyć się", "write" to "pisać", "speak" to "mówić", "good" to "dobry",
            "morning" to "rano", "water" to "woda", "food" to "jedzenie", "friend" to "przyjaciel",
            "family" to "rodzina", "school" to "szkoła", "teacher" to "nauczyciel", "student" to "uczeń",
            "please" to "proszę", "sorry" to "przepraszam", "name" to "imię", "day" to "dzień",
            "night" to "noc", "time" to "czas", "question" to "pytanie", "answer" to "odpowiedź",
            "world" to "świat", "country" to "kraj",
        ),
        "sv" to mapOf(
            "hello" to "hej", "thank" to "tack", "yes" to "ja", "no" to "nej",
            "language" to "språk", "word" to "ord", "book" to "bok", "read" to "läsa",
            "learn" to "lära", "write" to "skriva", "speak" to "tala", "good" to "bra",
            "morning" to "morgon", "water" to "vatten", "food" to "mat", "friend" to "vän",
            "family" to "familj", "school" to "skola", "teacher" to "lärare", "student" to "elev",
            "please" to "snälla", "sorry" to "förlåt", "name" to "namn", "day" to "dag",
            "night" to "natt", "time" to "tid", "question" to "fråga", "answer" to "svar",
            "world" to "värld", "country" to "land",
        ),
        "uk" to mapOf(
            "hello" to "привіт", "thank" to "дякую", "yes" to "так", "no" to "ні",
            "language" to "мова", "word" to "слово", "book" to "книга", "read" to "читати",
            "learn" to "вчити", "write" to "писати", "speak" to "говорити", "good" to "добрий",
            "morning" to "ранок", "water" to "вода", "food" to "їжа", "friend" to "друг",
            "family" to "сім'я", "school" to "школа", "teacher" to "вчитель", "student" to "студент",
            "please" to "будь ласка", "sorry" to "вибачте", "name" to "ім'я", "day" to "день",
            "night" to "ніч", "time" to "час", "question" to "питання", "answer" to "відповідь",
            "world" to "світ", "country" to "країна",
        ),
        "vi" to mapOf(
            "hello" to "xin chào", "thank" to "cảm ơn", "yes" to "có", "no" to "không",
            "language" to "ngôn ngữ", "word" to "từ", "book" to "sách", "read" to "đọc",
            "learn" to "học", "write" to "viết", "speak" to "nói", "good" to "tốt",
            "morning" to "buổi sáng", "water" to "nước", "food" to "thức ăn", "friend" to "bạn",
            "family" to "gia đình", "school" to "trường", "teacher" to "giáo viên", "student" to "học sinh",
            "please" to "làm ơn", "sorry" to "xin lỗi", "name" to "tên", "day" to "ngày",
            "night" to "đêm", "time" to "thời gian", "question" to "câu hỏi", "answer" to "câu trả lời",
            "world" to "thế giới", "country" to "quốc gia",
        ),
        "th" to mapOf(
            "hello" to "สวัสดี", "thank" to "ขอบคุณ", "yes" to "ใช่", "no" to "ไม่",
            "language" to "ภาษา", "word" to "คำ", "book" to "หนังสือ", "read" to "อ่าน",
            "learn" to "เรียน", "write" to "เขียน", "speak" to "พูด", "good" to "ดี",
            "morning" to "เช้า", "water" to "น้ำ", "food" to "อาหาร", "friend" to "เพื่อน",
            "family" to "ครอบครัว", "school" to "โรงเรียน", "teacher" to "ครู", "student" to "นักเรียน",
            "please" to "โปรด", "sorry" to "ขอโทษ", "name" to "ชื่อ", "day" to "วัน",
            "night" to "กลางคืน", "time" to "เวลา", "question" to "คำถาม", "answer" to "คำตอบ",
            "world" to "โลก", "country" to "ประเทศ",
        ),
        "id" to mapOf(
            "hello" to "halo", "thank" to "terima kasih", "yes" to "ya", "no" to "tidak",
            "language" to "bahasa", "word" to "kata", "book" to "buku", "read" to "membaca",
            "learn" to "belajar", "write" to "menulis", "speak" to "berbicara", "good" to "baik",
            "morning" to "pagi", "water" to "air", "food" to "makanan", "friend" to "teman",
            "family" to "keluarga", "school" to "sekolah", "teacher" to "guru", "student" to "siswa",
            "please" to "tolong", "sorry" to "maaf", "name" to "nama", "day" to "hari",
            "night" to "malam", "time" to "waktu", "question" to "pertanyaan", "answer" to "jawaban",
            "world" to "dunia", "country" to "negara",
        ),
        "fa" to mapOf(
            "hello" to "سلام", "thank" to "متشکرم", "yes" to "بله", "no" to "نه",
            "language" to "زبان", "word" to "کلمه", "book" to "کتاب", "read" to "خواندن",
            "learn" to "یاد گرفتن", "write" to "نوشتن", "speak" to "صحبت کردن", "good" to "خوب",
            "morning" to "صبح", "water" to "آب", "food" to "غذا", "friend" to "دوست",
            "family" to "خانواده", "school" to "مدرسه", "teacher" to "معلم", "student" to "دانش‌آموز",
            "please" to "لطفاً", "sorry" to "متأسفم", "name" to "نام", "day" to "روز",
            "night" to "شب", "time" to "زمان", "question" to "سؤال", "answer" to "پاسخ",
            "world" to "جهان", "country" to "کشور",
        ),
        "he" to mapOf(
            "hello" to "שלום", "thank" to "תודה", "yes" to "כן", "no" to "לא",
            "language" to "שפה", "word" to "מילה", "book" to "ספר", "read" to "לקרוא",
            "learn" to "ללמוד", "write" to "לכתוב", "speak" to "לדבר", "good" to "טוב",
            "morning" to "בוקר", "water" to "מים", "food" to "אוכל", "friend" to "חבר",
            "family" to "משפחה", "school" to "בית ספר", "teacher" to "מורה", "student" to "תלמיד",
            "please" to "בבקשה", "sorry" to "סליחה", "name" to "שם", "day" to "יום",
            "night" to "לילה", "time" to "זמן", "question" to "שאלה", "answer" to "תשובה",
            "world" to "עולם", "country" to "מדינה",
        ),
        "el" to mapOf(
            "hello" to "γεια", "thank" to "ευχαριστώ", "yes" to "ναι", "no" to "όχι",
            "language" to "γλώσσα", "word" to "λέξη", "book" to "βιβλίο", "read" to "διαβάζω",
            "learn" to "μαθαίνω", "write" to "γράφω", "speak" to "μιλάω", "good" to "καλό",
            "morning" to "πρωί", "water" to "νερό", "food" to "φαγητό", "friend" to "φίλος",
            "family" to "οικογένεια", "school" to "σχολείο", "teacher" to "δάσκαλος", "student" to "μαθητής",
            "please" to "παρακαλώ", "sorry" to "συγγνώμη", "name" to "όνομα", "day" to "ημέρα",
            "night" to "νύχτα", "time" to "χρόνος", "question" to "ερώτηση", "answer" to "απάντηση",
            "world" to "κόσμος", "country" to "χώρα",
        ),
        "ro" to mapOf(
            "hello" to "salut", "thank" to "mulțumesc", "yes" to "da", "no" to "nu",
            "language" to "limbă", "word" to "cuvânt", "book" to "carte", "read" to "a citi",
            "learn" to "a învăța", "write" to "a scrie", "speak" to "a vorbi", "good" to "bun",
            "morning" to "dimineață", "water" to "apă", "food" to "mâncare", "friend" to "prieten",
            "family" to "familie", "school" to "școală", "teacher" to "profesor", "student" to "student",
            "please" to "vă rog", "sorry" to "scuze", "name" to "nume", "day" to "zi",
            "night" to "noapte", "time" to "timp", "question" to "întrebare", "answer" to "răspuns",
            "world" to "lume", "country" to "țară",
        ),
        "cs" to mapOf(
            "hello" to "ahoj", "thank" to "děkuji", "yes" to "ano", "no" to "ne",
            "language" to "jazyk", "word" to "slovo", "book" to "kniha", "read" to "číst",
            "learn" to "učit se", "write" to "psát", "speak" to "mluvit", "good" to "dobrý",
            "morning" to "ráno", "water" to "voda", "food" to "jídlo", "friend" to "přítel",
            "family" to "rodina", "school" to "škola", "teacher" to "učitel", "student" to "student",
            "please" to "prosím", "sorry" to "promiňte", "name" to "jméno", "day" to "den",
            "night" to "noc", "time" to "čas", "question" to "otázka", "answer" to "odpověď",
            "world" to "svět", "country" to "země",
        ),
        "hu" to mapOf(
            "hello" to "szia", "thank" to "köszönöm", "yes" to "igen", "no" to "nem",
            "language" to "nyelv", "word" to "szó", "book" to "könyv", "read" to "olvas",
            "learn" to "tanul", "write" to "ír", "speak" to "beszél", "good" to "jó",
            "morning" to "reggel", "water" to "víz", "food" to "étel", "friend" to "barát",
            "family" to "család", "school" to "iskola", "teacher" to "tanár", "student" to "diák",
            "please" to "kérlek", "sorry" to "bocsánat", "name" to "név", "day" to "nap",
            "night" to "éjszaka", "time" to "idő", "question" to "kérdés", "answer" to "válasz",
            "world" to "világ", "country" to "ország",
        ),
        "sw" to mapOf(
            "hello" to "jambo", "thank" to "asante", "yes" to "ndiyo", "no" to "hapana",
            "language" to "lugha", "word" to "neno", "book" to "kitabu", "read" to "soma",
            "learn" to "jifunza", "write" to "andika", "speak" to "sema", "good" to "nzuri",
            "morning" to "asubuhi", "water" to "maji", "food" to "chakula", "friend" to "rafiki",
            "family" to "familia", "school" to "shule", "teacher" to "mwalimu", "student" to "mwanafunzi",
            "please" to "tafadhali", "sorry" to "samahani", "name" to "jina", "day" to "siku",
            "night" to "usiku", "time" to "wakati", "question" to "swali", "answer" to "jibu",
            "world" to "dunia", "country" to "nchi",
        ),
        "ta" to mapOf(
            "hello" to "வணக்கம்", "thank" to "நன்றி", "yes" to "ஆம்", "no" to "இல்லை",
            "language" to "மொழி", "word" to "சொல்", "book" to "புத்தகம்", "read" to "படிக்க",
            "learn" to "கற்க", "write" to "எழுத", "speak" to "பேச", "good" to "நல்ல",
            "morning" to "காலை", "water" to "தண்ணீர்", "food" to "உணவு", "friend" to "நண்பர்",
            "family" to "குடும்பம்", "school" to "பள்ளி", "teacher" to "ஆசிரியர்", "student" to "மாணவர்",
            "please" to "தயவு", "sorry" to "மன்னிக்கவும்", "name" to "பெயர்", "day" to "நாள்",
            "night" to "இரவு", "time" to "நேரம்", "question" to "கேள்வி", "answer" to "பதில்",
            "world" to "உலகம்", "country" to "நாடு",
        ),
        "te" to mapOf(
            "hello" to "నమస్కారం", "thank" to "ధన్యవాదాలు", "yes" to "అవును", "no" to "కాదు",
            "language" to "భాష", "word" to "పదం", "book" to "పుస్తకం", "read" to "చదవు",
            "learn" to "నేర్చుకో", "write" to "రాయు", "speak" to "మాట్లాడు", "good" to "మంచి",
            "morning" to "ఉదయం", "water" to "నీరు", "food" to "ఆహారం", "friend" to "స్నేహితుడు",
            "family" to "కుటుంబం", "school" to "పాఠశాల", "teacher" to "ఉపాధ్యాయుడు", "student" to "విద్యార్థి",
            "please" to "దయచేసి", "sorry" to "క్షమించండి", "name" to "పేరు", "day" to "రోజు",
            "night" to "రాత్రి", "time" to "సమయం", "question" to "ప్రశ్న", "answer" to "సమాధానం",
            "world" to "ప్రపంచం", "country" to "దేశం",
        ),
        "mr" to mapOf(
            "hello" to "नमस्कार", "thank" to "धन्यवाद", "yes" to "हो", "no" to "नाही",
            "language" to "भाषा", "word" to "शब्द", "book" to "पुस्तक", "read" to "वाचणे",
            "learn" to "शिकणे", "write" to "लिहिणे", "speak" to "बोलणे", "good" to "चांगले",
            "morning" to "सकाळ", "water" to "पाणी", "food" to "अन्न", "friend" to "मित्र",
            "family" to "कुटुंब", "school" to "शाळा", "teacher" to "शिक्षक", "student" to "विद्यार्थी",
            "please" to "कृपया", "sorry" to "माफ करा", "name" to "नाव", "day" to "दिवस",
            "night" to "रात्र", "time" to "वेळ", "question" to "प्रश्न", "answer" to "उत्तर",
            "world" to "जग", "country" to "देश",
        ),
        "ur" to mapOf(
            "hello" to "سلام", "thank" to "شکریہ", "yes" to "ہاں", "no" to "نہیں",
            "language" to "زبان", "word" to "لفظ", "book" to "کتاب", "read" to "پڑھنا",
            "learn" to "سیکھنا", "write" to "لکھنا", "speak" to "بولنا", "good" to "اچھا",
            "morning" to "صبح", "water" to "پانی", "food" to "کھانا", "friend" to "دوست",
            "family" to "خاندان", "school" to "اسکول", "teacher" to "استاد", "student" to "طالب علم",
            "please" to "براہ کرم", "sorry" to "معاف کیجئے", "name" to "نام", "day" to "دن",
            "night" to "رات", "time" to "وقت", "question" to "سوال", "answer" to "جواب",
            "world" to "دنیا", "country" to "ملک",
        ),
    )

    fun generate(entry: LanguageCatalogEntry): LanguagePackSeed {
        Tier1PackData.get(entry.code)?.let { return it }

        val code = entry.code.lowercase()
        val forms = extendedForms[code]
        val words = concepts.map { concept ->
            val surface = forms?.get(concept.lemma)
                ?: fallbackWord(entry, concept.lemma)
            WordSeed(
                word = surface,
                lemma = surface.lowercase(),
                language = code,
                frequencyScore = concept.baseFreq,
                meanings = listOf(
                    "${concept.englishMeaning} (${entry.name})",
                    if (forms != null) concept.englishMeaning else "Common concept in ${entry.name}",
                ),
            )
        } + WordSeed(
            word = entry.nativeName,
            lemma = entry.nativeName.lowercase(),
            language = code,
            frequencyScore = 9950.0,
            meanings = listOf(
                "Native name for ${entry.name}",
                "The ${entry.name} language",
            ),
        )

        val helloWord = forms?.get("hello") ?: entry.nativeName
        val phrases = listOf(
            PhraseTranslation(
                helloWord,
                code,
                code,
                "Standard greeting in ${entry.name}.",
            ),
            PhraseTranslation(
                "thank you",
                "en",
                code,
                forms?.get("thank") ?: "thank you (${entry.name})",
            ),
        )

        val wordTranslations = listOf(
            WordTranslation(helloWord, code, "hello", "en"),
            WordTranslation("hello", "en", helloWord, code),
        )

        val qaPairs = listOf(
            QaPair(
                "What is ${entry.name}?",
                "en",
                "${entry.name} (${entry.nativeName}) is a language spoken in ${entry.region}.",
                "en",
            ),
        )

        return LanguagePackSeed(
            languageCode = code,
            words = words,
            phrases = phrases,
            wordTranslations = wordTranslations,
            qaPairs = qaPairs,
        )
    }

    private fun fallbackWord(entry: LanguageCatalogEntry, lemma: String): String {
        val prefix = entry.name.take(12).replace(" ", "")
        return when (lemma) {
            "hello" -> entry.nativeName.take(24)
            "language" -> entry.nativeName.take(24)
            else -> "$prefix-$lemma"
        }
    }
}
