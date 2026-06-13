package com.cheradip.ailanguagetutor.core.image

import android.content.Context
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.RectF
import com.tom_roush.pdfbox.android.PDFBoxResourceLoader
import com.tom_roush.pdfbox.pdmodel.PDDocument
import com.tom_roush.pdfbox.pdmodel.PDPage
import com.tom_roush.pdfbox.pdmodel.PDPageContentStream
import com.tom_roush.pdfbox.pdmodel.common.PDRectangle
import com.tom_roush.pdfbox.pdmodel.encryption.AccessPermission
import com.tom_roush.pdfbox.pdmodel.encryption.StandardProtectionPolicy
import com.tom_roush.pdfbox.pdmodel.graphics.image.JPEGFactory
import com.tom_roush.pdfbox.pdmodel.interactive.documentnavigation.outline.PDDocumentOutline
import com.tom_roush.pdfbox.pdmodel.interactive.documentnavigation.outline.PDOutlineItem
import java.io.ByteArrayOutputStream
import java.io.File
import java.io.FileOutputStream
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import kotlin.math.roundToInt

class ScanExportService(context: Context) {
    private val appContext = context.applicationContext

    @Volatile
    private var pdfBoxReady = false

    private fun ensurePdfBoxReady() {
        if (pdfBoxReady) return
        synchronized(this) {
            if (pdfBoxReady) return
            PDFBoxResourceLoader.init(appContext)
            pdfBoxReady = true
        }
    }

    private val baseDir: File
        get() {
            val public = android.os.Environment.getExternalStoragePublicDirectory(
                android.os.Environment.DIRECTORY_DOCUMENTS,
            )
            val dir = File(public, "AILanguageTutor")
            if (dir.exists() || dir.mkdirs()) return dir
            val fallbackRoot = appContext.getExternalFilesDir(android.os.Environment.DIRECTORY_DOCUMENTS)
                ?: appContext.filesDir
            return File(fallbackRoot, "AILanguageTutor").also { it.mkdirs() }
        }

    data class ExportResult(val paths: List<String>, val format: ExportFormat)

    fun export(pagePaths: List<String>, options: ExportOptions): ExportResult {
        ensurePdfBoxReady()
        val bitmaps = pagePaths.map { BitmapUtils.load(it) }
        return when (options.format) {
            ExportFormat.PDF -> ExportResult(listOf(exportPdf(bitmaps, options)), ExportFormat.PDF)
            ExportFormat.IMAGES -> ExportResult(exportImages(bitmaps, options), ExportFormat.IMAGES)
            ExportFormat.LONG_IMAGE -> ExportResult(listOf(exportLongImage(bitmaps, options)), ExportFormat.LONG_IMAGE)
        }
    }

    private fun exportPdf(bitmaps: List<Bitmap>, options: ExportOptions): String {
        val dir = File(baseDir, "PDF").also { it.mkdirs() }
        val baseName = sanitizedName(options.documentName.ifBlank { "Document" })
        val name = if (options.title.isNotBlank()) "${sanitizedName(options.title)}.pdf" else "$baseName.pdf"
        val file = File(dir, name)
        val quality = jpegQuality(options)
        PDDocument().use { document ->
            document.documentInformation.author = options.author.ifBlank { null }
            document.documentInformation.title = options.title.ifBlank { baseName }
            document.documentInformation.subject = options.subject.ifBlank { null }
            document.documentInformation.keywords = options.keywords.ifBlank { null }
            val outline = PDDocumentOutline()
            document.documentCatalog.documentOutline = outline
            bitmaps.forEachIndexed { index, bitmap ->
                val rendered = renderPageBitmap(bitmap, options)
                val (pageW, pageH) = rendered.width to rendered.height
                val page = PDPage(PDRectangle(pageW.toFloat(), pageH.toFloat()))
                document.addPage(page)
                val jpegBytes = bitmapToJpegBytes(rendered, quality)
                val image = JPEGFactory.createFromByteArray(document, jpegBytes)
                PDPageContentStream(document, page).use { cs ->
                    cs.drawImage(image, 0f, 0f, pageW.toFloat(), pageH.toFloat())
                }
                val item = PDOutlineItem()
                item.title = "Page ${index + 1}"
                outline.addLast(item)
            }
            if (options.passwordEnabled && options.password.isNotBlank()) {
                val permissions = AccessPermission()
                permissions.setCanPrint(true)
                val policy = StandardProtectionPolicy(
                    options.password,
                    options.password,
                    permissions,
                )
                policy.encryptionKeyLength = 128
                document.protect(policy)
            }
            FileOutputStream(file).use { document.save(it) }
        }
        registerMedia(file, "application/pdf")
        return file.absolutePath
    }

