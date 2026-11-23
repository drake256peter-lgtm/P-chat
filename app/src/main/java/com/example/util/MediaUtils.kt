package com.example.util

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Canvas
import android.graphics.ColorMatrix
import android.graphics.ColorMatrixColorFilter
import android.graphics.Paint
import android.media.MediaPlayer
import android.media.MediaRecorder
import android.net.Uri
import android.os.Build
import android.util.Base64
import android.util.Log
import java.io.ByteArrayOutputStream
import java.io.File
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

import android.content.ContentValues
import android.content.Intent
import android.os.Environment
import android.provider.MediaStore
import androidx.core.content.FileProvider
import java.io.FileInputStream
import java.io.FileOutputStream

private const val TAG = "MediaUtils"

/**
 * Optical color-grading presets only — zero cartoon overlays or simulated graphics.
 */
enum class SnapFilter(val id: String, val label: String, val badge: String) {
    NONE("none", "Normal", "📷"),
    BEAUTY_GLOW("beauty_glow", "Studio", "✨"),
    GOLDEN_HOUR("golden_hour", "Warm", "☀️"),
    NEON_NIGHT("neon_night", "Purple", "💜"),
    MONO_FILM("mono_film", "Noir", "🎞️");

    companion object {
        fun fromId(id: String?): SnapFilter = when (id) {
            "beauty_glow" -> BEAUTY_GLOW
            "golden_hour" -> GOLDEN_HOUR
            "neon_night", "cyber_visor" -> NEON_NIGHT
            "mono_film" -> MONO_FILM
            // Any legacy cartoon lens IDs safely map back to raw camera feed
            "dog_ears", "neon_crown", "cat_whiskers", "none", null, "" -> NONE
            else -> NONE
        }

        fun isLegacyCartoonFilterId(id: String?): Boolean =
            id in setOf("dog_ears", "neon_crown", "cat_whiskers", "cyber_visor")
    }
}

object MediaUtils {

    fun bitmapToBase64(bitmap: Bitmap, maxDim: Int = 640, quality: Int = 72): String {
        val scaled = scaleBitmap(bitmap, maxDim)
        val stream = ByteArrayOutputStream()
        scaled.compress(Bitmap.CompressFormat.JPEG, quality, stream)
        var bytes = stream.toByteArray()
        var currentQuality = quality
        while (bytes.size > 165_000 && currentQuality > 30) {
            stream.reset()
            currentQuality -= 12
            scaled.compress(Bitmap.CompressFormat.JPEG, currentQuality, stream)
            bytes = stream.toByteArray()
        }
        return Base64.encodeToString(bytes, Base64.NO_WRAP)
    }

    fun base64ToBitmap(base64: String): Bitmap? {
        if (base64.isBlank()) return null
        return try {
            val bytes = Base64.decode(base64, Base64.DEFAULT)
            BitmapFactory.decodeByteArray(bytes, 0, bytes.size)
        } catch (e: Exception) {
            Log.w(TAG, "Failed to decode base64 bitmap", e)
            null
        }
    }

    fun uriToBitmap(context: Context, uri: Uri, maxDim: Int = 960): Bitmap? {
        return try {
            context.contentResolver.openInputStream(uri)?.use { stream ->
                val raw = BitmapFactory.decodeStream(stream) ?: return null
                scaleBitmap(raw, maxDim)
            }
        } catch (e: Exception) {
            Log.w(TAG, "Failed to decode bitmap from URI: $uri", e)
            null
        }
    }

    fun scaleBitmap(source: Bitmap, maxDim: Int): Bitmap {
        val width = source.width
        val height = source.height
        if (width <= maxDim && height <= maxDim) return source
        val ratio = width.toFloat() / height.toFloat()
        val (newW, newH) = if (ratio >= 1f) {
            maxDim to (maxDim / ratio).toInt().coerceAtLeast(1)
        } else {
            (maxDim * ratio).toInt().coerceAtLeast(1) to maxDim
        }
        return Bitmap.createScaledBitmap(source, newW, newH, true)
    }

