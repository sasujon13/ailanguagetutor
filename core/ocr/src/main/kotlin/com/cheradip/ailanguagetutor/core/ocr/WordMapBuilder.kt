package com.cheradip.ailanguagetutor.core.ocr

import com.cheradip.ailanguagetutor.core.model.WordBoundingBox
import com.cheradip.ailanguagetutor.core.model.WordSpan
import com.google.mlkit.vision.text.Text
import org.json.JSONArray
import org.json.JSONObject
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class WordMapBuilder @Inject constructor() {

    fun buildFromMlKit(visionText: Text, fullText: String): List<WordSpan> {
        val words = mutableListOf<WordSpan>()
        var charIndex = 0
        for (block in visionText.textBlocks) {
            for (line in block.lines) {
                for (element in line.elements) {
                    val text = element.text
                    if (text.isBlank()) continue
                    val start = fullText.indexOf(text, charIndex).takeIf { it >= 0 } ?: charIndex
                    val end = start + text.length
                    charIndex = end
                    val box = element.boundingBox
                    words += WordSpan(
                        text = text,
                        startOffset = start,
                        endOffset = end,
                        boundingBox = box?.let {
                            WordBoundingBox(
                                left = it.left.toFloat(),
                                top = it.top.toFloat(),
                                right = it.right.toFloat(),
                                bottom = it.bottom.toFloat(),
                            )
                        },
                    )
                }
            }
        }
        if (words.isEmpty() && fullText.isNotBlank()) {
            fullText.split(Regex("\\s+")).filter { it.isNotBlank() }.fold(0) { offset, word ->
                val start = fullText.indexOf(word, offset)
                val end = start + word.length
                words += WordSpan(text = word, startOffset = start, endOffset = end)
                end
            }
        }
        return words
    }

    fun toJson(words: List<WordSpan>): String {
        val array = JSONArray()
        words.forEach { word ->
            array.put(
                JSONObject().apply {
                    put("text", word.text)
                    put("start", word.startOffset)
                    put("end", word.endOffset)
                    word.boundingBox?.let { box ->
                        put("box", JSONArray().apply {
                            put(box.left.toDouble())
                            put(box.top.toDouble())
                            put(box.right.toDouble())
                            put(box.bottom.toDouble())
                        })
                    }
                },
            )
        }
        return array.toString()
    }

    fun findWordAtOffset(words: List<WordSpan>, offset: Int): WordSpan? =
        words.firstOrNull { offset in it.startOffset until it.endOffset }

    /** Word spans for plain typed/voice text (no OCR bounding boxes). */
    fun buildFromPlainText(fullText: String): List<WordSpan> {
        if (fullText.isBlank()) return emptyList()
        val words = mutableListOf<WordSpan>()
        var searchFrom = 0
        Regex("\\S+").findAll(fullText).forEach { match ->
            val start = match.range.first
            if (start >= searchFrom) {
                words += WordSpan(
                    text = match.value,
                    startOffset = start,
                    endOffset = match.range.last + 1,
                )
                searchFrom = match.range.last + 1
            }
        }
        return words
    }
}
