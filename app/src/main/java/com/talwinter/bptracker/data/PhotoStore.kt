package com.talwinter.bptracker.data

import android.content.Context
import android.net.Uri
import androidx.core.content.FileProvider
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File

/**
 * Owns the photos attached to readings.
 *
 * Neither source URI can be stored directly and expected to survive:
 *
 *  - The camera writes into cacheDir, which Android empties whenever it wants space. The
 *    row would keep a path to a file that no longer exists.
 *  - Gallery URIs from PickVisualMedia are grant-scoped to the picker session and are not
 *    persistable (takePersistableUriPermission only applies to ACTION_OPEN_DOCUMENT). They
 *    stop resolving after process death.
 *
 * Either way the promise that an extracted value "stays checkable against the photo" would
 * quietly become false weeks later, which is exactly when you'd want to check it. So the
 * bytes get copied into app-private storage and that copy is what the database records.
 */
class PhotoStore(private val context: Context) {

    private val dir: File
        get() = File(context.filesDir, "reading_photos").apply { mkdirs() }

    /** Copies [source] into private storage and returns the durable URI. */
    suspend fun persist(source: Uri): Uri? = withContext(Dispatchers.IO) {
        runCatching {
            val target = File(dir, "reading_${System.currentTimeMillis()}.jpg")
            context.contentResolver.openInputStream(source)?.use { input ->
                target.outputStream().use { output -> input.copyTo(output) }
            } ?: return@runCatching null
            Uri.fromFile(target)
        }.getOrNull()
    }

    /** Called when a reading is deleted, so photos don't accumulate forever. */
    suspend fun delete(uri: String?) = withContext(Dispatchers.IO) {
        if (uri == null) return@withContext
        runCatching {
            val file = Uri.parse(uri).path?.let(::File) ?: return@runCatching
            if (file.exists() && file.parentFile?.name == "reading_photos") file.delete()
        }
    }

    /** Temp destination for the camera intent. Copied into private storage once accepted. */
    fun newCameraTarget(): Pair<File, Uri> {
        val cacheDir = File(context.cacheDir, "camera_temp").apply { mkdirs() }
        val file = File(cacheDir, "capture_${System.currentTimeMillis()}.jpg")
        val uri = FileProvider.getUriForFile(context, "${context.packageName}.fileprovider", file)
        return file to uri
    }
}