    /**
     * Returns a photographic ColorMatrix for subtle optical color grading.
     */
    fun colorMatrixForFilter(filter: SnapFilter): ColorMatrix? {
        return when (filter) {
            SnapFilter.BEAUTY_GLOW -> ColorMatrix(
                floatArrayOf(
                    1.05f, 0.02f, 0.0f, 0f, 6f,
                    0.0f, 1.03f, 0.01f, 0f, 5f,
                    0.02f, 0.01f, 1.05f, 0f, 8f,
                    0f, 0f, 0f, 1f, 0f
                )
            )
            SnapFilter.GOLDEN_HOUR -> ColorMatrix(
                floatArrayOf(
                    1.14f, 0.06f, 0.0f, 0f, 10f,
                    0.02f, 1.04f, 0.0f, 0f, 4f,
                    0.0f, 0.0f, 0.86f, 0f, -6f,
                    0f, 0f, 0f, 1f, 0f
                )
            )
            SnapFilter.NEON_NIGHT -> ColorMatrix(
                floatArrayOf(
                    1.04f, 0.0f, 0.12f, 0f, 4f,
                    0.0f, 0.96f, 0.04f, 0f, -2f,
                    0.10f, 0.04f, 1.18f, 0f, 12f,
                    0f, 0f, 0f, 1f, 0f
                )
            )
            SnapFilter.MONO_FILM -> ColorMatrix().apply {
                setSaturation(0f)
                val contrast = ColorMatrix(
                    floatArrayOf(
                        1.18f, 0f, 0f, 0f, -18f,
                        0f, 1.18f, 0f, 0f, -18f,
                        0f, 0f, 1.18f, 0f, -18f,
                        0f, 0f, 0f, 1f, 0f
                    )
                )
                postConcat(contrast)
            }
            SnapFilter.NONE -> null
        }
    }

    /**
     * Applies pure photographic color grading (zero cartoon stickers/drawings) to a captured photo.
     */
    fun applySnapFilterToBitmap(source: Bitmap, filter: SnapFilter): Bitmap {
        if (filter == SnapFilter.NONE) return source
        val matrix = colorMatrixForFilter(filter) ?: return source
        val result = Bitmap.createBitmap(source.width, source.height, Bitmap.Config.ARGB_8888)
        val canvas = Canvas(result)
        val basePaint = Paint(Paint.ANTI_ALIAS_FLAG or Paint.FILTER_BITMAP_FLAG).apply {
            colorFilter = ColorMatrixColorFilter(matrix)
        }
        canvas.drawBitmap(source, 0f, 0f, basePaint)
        return result
    }

