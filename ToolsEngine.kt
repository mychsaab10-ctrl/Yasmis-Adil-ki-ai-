package com.yesmin.ai.tools

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Color
import android.graphics.pdf.PdfRenderer
import android.net.Uri
import android.os.ParcelFileDescriptor
import android.util.Base64
import com.google.mlkit.vision.common.InputImage
import com.google.mlkit.vision.text.TextRecognition
import com.google.mlkit.vision.text.latin.TextRecognizerOptions
import com.yesmin.ai.ai.Attach
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.withContext
import java.io.ByteArrayOutputStream
import kotlin.coroutines.resume
import kotlin.coroutines.resumeWithException

object ToolsEngine {

    fun bitmapToAttach(bmp: Bitmap, maxSide: Int = 1280): Attach {
        val scale = minOf(1f, maxSide.toFloat() / maxOf(bmp.width, bmp.height))
        val b = if (scale < 1f) Bitmap.createScaledBitmap(bmp, (bmp.width * scale).toInt(), (bmp.height * scale).toInt(), true) else bmp
        val out = ByteArrayOutputStream()
        b.compress(Bitmap.CompressFormat.JPEG, 85, out)
        return Attach("image/jpeg", Base64.encodeToString(out.toByteArray(), Base64.NO_WRAP))
    }

    suspend fun loadBitmap(ctx: Context, uri: Uri): Bitmap? = withContext(Dispatchers.IO) {
        try {
            val opts = BitmapFactory.Options().apply { inSampleSize = 2 }
            ctx.contentResolver.openInputStream(uri)?.use { BitmapFactory.decodeStream(it, null, opts) }
        } catch (e: Exception) { null }
    }

    /** On-device OCR (Latin script: English / Hinglish in Roman letters). Devanagari needs a different ML Kit model. */
    suspend fun ocr(bmp: Bitmap): String = suspendCancellableCoroutine { cont ->
        val rec = TextRecognition.getClient(TextRecognizerOptions.DEFAULT_OPTIONS)
        rec.process(InputImage.fromBitmap(bmp, 0))
            .addOnSuccessListener { cont.resume(it.text) }
            .addOnFailureListener { cont.resumeWithException(it) }
        cont.invokeOnCancellation { rec.close() }
    }

    /** Renders up to [maxPages] PDF pages to JPEG so Gemini can read them (works for scanned PDFs too). */
    suspend fun pdfPages(ctx: Context, uri: Uri, maxPages: Int = 8): Pair<List<Attach>, Int> = withContext(Dispatchers.IO) {
        val pfd: ParcelFileDescriptor = ctx.contentResolver.openFileDescriptor(uri, "r")
            ?: throw IllegalStateException("Cannot open the PDF.")
        pfd.use {
            PdfRenderer(it).use { r ->
                val total = r.pageCount
                val out = ArrayList<Attach>()
                for (i in 0 until minOf(total, maxPages)) {
                    r.openPage(i).use { p ->
                        val w = 1000
                        val h = (w.toFloat() * p.height / p.width).toInt()
                        val bmp = Bitmap.createBitmap(w, h, Bitmap.Config.ARGB_8888)
                        bmp.eraseColor(Color.WHITE)
                        p.render(bmp, null, null, PdfRenderer.Page.RENDER_MODE_FOR_DISPLAY)
                        out.add(bitmapToAttach(bmp, 1000))
                        bmp.recycle()
                    }
                }
                Pair(out, total)
            }
        }
    }
}
