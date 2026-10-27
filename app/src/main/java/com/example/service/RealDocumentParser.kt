package com.example.service

import android.content.Context
import android.graphics.Bitmap
import android.graphics.Color
import android.graphics.pdf.PdfRenderer
import android.net.Uri
import android.os.ParcelFileDescriptor
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.BufferedReader
import java.io.File
import java.io.FileOutputStream
import java.io.InputStreamReader

data class ParsedDocumentResult(
    val filename: String,
    val fileType: String,
    val content: String,
    val pageCount: Int,
    val fileSizeFormatted: String
)

class RealDocumentParser(
    private val context: Context,
    private val ocrEngine: RealImageOcrEngine
) {

    suspend fun parseUri(uri: Uri): ParsedDocumentResult = withContext(Dispatchers.IO) {
        val filename = getFilename(uri)
        val isPdf = filename.endsWith(".pdf", ignoreCase = true)

        if (isPdf) {
            parsePdfUri(uri, filename)
        } else {
            parseTextUri(uri, filename)
        }
    }

    private fun getFilename(uri: Uri): String {
        return uri.lastPathSegment?.substringAfterLast('/') ?: "Uploaded_Document.txt"
    }

    private fun parseTextUri(uri: Uri, filename: String): ParsedDocumentResult {
        val inputStream = context.contentResolver.openInputStream(uri)
            ?: throw IllegalStateException("Cannot open file stream for $uri")

        val reader = BufferedReader(InputStreamReader(inputStream, Charsets.UTF_8))
        val text = reader.readText()
        reader.close()

        val sizeBytes = text.toByteArray().size
        val sizeFormatted = formatFileSize(sizeBytes)

        val cleanText = if (text.isBlank()) {
            "Empty document content in $filename."
        } else {
            text
        }

        return ParsedDocumentResult(
            filename = filename,
            fileType = "TXT",
            content = cleanText,
            pageCount = (cleanText.length / 1800).coerceAtLeast(1),
            fileSizeFormatted = sizeFormatted
        )
    }

    private suspend fun parsePdfUri(uri: Uri, filename: String): ParsedDocumentResult {
        // Copy URI to a local temporary cache file to obtain seekable FileDescriptor
        val tempFile = File(context.cacheDir, "temp_pdf_${System.currentTimeMillis()}.pdf")
        val inputStream = context.contentResolver.openInputStream(uri)
            ?: throw IllegalStateException("Cannot open PDF stream")

        val outputStream = FileOutputStream(tempFile)
        inputStream.copyTo(outputStream)
        inputStream.close()
        outputStream.close()

        val fileSizeFormatted = formatFileSize(tempFile.length().toInt())

        var pfd: ParcelFileDescriptor? = null
        var renderer: PdfRenderer? = null
        val extractedTextBuilder = StringBuilder()
        var pageCount = 0

        try {
            pfd = ParcelFileDescriptor.open(tempFile, ParcelFileDescriptor.MODE_READ_ONLY)
            renderer = PdfRenderer(pfd)
            pageCount = renderer.pageCount

            // Render and OCR up to 6 pages to preserve low-memory 4GB RAM ceiling
            val pagesToProcess = minOf(pageCount, 6)

            for (pageIndex in 0 until pagesToProcess) {
                val page = renderer.openPage(pageIndex)
                // 1080p target width for crisp OCR without high RAM allocation
                val scale = 1080f / page.width.toFloat()
                val targetW = 1080
                val targetH = (page.height * scale).toInt()

                val bitmap = Bitmap.createBitmap(targetW, targetH, Bitmap.Config.ARGB_8888)
                bitmap.eraseColor(Color.WHITE)

                page.render(bitmap, null, null, PdfRenderer.Page.RENDER_MODE_FOR_DISPLAY)
                page.close()

                // Run real OCR on the rendered PDF page
                val ocrResult = ocrEngine.processBitmap(bitmap)
                bitmap.recycle() // Reclaim bitmap memory immediately

                if (ocrResult.hasText) {
                    if (extractedTextBuilder.isNotEmpty()) {
                        extractedTextBuilder.append("\n\n")
                    }
                    extractedTextBuilder.append("--- PAGE ${pageIndex + 1} ---\n")
                    extractedTextBuilder.append(ocrResult.extractedText)
                }
            }
        } catch (e: Exception) {
            extractedTextBuilder.append("PDF Rendering Notice: ${e.localizedMessage}")
        } finally {
            try {
                renderer?.close()
                pfd?.close()
                tempFile.delete()
            } catch (_: Exception) {}
        }

        val finalContent = if (extractedTextBuilder.isBlank()) {
            "PDF document '$filename' loaded ($pageCount pages). No machine-readable or OCR text was detected on the rendered pages."
        } else {
            extractedTextBuilder.toString()
        }

        return ParsedDocumentResult(
            filename = filename,
            fileType = "PDF",
            content = finalContent,
            pageCount = pageCount.coerceAtLeast(1),
            fileSizeFormatted = fileSizeFormatted
        )
    }

    private fun formatFileSize(sizeBytes: Int): String {
        return when {
            sizeBytes < 1024 -> "$sizeBytes B"
            sizeBytes < 1024 * 1024 -> "${sizeBytes / 1024} KB"
            else -> String.format("%.1f MB", sizeBytes / (1024.0 * 1024.0))
        }
    }
}