    fun saveBitmapToGallery(context: Context, bitmap: Bitmap): Result<String> = runCatching {
        val filename = "PCHAT_${System.currentTimeMillis()}.jpg"
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            val values = ContentValues().apply {
                put(MediaStore.Images.Media.DISPLAY_NAME, filename)
                put(MediaStore.Images.Media.MIME_TYPE, "image/jpeg")
                put(MediaStore.Images.Media.RELATIVE_PATH, "${Environment.DIRECTORY_PICTURES}/P-CHAT")
                put(MediaStore.Images.Media.IS_PENDING, 1)
            }
            val resolver = context.contentResolver
            val uri = resolver.insert(MediaStore.Images.Media.EXTERNAL_CONTENT_URI, values)
                ?: error("Could not create MediaStore image entry")
            resolver.openOutputStream(uri)?.use { out ->
                bitmap.compress(Bitmap.CompressFormat.JPEG, 92, out)
            } ?: error("Could not open gallery output stream")
            values.clear()
            values.put(MediaStore.Images.Media.IS_PENDING, 0)
            resolver.update(uri, values, null, null)
            "Saved to Pictures/P-CHAT/$filename"
        } else {
            val dir = File(context.getExternalFilesDir(Environment.DIRECTORY_PICTURES), "P-CHAT").apply { mkdirs() }
            val outFile = File(dir, filename)
            FileOutputStream(outFile).use { out ->
                bitmap.compress(Bitmap.CompressFormat.JPEG, 92, out)
            }
            "Saved photo to ${outFile.name}"
        }
    }

    fun saveVideoToGallery(context: Context, videoFile: File): Result<String> = runCatching {
        require(videoFile.exists() && videoFile.length() > 0L) { "Recorded video file is missing." }
        val filename = "PCHAT_${System.currentTimeMillis()}.mp4"
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            val values = ContentValues().apply {
                put(MediaStore.Video.Media.DISPLAY_NAME, filename)
                put(MediaStore.Video.Media.MIME_TYPE, "video/mp4")
                put(MediaStore.Video.Media.RELATIVE_PATH, "${Environment.DIRECTORY_MOVIES}/P-CHAT")
                put(MediaStore.Video.Media.IS_PENDING, 1)
            }
            val resolver = context.contentResolver
            val uri = resolver.insert(MediaStore.Video.Media.EXTERNAL_CONTENT_URI, values)
                ?: error("Could not create MediaStore video entry")
            resolver.openOutputStream(uri)?.use { out ->
                FileInputStream(videoFile).use { input -> input.copyTo(out) }
            } ?: error("Could not open gallery output stream")
            values.clear()
            values.put(MediaStore.Video.Media.IS_PENDING, 0)
            resolver.update(uri, values, null, null)
            "Saved to Movies/P-CHAT/$filename"
        } else {
            val dir = File(context.getExternalFilesDir(Environment.DIRECTORY_MOVIES), "P-CHAT").apply { mkdirs() }
            val outFile = File(dir, filename)
            videoFile.copyTo(outFile, overwrite = true)
            "Saved video to ${outFile.name}"
        }
    }

    fun fileProviderAuthority(context: Context): String = "${context.packageName}.fileprovider"

    fun shareBitmapViaChooser(
        context: Context,
        bitmap: Bitmap,
        chooserTitle: String = "Share photo via",
        caption: String = ""
    ): Result<Unit> = runCatching {
        val shareDir = File(context.cacheDir, "shared_media").apply { mkdirs() }
        val shareFile = File(shareDir, "pchat_share_${System.currentTimeMillis()}.jpg")
        FileOutputStream(shareFile).use { out ->
            bitmap.compress(Bitmap.CompressFormat.JPEG, 92, out)
        }
        val uri = FileProvider.getUriForFile(context, fileProviderAuthority(context), shareFile)
        val intent = Intent(Intent.ACTION_SEND).apply {
            type = "image/jpeg"
            putExtra(Intent.EXTRA_STREAM, uri)
            if (caption.isNotBlank()) putExtra(Intent.EXTRA_TEXT, caption)
            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
        }
        val chooser = Intent.createChooser(intent, chooserTitle).apply {
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        }
        context.startActivity(chooser)
    }

    fun shareVideoViaChooser(
        context: Context,
        videoFile: File,
        chooserTitle: String = "Share video via",
        caption: String = ""
    ): Result<Unit> = runCatching {
        require(videoFile.exists() && videoFile.length() > 0L) { "Recorded video file is missing." }
        val targetFile = if (videoFile.parentFile == context.cacheDir || videoFile.parentFile == context.filesDir) {
            videoFile
        } else {
            val shareDir = File(context.cacheDir, "shared_media").apply { mkdirs() }
            File(shareDir, "pchat_video_${System.currentTimeMillis()}.mp4").also {
                videoFile.copyTo(it, overwrite = true)
            }
        }
        val uri = FileProvider.getUriForFile(context, fileProviderAuthority(context), targetFile)
        val intent = Intent(Intent.ACTION_SEND).apply {
            type = "video/mp4"
            putExtra(Intent.EXTRA_STREAM, uri)
            if (caption.isNotBlank()) putExtra(Intent.EXTRA_TEXT, caption)
            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
        }
        val chooser = Intent.createChooser(intent, chooserTitle).apply {
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        }
        context.startActivity(chooser)
    }

    fun shareTextViaChooser(
        context: Context,
        text: String,
        chooserTitle: String = "Share MALAK AI creation"
    ): Result<Unit> = runCatching {
        val intent = Intent(Intent.ACTION_SEND).apply {
            type = "text/plain"
            putExtra(Intent.EXTRA_TEXT, text)
        }
        val chooser = Intent.createChooser(intent, chooserTitle).apply {
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        }
        context.startActivity(chooser)
    }

    fun formatTimestamp(seconds: Long?): String {
        if (seconds == null || seconds <= 0L) return "Now"
        val date = Date(seconds * 1000L)
        val now = System.currentTimeMillis()
        val diff = now - date.time
        return when {
            diff < 60_000L -> "Just now"
            diff < 3_600_000L -> "${diff / 60_000L}m ago"
            diff < 86_400_000L -> SimpleDateFormat("h:mm a", Locale.getDefault()).format(date)
            else -> SimpleDateFormat("MMM d", Locale.getDefault()).format(date)
        }
    }
}

