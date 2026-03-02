package com.example.llamaapp.worker

import android.content.Context
import androidx.hilt.work.HiltWorker
import androidx.work.*
import com.example.llamaapp.data.repository.ModelRepository
import com.example.llamaapp.storage.ModelStorageManager
import dagger.assisted.Assisted
import dagger.assisted.AssistedInject
import java.io.File
import java.io.FileOutputStream
import java.net.HttpURLConnection
import java.net.URL

@HiltWorker
class ModelDownloadWorker @AssistedInject constructor(
    @Assisted appContext: Context,
    @Assisted workerParams: WorkerParameters,
    private val storageManager: ModelStorageManager,
    private val modelRepository: ModelRepository
) : CoroutineWorker(appContext, workerParams) {

    companion object {
        const val KEY_URL = "download_url"
        const val KEY_FILENAME = "filename"
        const val KEY_PROGRESS = "download_progress"
        const val KEY_ERROR = "error_message"
    }

    override suspend fun doWork(): Result {
        val url = inputData.getString(KEY_URL) ?: return Result.failure(
            workDataOf(KEY_ERROR to "Missing download URL")
        )
        val filename = inputData.getString(KEY_FILENAME) ?: "model.gguf"
        val destFile = applicationContext.getExternalFilesDir("models")
            ?.resolve(filename)
            ?: return Result.failure(workDataOf(KEY_ERROR to "Storage unavailable"))

        return try {
            downloadWithResume(url, destFile)
            modelRepository.registerModel(destFile)
            Result.success()
        } catch (e: Exception) {
            Result.failure(workDataOf(KEY_ERROR to (e.message ?: "Download failed")))
        }
    }

    private suspend fun downloadWithResume(url: String, dest: File) {
        dest.parentFile?.mkdirs()
        val existingBytes = if (dest.exists()) dest.length() else 0L
        val connection = URL(url).openConnection() as HttpURLConnection
        try {
            if (existingBytes > 0) {
                connection.setRequestProperty("Range", "bytes=$existingBytes-")
            }
            connection.connectTimeout = 30_000
            connection.readTimeout = 60_000
            connection.connect()

            val contentLength = connection.contentLengthLong
            val totalSize = if (existingBytes > 0 && contentLength > 0) {
                existingBytes + contentLength
            } else {
                contentLength
            }

            var downloaded = existingBytes
            connection.inputStream.use { input ->
                FileOutputStream(dest, existingBytes > 0).use { output ->
                    val buf = ByteArray(8 * 1024 * 1024) // 8 MB buffer
                    var n: Int
                    while (input.read(buf).also { n = it } != -1) {
                        output.write(buf, 0, n)
                        downloaded += n
                        val progress = if (totalSize > 0) {
                            (downloaded * 100 / totalSize).toInt()
                        } else 0
                        setProgress(workDataOf(KEY_PROGRESS to progress))
                    }
                }
            }
        } finally {
            connection.disconnect()
        }
    }
}
