package org.ossreviewtoolkit.clients.dos

import okhttp3.MediaType.Companion.toMediaType
import okhttp3.RequestBody.Companion.toRequestBody
import org.ossreviewtoolkit.clients.dos.DOSService.Companion.logger
import java.io.File
import java.io.IOException
import java.nio.file.Files
import java.nio.file.Paths

class DOSRepository(private val dosService: DOSService) {
    /**
     * Upload a file to S3, using presigned URL, and if successfull,
     * delete the file from local storage.
     */
    suspend fun uploadFile(presignedUrl: String, filePath: String) {
        val file = File(filePath)
        val requestBody = file.readBytes().toRequestBody("application/zip".toMediaType())
        val response = dosService.putS3File(presignedUrl, requestBody)

        if (!response.isSuccessful) {
            logger.error { "DOS / failed to upload packet to S3: ${response.message()}" }
        } else {
            logger.info { "DOS / packet successfully uploaded to S3!" }

            // Delete the zipped file from local storage
            val path = Paths.get(filePath)
            try {
                val result = Files.deleteIfExists(path)
                if (result) {
                    logger.info { "DOS / packet deleted from local storage" }
                } else {
                    logger.info { "DOS / failed to delete the packet from local storage" }
                }
            } catch (e: IOException) {
                logger.info { "DOS / failed to delete the packet from local storage" }
                e.printStackTrace()
            }
        }
    }

}
