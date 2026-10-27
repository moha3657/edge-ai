package com.example.service

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.net.Uri
import com.google.mlkit.vision.common.InputImage
import com.google.mlkit.vision.text.TextRecognition
import com.google.mlkit.vision.text.latin.TextRecognizerOptions
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.tasks.await
import kotlinx.coroutines.withContext
import java.io.InputStream

data class OcrResult(
    val hasText: Boolean,
    val extractedText: String,
    val lineCount: Int,
    val blockCount: Int,
    val imageWidth: Int,
    val imageHeight: Int
)

class RealImageOcrEngine(private val context: Context) {

    private val recognizer = TextRecognition.getClient(TextRecognizerOptions.DEFAULT_OPTIONS)

    /**
     * Decodes a Bitmap from an Android content Uri, scaling down if large to fit 4GB RAM budget.
     */
    fun loadSampledBitmapFromUri(uri: Uri, maxDimension: Int = 1440): Bitmap? {
        return try {
            val options = BitmapFactory.Options().apply { inJustDecodeBounds = true }
            var stream: InputStream? = context.contentResolver.openInputStream(uri)
            BitmapFactory.decodeStream(stream, null, options)
            stream?.close()

            val w = options.outWidth
            val h = options.outHeight
            if (w <= 0 || h <= 0) return null

            var inSampleSize = 1
            var maxDim = maxOf(w, h)
            while (maxDim > maxDimension) {
                inSampleSize *= 2
                maxDim /= 2
            }

            val decodeOptions = BitmapFactory.Options().apply {
                this.inSampleSize = inSampleSize
                inPreferredConfig = Bitmap.Config.ARGB_8888
            }

            stream = context.contentResolver.openInputStream(uri)
            val bitmap = BitmapFactory.decodeStream(stream, null, decodeOptions)
            stream?.close()
            bitmap
        } catch (e: Exception) {
            null
        }
    }

    /**
     * Executes real on-device Google ML Kit OCR on the given bitmap.
     */
    suspend fun processBitmap(bitmap: Bitmap): OcrResult = withContext(Dispatchers.Default) {
        val inputImage = InputImage.fromBitmap(bitmap, 0)
        try {
            val visionText = recognizer.process(inputImage).await()
            val text = visionText.text.trim()
            val blocks = visionText.textBlocks

            var lineCount = 0
            for (block in blocks) {
                lineCount += block.lines.size
            }

            if (text.isNotBlank()) {
                OcrResult(
                    hasText = true,
                    extractedText = text,
                    lineCount = lineCount,
                    blockCount = blocks.size,
                    imageWidth = bitmap.width,
                    imageHeight = bitmap.height
                )
            } else {
                OcrResult(
                    hasText = false,
                    extractedText = "",
                    lineCount = 0,
                    blockCount = 0,
                    imageWidth = bitmap.width,
                    imageHeight = bitmap.height
                )
            }
        } catch (e: Exception) {
            OcrResult(
                hasText = false,
                extractedText = "OCR Recognition Error: ${e.localizedMessage}",
                lineCount = 0,
                blockCount = 0,
                imageWidth = bitmap.width,
                imageHeight = bitmap.height
            )
        }
    }
}