    /** Lays out source on page canvas with margins, orientation, and optional watermark. */
    private fun renderPageBitmap(source: Bitmap, options: ExportOptions): Bitmap {
        val (pageW, pageH) = resolvePageSize(options, source)
        val margin = marginPx(options.margins)
        val result = Bitmap.createBitmap(pageW, pageH, Bitmap.Config.ARGB_8888)
        val canvas = Canvas(result)
        canvas.drawColor(Color.WHITE)
        val availW = pageW - margin * 2
        val availH = pageH - margin * 2
        if (availW > 0 && availH > 0) {
            val scale = minOf(availW.toFloat() / source.width, availH.toFloat() / source.height)
            val drawW = source.width * scale
            val drawH = source.height * scale
            val x = margin + (availW - drawW) / 2f
            val y = margin + (availH - drawH) / 2f
            canvas.drawBitmap(source, null, RectF(x, y, x + drawW, y + drawH), null)
        }
        resolveWatermarkText(options)?.let { drawWatermark(canvas, it, pageW, pageH, margin) }
        return result
    }

    private fun drawWatermark(canvas: Canvas, text: String, pageW: Int, pageH: Int, margin: Int) {
        val paint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = 0x99000000.toInt()
            textSize = (pageW / 40f).coerceIn(18f, 36f)
        }
        canvas.drawText(text, margin.toFloat(), pageH - margin / 2f, paint)
    }

    private fun bitmapToJpegBytes(bitmap: Bitmap, quality: Int): ByteArray {
        val stream = ByteArrayOutputStream()
        bitmap.compress(Bitmap.CompressFormat.JPEG, quality, stream)
        return stream.toByteArray()
    }

    private fun resolveWatermarkText(options: ExportOptions): String? {
        when (options.watermarkMode) {
            WatermarkMode.NONE -> if (!options.useTimestampWatermark && options.watermark.isBlank()) return null
            WatermarkMode.CUSTOM -> return options.watermark.takeIf { it.isNotBlank() }
            WatermarkMode.TIMESTAMP -> return SimpleDateFormat("yyyy-MM-dd HH:mm", Locale.US).format(Date())
        }
        if (options.watermark.isNotBlank()) return options.watermark
        if (options.useTimestampWatermark) {
            return SimpleDateFormat("yyyy-MM-dd HH:mm", Locale.US).format(Date())
        }
        return null
    }

    private fun resolvePageSize(options: ExportOptions, bitmap: Bitmap): Pair<Int, Int> {
        val dpi = 72f
        var (w, h) = when (options.pageSize) {
            ExportPageSize.A4 -> (210 / 25.4 * dpi).roundToInt() to (297 / 25.4 * dpi).roundToInt()
            ExportPageSize.LETTER -> (8.5 * dpi).roundToInt() to (11 * dpi).roundToInt()
            ExportPageSize.LEGAL -> (8.5 * dpi).roundToInt() to (14 * dpi).roundToInt()
            ExportPageSize.ORIGINAL -> bitmap.width to bitmap.height
        }
        val landscape = when (options.orientation) {
            ExportOrientation.LANDSCAPE -> true
            ExportOrientation.PORTRAIT -> false
            ExportOrientation.AUTO -> bitmap.width > bitmap.height
        }
        if (landscape && w < h) {
            val tmp = w; w = h; h = tmp
        } else if (!landscape && w > h) {
            val tmp = w; w = h; h = tmp
        }
        return w to h
    }

    private fun marginPx(margins: ExportMargins): Int = when (margins) {
        ExportMargins.NONE -> 0
        ExportMargins.SMALL -> 24
        ExportMargins.MEDIUM -> 48
        ExportMargins.LARGE -> 72
    }

    private fun longImageSpacing(margins: ExportMargins): Int = when (margins) {
        ExportMargins.NONE -> 0
        ExportMargins.SMALL -> 8
        ExportMargins.MEDIUM -> 16
        ExportMargins.LARGE -> 32
    }

    private fun exportImages(bitmaps: List<Bitmap>, options: ExportOptions): List<String> {
        val dir = File(baseDir, "Images").also { it.mkdirs() }
        val prefix = resolveImagePrefix(dir, options.documentName)
        val quality = jpegQuality(options)
        return bitmaps.mapIndexed { index, bitmap ->
            val rendered = renderPageBitmap(bitmap, options)
            val fileName = if (options.documentName.isNotBlank()) {
                "${sanitizedName(options.documentName)}_${index + 1}.jpg"
            } else {
                "${prefix}${index + 1}.jpg"
            }
            val file = File(dir, fileName)
            BitmapUtils.save(rendered, file.absolutePath, quality)
            registerMedia(file, "image/jpeg")
            file.absolutePath
        }
    }

    private fun exportLongImage(bitmaps: List<Bitmap>, options: ExportOptions): String {
        val dir = File(baseDir, "LongImages").also { it.mkdirs() }
        val renderedPages = bitmaps.map { renderPageBitmap(it, options) }
        val width = renderedPages.maxOf { it.width }
        val spacing = longImageSpacing(options.margins)
        val height = renderedPages.sumOf { it.height } + spacing * (renderedPages.size - 1).coerceAtLeast(0)
        val longBitmap = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888)
        val canvas = Canvas(longBitmap)
        canvas.drawColor(Color.WHITE)
        var y = 0
        renderedPages.forEach { page ->
            val x = ((width - page.width) / 2f).roundToInt()
            canvas.drawBitmap(page, x.toFloat(), y.toFloat(), null)
            y += page.height + spacing
        }
        val name = sanitizedName(options.documentName.ifBlank { "long_image" }) + ".jpg"
        val file = File(dir, name)
        BitmapUtils.save(longBitmap, file.absolutePath, jpegQuality(options))
        registerMedia(file, "image/jpeg")
        return file.absolutePath
    }

    /** Returns prefix like image_a (no trailing underscore). Files: image_a1.jpg */
    fun resolveImagePrefix(dir: File, customName: String): String {
        if (customName.isNotBlank()) return sanitizedName(customName)
        val existing = dir.listFiles()
            ?.mapNotNull { file ->
                Regex("""^image_([a-z]+)\d+\.jpg$""").find(file.name)?.groupValues?.get(1)
            }?.toSet().orEmpty()
        var length = 1
        while (length <= 6) {
            for (candidate in generatePrefixes(length)) {
                if (candidate !in existing) return "image_$candidate"
            }
            length++
        }
        return "image_exp${System.currentTimeMillis()}"
    }

    private fun generatePrefixes(length: Int): List<String> {
        val out = mutableListOf<String>()
        fun recurse(current: String, remaining: Int) {
            if (remaining == 0) {
                out.add(current)
                return
            }
            for (c in 'a'..'z') recurse(current + c, remaining - 1)
        }
        recurse("", length)
        return out
    }

    private fun sanitizedName(name: String): String =
        name.trim().replace(Regex("[^A-Za-z0-9._-]"), "_").ifBlank { "Document" }

    private fun jpegQuality(options: ExportOptions): Int {
        val base = when (options.quality) {
            ExportQuality.LOW -> 70
            ExportQuality.MEDIUM -> 82
            ExportQuality.HIGH -> 92
            ExportQuality.ORIGINAL -> 98
        }
        return when (options.compression) {
            ExportCompression.SMALL -> (base - 12).coerceAtLeast(60)
            ExportCompression.BALANCED -> base
            ExportCompression.MAXIMUM -> (base + 4).coerceAtMost(100)
        }
    }

    private fun registerMedia(file: File, mimeType: String) {
        if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.Q) {
            val values = android.content.ContentValues().apply {
                put(android.provider.MediaStore.MediaColumns.DISPLAY_NAME, file.name)
                put(android.provider.MediaStore.MediaColumns.MIME_TYPE, mimeType)
                put(
                    android.provider.MediaStore.MediaColumns.RELATIVE_PATH,
                    "Documents/AILanguageTutor/${file.parentFile?.name}",
                )
            }
            appContext.contentResolver.insert(
                android.provider.MediaStore.Files.getContentUri("external"),
                values,
            )
        }
    }
}
