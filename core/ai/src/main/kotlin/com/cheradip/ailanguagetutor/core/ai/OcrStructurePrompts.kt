package com.cheradip.ailanguagetutor.core.ai

import com.cheradip.ailanguagetutor.core.model.ScannedContentType

internal object OcrStructurePrompts {

    fun build(contentType: ScannedContentType, rawOcr: String, languageCode: String): String {
        val clipped = rawOcr.take(6000)
        return when (contentType) {
            ScannedContentType.MATH -> """
                You are an expert at reconstructing mathematics from noisy OCR scans.
                Language context: $languageCode.
                
                Raw OCR text:
                ---
                $clipped
                ---
                
                Tasks:
                1. Reconstruct equations and math notation clearly (use Unicode math symbols or LaTeX where helpful).
                2. Fix OCR errors (confused 0/O, 1/l, missing operators, broken fractions, split lines).
                3. Preserve step order for multi-line derivations.
                4. Group related lines; add short section headings if needed.
                5. Note any missing or uncertain parts at the end under "## Gaps detected".
                
                Reply with ONLY the cleaned, structured mathematics text. No preamble.
            """.trimIndent()

            ScannedContentType.CODE -> """
                You are an expert at reconstructing source code from OCR scans.
                
                Raw OCR text:
                ---
                $clipped
                ---
                
                Tasks:
                1. Format as proper code with correct indentation and line breaks.
                2. Fix OCR typos in keywords, brackets, semicolons, and string literals.
                3. Detect the programming language and wrap the main code in a fenced ``` block with the language tag.
                4. Under "## Analysis", list likely missing lines, incomplete blocks, or syntax issues.
                5. Do not invent large missing sections — mark them as [missing: …].
                
                Reply with ONLY the structured output (code block + analysis). No preamble.
            """.trimIndent()

            ScannedContentType.FLOWCHART -> """
                You are reconstructing a flowchart or process diagram from OCR labels and arrows.
                
                Raw OCR text:
                ---
                $clipped
                ---
                
                Tasks:
                1. Organize nodes in logical top-to-bottom or left-to-right order.
                2. Show connections with arrows (→ or ->).
                3. Mark decision points clearly (Yes/No branches).
                4. Fix OCR label errors.
                5. Note missing or unclear connections under "## Gaps detected".
                
                Reply with ONLY the structured flowchart text. No preamble.
            """.trimIndent()

            ScannedContentType.DIAGRAM -> """
                The OCR found very little text on what appears to be a diagram or image-heavy page.
                
                Raw OCR text:
                ---
                $clipped
                ---
                
                Tasks:
                1. List any readable labels, captions, or legend text.
                2. Describe what type of diagram this likely is (chart, map, photo, schematic).
                3. Note that the user should refer to the original scan image for visual details.
                
                Keep it brief. Reply with ONLY structured notes. No preamble.
            """.trimIndent()

            ScannedContentType.MIXED -> """
                This scan contains mixed content (e.g. code + prose, or math + text).
                Language: $languageCode.
                
                Raw OCR:
                ---
                $clipped
                ---
                
                Tasks:
                1. Separate sections with clear headings (## Prose, ## Code, ## Math, etc.).
                2. Use ``` code fences for code; fix OCR in each section.
                3. Fix errors and restore logical reading order.
                4. Note gaps under "## Gaps detected".
                
                Reply with ONLY the structured document. No preamble.
            """.trimIndent()

            ScannedContentType.PROSE -> """
                Fix OCR errors and structure this scanned document text.
                Language: $languageCode.
                
                Raw OCR:
                ---
                $clipped
                ---
                
                Tasks:
                1. Restore paragraphs and line breaks.
                2. Fix character errors, broken words, and merged/split lines.
                3. Preserve headings if present.
                4. Do not summarize — keep all content.
                
                Reply with ONLY the cleaned text. No preamble.
            """.trimIndent()
        }
    }
}
