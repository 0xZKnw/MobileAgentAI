package com.example.llamaapp.storage

import android.content.Context
import android.net.Uri
import android.provider.OpenableColumns
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class ModelStorageManager @Inject constructor(
    @ApplicationContext private val context: Context
) {
    private val modelsDir: File
        get() = context.getExternalFilesDir("models") ?: context.filesDir.resolve("models")

    suspend fun importModel(uri: Uri, onProgress: (Float) -> Unit = {}): File =
        withContext(Dispatchers.IO) {
            val fileName = resolveFileName(uri)
            val destFile = modelsDir.also { it.mkdirs() }.resolve(fileName)
            val totalSize = context.contentResolver.query(uri, null, null, null, null)?.use { cursor ->
                cursor.moveToFirst()
                cursor.getLong(cursor.getColumnIndexOrThrow(OpenableColumns.SIZE))
            } ?: -1L
            var copied = 0L
            context.contentResolver.openInputStream(uri)!!.use { input ->
                destFile.outputStream().use { output ->
                    val buf = ByteArray(8 * 1024 * 1024) // 8 MB buffer
                    var n: Int
                    while (input.read(buf).also { n = it } != -1) {
                        output.write(buf, 0, n)
                        copied += n
                        if (totalSize > 0) onProgress(copied.toFloat() / totalSize)
                    }
                }
            }
            destFile
        }

    fun listModels(): List<File> =
        modelsDir.listFiles { f -> f.extension.lowercase() == "gguf" }?.toList() ?: emptyList()

    fun deleteModel(file: File) { file.delete() }

    fun getModelSize(file: File): Long = file.length()

    private fun resolveFileName(uri: Uri): String {
        var name = "model.gguf"
        context.contentResolver.query(uri, null, null, null, null)?.use { cursor ->
            if (cursor.moveToFirst()) {
                val idx = cursor.getColumnIndex(OpenableColumns.DISPLAY_NAME)
                if (idx >= 0) name = cursor.getString(idx)
            }
        }
        return name.ifEmpty { "model_${System.currentTimeMillis()}.gguf" }
    }
}
