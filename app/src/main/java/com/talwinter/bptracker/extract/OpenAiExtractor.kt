package com.talwinter.bptracker.extract

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Matrix
import android.net.Uri
import android.util.Base64
import androidx.exifinterface.media.ExifInterface
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import org.json.JSONArray
import org.json.JSONObject
import java.io.ByteArrayOutputStream
import java.text.SimpleDateFormat
import java.util.Locale
import java.util.concurrent.TimeUnit

/**
 * Vision extraction via the OpenAI Responses API.
 *
 * Everything here is optional. If it throws, is offline, or has no key, the user simply
 * types the numbers instead — the app is fully functional without it.
 */
class OpenAiExtractor(private val context: Context) {

    private val client = OkHttpClient.Builder()
        .connectTimeout(15, TimeUnit.SECONDS)
        .readTimeout(60, TimeUnit.SECONDS)
        .build()

    private val json = Json { ignoreUnknownKeys = true; coerceInputValues = true }

    sealed interface Outcome {
        data class Success(val result: ExtractionResult, val problems: List<ReviewProblem>) : Outcome
        /** Recoverable: the user types the numbers by hand. Never a dead end. */
        data class Failed(val message: String) : Outcome
    }

    suspend fun extract(imageUri: Uri, apiKey: String, model: String): Outcome = withContext(Dispatchers.IO) {
        try {
            val base64 = downscaleToBase64(imageUri)
                ?: return@withContext Outcome.Failed("Couldn't read that image file.")

            val payload = buildRequest(base64, model)
            val request = Request.Builder()
                .url("https://api.openai.com/v1/responses")
                .addHeader("Authorization", "Bearer $apiKey")
                .post(payload.toRequestBody("application/json".toMediaType()))
                .build()

            client.newCall(request).execute().use { response ->
                val body = response.body?.string().orEmpty()
                if (!response.isSuccessful) {
                    return@withContext Outcome.Failed(
                        when (response.code) {
                            401 -> "That API key was rejected. Check it in Settings."
                            429 -> "Rate limited by OpenAI. Wait a moment, or just type the numbers."
                            in 500..599 -> "OpenAI is having trouble right now. Type the numbers instead."
                            else -> "Extraction failed (HTTP ${response.code})."
                        }
                    )
                }
                val text = extractOutputText(body)
                    ?: return@withContext Outcome.Failed("Unexpected response shape from OpenAI.")

                val result = json.decodeFromString(ExtractionResult.serializer(), text)
                Outcome.Success(result, ExtractionReview.review(result))
            }
        } catch (e: java.io.IOException) {
            Outcome.Failed("No connection. Type the numbers in and they'll save normally.")
        } catch (e: Exception) {
            Outcome.Failed("Couldn't read that photo automatically — type the numbers instead.")
        }
    }

    private fun buildRequest(base64Image: String, model: String): String {
        val content = JSONArray()
            .put(JSONObject().put("type", "input_text").put("text", ExtractionContract.SYSTEM_PROMPT))
            .put(
                JSONObject()
                    .put("type", "input_image")
                    .put("image_url", "data:image/jpeg;base64,$base64Image")
                    .put("detail", "high")
            )

        val format = JSONObject()
            .put("type", "json_schema")
            .put("name", "blood_pressure_extraction")
            .put("strict", true)
            .put("schema", JSONObject(ExtractionContract.JSON_SCHEMA))

        return JSONObject()
            .put("model", model)
            .put("input", JSONArray().put(JSONObject().put("role", "user").put("content", content)))
            .put("text", JSONObject().put("format", format))
            .toString()
    }

    /** Responses API returns output_text either at the top level or nested in output[].content[]. */
    private fun extractOutputText(body: String): String? {
        val root = json.parseToJsonElement(body).jsonObject
        (root["output_text"] as? kotlinx.serialization.json.JsonPrimitive)?.contentOrNull?.let { return it }
        val output = root["output"]?.jsonArray ?: return null
        for (item in output) {
            val contents = (item as? JsonObject)?.get("content")?.jsonArray ?: continue
            for (c in contents) {
                val obj = c.jsonObject
                if (obj["type"]?.jsonPrimitive?.content == "output_text") {
                    return obj["text"]?.jsonPrimitive?.content
                }
            }
        }
        return null
    }

    /**
     * Downscale before upload. A 12 MP phone photo is far more detail than a two-inch LCD
     * needs, and it would cost tokens and seconds for nothing. 1600px on the long edge
     * keeps seven-segment digits crisp.
     */
    private fun downscaleToBase64(uri: Uri, maxEdge: Int = 1600): String? {
        val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
        context.contentResolver.openInputStream(uri)?.use { BitmapFactory.decodeStream(it, null, bounds) }
        if (bounds.outWidth <= 0) return null

        var sample = 1
        while (maxOf(bounds.outWidth, bounds.outHeight) / sample > maxEdge * 2) sample *= 2

        val opts = BitmapFactory.Options().apply { inSampleSize = sample }
        var bitmap = context.contentResolver.openInputStream(uri)?.use {
            BitmapFactory.decodeStream(it, null, opts)
        } ?: return null

        bitmap = rotateToUpright(uri, bitmap)

        val scale = maxEdge.toFloat() / maxOf(bitmap.width, bitmap.height)
        if (scale < 1f) {
            bitmap = Bitmap.createScaledBitmap(
                bitmap, (bitmap.width * scale).toInt(), (bitmap.height * scale).toInt(), true
            )
        }

        val out = ByteArrayOutputStream()
        bitmap.compress(Bitmap.CompressFormat.JPEG, 90, out)
        return Base64.encodeToString(out.toByteArray(), Base64.NO_WRAP)
    }

    /** A sideways photo measurably hurts digit recognition, so honour the EXIF orientation. */
    private fun rotateToUpright(uri: Uri, bitmap: Bitmap): Bitmap {
        val orientation = context.contentResolver.openInputStream(uri)?.use {
            ExifInterface(it).getAttributeInt(ExifInterface.TAG_ORIENTATION, ExifInterface.ORIENTATION_NORMAL)
        } ?: return bitmap

        val degrees = when (orientation) {
            ExifInterface.ORIENTATION_ROTATE_90 -> 90f
            ExifInterface.ORIENTATION_ROTATE_180 -> 180f
            ExifInterface.ORIENTATION_ROTATE_270 -> 270f
            else -> return bitmap
        }
        val matrix = Matrix().apply { postRotate(degrees) }
        return Bitmap.createBitmap(bitmap, 0, 0, bitmap.width, bitmap.height, matrix, true)
    }

    companion object {
        /**
         * When the photo came from the gallery, its EXIF capture time is the real time of
         * the reading. Defaulting to "now" would collapse an imported back-catalogue onto
         * today and turn the trend line into fiction.
         */
        fun exifTimestamp(context: Context, uri: Uri): Long? = runCatching {
            context.contentResolver.openInputStream(uri)?.use { stream ->
                val exif = ExifInterface(stream)
                val raw = exif.getAttribute(ExifInterface.TAG_DATETIME_ORIGINAL)
                    ?: exif.getAttribute(ExifInterface.TAG_DATETIME)
                    ?: return@use null
                SimpleDateFormat("yyyy:MM:dd HH:mm:ss", Locale.US).parse(raw)?.time
            }
        }.getOrNull()
    }
}

private val kotlinx.serialization.json.JsonPrimitive.contentOrNull: String?
    get() = if (this is kotlinx.serialization.json.JsonNull) null else content
