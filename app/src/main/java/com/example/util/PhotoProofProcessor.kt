package com.example.util

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Matrix
import android.graphics.Paint
import android.graphics.Typeface
import android.media.ExifInterface
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File
import java.io.FileOutputStream
import java.security.MessageDigest
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

data class CapturedPhotoProof(
    val localPath: String,
    val capturedAtIso: String,
    val latitude: Double,
    val longitude: Double,
    val sha256: String,
)

object PhotoProofProcessor {
    suspend fun processAndStampPhoto(
        context: Context,
        rawFile: File,
        actId: String,
        zone: String,
        latitude: Double,
        longitude: Double,
    ): CapturedPhotoProof = withContext(Dispatchers.IO) {
        val photosDir = File(context.filesDir, "act_photos/$actId").apply { mkdirs() }
        val finalFile = File(photosDir, "proof_${System.currentTimeMillis()}.jpg")

        val decoded = decodeOrientedBitmap(rawFile, maxDimension = 1440)
        val stamped = decoded.copy(Bitmap.Config.ARGB_8888, true)
        if (stamped !== decoded) decoded.recycle()

        val now = Date()
        val displayTime = SimpleDateFormat("dd.MM.yyyy HH:mm:ss", Locale("ru", "RU")).format(now)
        val isoTime = SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss", Locale.US).format(now)
        val gpsText = if (latitude != 0.0 || longitude != 0.0) {
            String.format(Locale.US, "GPS %.5f, %.5f", latitude, longitude)
        } else {
            "GPS: фиксируется устройством"
        }

        val canvas = Canvas(stamped)
        val barHeight = (stamped.height * 0.11f).coerceIn(72f, 140f)
        val bgPaint = Paint().apply {
            color = Color.argb(196, 15, 23, 42)
            style = Paint.Style.FILL
        }
        canvas.drawRect(0f, stamped.height - barHeight, stamped.width.toFloat(), stamped.height.toFloat(), bgPaint)

        val accentPaint = Paint().apply {
            color = Color.rgb(16, 185, 129)
            style = Paint.Style.FILL
        }
        canvas.drawRect(0f, stamped.height - barHeight, 10f, stamped.height.toFloat(), accentPaint)

        val titleSize = (barHeight * 0.28f).coerceIn(18f, 34f)
        val subSize = (barHeight * 0.24f).coerceIn(15f, 28f)

        val titlePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = Color.WHITE
            textSize = titleSize
            typeface = Typeface.create(Typeface.MONOSPACE, Typeface.BOLD)
        }
        val subPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = Color.rgb(209, 250, 229)
            textSize = subSize
            typeface = Typeface.create(Typeface.MONOSPACE, Typeface.NORMAL)
        }

        val topY = stamped.height - barHeight + titleSize + 14f
        val bottomY = topY + subSize + 10f
        canvas.drawText("АктЖилья LIVE • $zone", 24f, topY, titlePaint)
        canvas.drawText("$displayTime • $gpsText", 24f, bottomY, subPaint)

        FileOutputStream(finalFile).use { out ->
            stamped.compress(Bitmap.CompressFormat.JPEG, 88, out)
        }
        stamped.recycle()
        rawFile.delete()

        val bytes = finalFile.readBytes()
        val digest = MessageDigest.getInstance("SHA-256").digest(bytes)
        val shaHex = digest.joinToString("") { "%02x".format(it) }

        CapturedPhotoProof(
            localPath = finalFile.absolutePath,
            capturedAtIso = isoTime,
            latitude = latitude,
            longitude = longitude,
            sha256 = shaHex,
        )
    }

    private fun decodeOrientedBitmap(file: File, maxDimension: Int): Bitmap {
        val opts = BitmapFactory.Options().apply { inJustDecodeBounds = true }
        BitmapFactory.decodeFile(file.absolutePath, opts)
        var sample = 1
        val maxSide = maxOf(opts.outWidth, opts.outHeight)
        while (maxSide / sample > maxDimension) {
            sample *= 2
        }
        val decodeOpts = BitmapFactory.Options().apply { inSampleSize = sample }
        val rawBitmap = BitmapFactory.decodeFile(file.absolutePath, decodeOpts)
            ?: Bitmap.createBitmap(800, 600, Bitmap.Config.ARGB_8888)

        val rotationDegrees = try {
            val exif = ExifInterface(file.absolutePath)
            when (exif.getAttributeInt(ExifInterface.TAG_ORIENTATION, ExifInterface.ORIENTATION_NORMAL)) {
                ExifInterface.ORIENTATION_ROTATE_90 -> 90f
                ExifInterface.ORIENTATION_ROTATE_180 -> 180f
                ExifInterface.ORIENTATION_ROTATE_270 -> 270f
                else -> 0f
            }
        } catch (_: Exception) {
            0f
        }
        if (rotationDegrees == 0f) return rawBitmap
        val matrix = Matrix().apply { postRotate(rotationDegrees) }
        val rotated = Bitmap.createBitmap(rawBitmap, 0, 0, rawBitmap.width, rawBitmap.height, matrix, true)
        if (rotated !== rawBitmap) rawBitmap.recycle()
        return rotated
    }
}
