package com.cheradip.ailanguagetutor.feature.help

sealed class ManualBlock {
    data class Heading(val level: Int, val text: String) : ManualBlock()
    data class Paragraph(val text: String) : ManualBlock()
    data class Bullet(val text: String) : ManualBlock()
    data class Code(val text: String) : ManualBlock()
    data object Divider : ManualBlock()
    data object Spacer : ManualBlock()
}

object ManualParser {
    fun parse(markdown: String): List<ManualBlock> {
        val blocks = mutableListOf<ManualBlock>()
        val lines = markdown.lines()
        var i = 0
        val paragraphBuffer = StringBuilder()

        fun flushParagraph() {
            val text = paragraphBuffer.toString().trim()
            if (text.isNotEmpty()) {
                blocks.add(ManualBlock.Paragraph(text))
            }
            paragraphBuffer.clear()
        }

        while (i < lines.size) {
            val line = lines[i]
            when {
                line.startsWith("```") -> {
                    flushParagraph()
                    val code = StringBuilder()
                    i++
                    while (i < lines.size && !lines[i].startsWith("```")) {
                        if (code.isNotEmpty()) code.append('\n')
                        code.append(lines[i])
                        i++
                    }
                    blocks.add(ManualBlock.Code(code.toString()))
                }
                line.startsWith("### ") -> {
                    flushParagraph()
                    blocks.add(ManualBlock.Heading(3, line.removePrefix("### ").trim()))
                }
                line.startsWith("## ") -> {
                    flushParagraph()
                    blocks.add(ManualBlock.Heading(2, line.removePrefix("## ").trim()))
                }
                line.startsWith("# ") -> {
                    flushParagraph()
                    blocks.add(ManualBlock.Heading(1, line.removePrefix("# ").trim()))
                }
                line.trim() == "---" -> {
                    flushParagraph()
                    blocks.add(ManualBlock.Divider)
                }
                line.startsWith("- ") || line.startsWith("* ") -> {
                    flushParagraph()
                    blocks.add(ManualBlock.Bullet(line.drop(2).trim()))
                }
                line.trim().isEmpty() -> {
                    flushParagraph()
                    blocks.add(ManualBlock.Spacer)
                }
                else -> {
                    if (paragraphBuffer.isNotEmpty()) paragraphBuffer.append(' ')
                    paragraphBuffer.append(line.trim())
                }
            }
            i++
        }
        flushParagraph()
        return blocks
    }
}