class VoiceNoteRecorder(private val context: Context) {
    private var recorder: MediaRecorder? = null
    private var outputFile: File? = null
    private var startMillis: Long = 0L

    fun startRecording(): Boolean {
        return try {
            stopAndDiscard()
            val file = File(context.cacheDir, "pchat_voice_${System.currentTimeMillis()}.m4a")
            val mediaRecorder = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                MediaRecorder(context)
            } else {
                @Suppress("DEPRECATION")
                MediaRecorder()
            }
            mediaRecorder.apply {
                setAudioSource(MediaRecorder.AudioSource.MIC)
                setOutputFormat(MediaRecorder.OutputFormat.MPEG_4)
                setAudioEncoder(MediaRecorder.AudioEncoder.AAC)
                setAudioEncodingBitRate(64000)
                setAudioSamplingRate(22050)
                setOutputFile(file.absolutePath)
                prepare()
                start()
            }
            recorder = mediaRecorder
            outputFile = file
            startMillis = System.currentTimeMillis()
            true
        } catch (e: Exception) {
            Log.e(TAG, "Failed to start voice note recording", e)
            stopAndDiscard()
            false
        }
    }

    fun stopRecording(): Pair<File, Int>? {
        return try {
            val file = outputFile
            val durationSec = ((System.currentTimeMillis() - startMillis) / 1000L).toInt().coerceAtLeast(1)
            recorder?.apply {
                stop()
                release()
            }
            recorder = null
            if (file != null && file.exists()) file to durationSec else null
        } catch (e: Exception) {
            Log.w(TAG, "Failed to stop recording cleanly", e)
            stopAndDiscard()
            null
        }
    }

    fun stopAndDiscard() {
        try {
            recorder?.release()
        } catch (_: Exception) {
        }
        recorder = null
        outputFile?.delete()
        outputFile = null
    }
}

class VoiceNotePlayer {
    private var player: MediaPlayer? = null

    fun playFile(path: String, onComplete: () -> Unit) {
        stop()
        try {
            val file = File(path)
            if (!file.exists()) {
                onComplete()
                return
            }
            player = MediaPlayer().apply {
                setDataSource(path)
                prepare()
                setOnCompletionListener {
                    stop()
                    onComplete()
                }
                start()
            }
        } catch (e: Exception) {
            Log.w(TAG, "Error playing voice file", e)
            stop()
            onComplete()
        }
    }

    fun stop() {
        try {
            player?.stop()
        } catch (_: Exception) {
        }
        try {
            player?.release()
        } catch (_: Exception) {
        }
        player = null
    }
}
