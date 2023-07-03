package org.ossreviewtoolkit.clients.dos

import okhttp3.MediaType.Companion.toMediaType
import okhttp3.RequestBody.Companion.toRequestBody
import org.ossreviewtoolkit.clients.dos.DOSService.Companion.logger
import java.io.File

class DOSRepository(private val dosService: DOSService) {
    suspend fun uploadFile(presignedUrl: String, filePath: String) {
        val file = File(filePath)
        val requestBody = file.readBytes().toRequestBody("application/zip".toMediaType())
        val response = dosService.putS3File(presignedUrl, requestBody)

        if (!response.isSuccessful) {
            DOSService.logger.error { "DOS / failed to upload packet to S3: ${response.message()}" }
        } else {
            DOSService.logger.info { "DOS / packet successfully uploaded to S3!" }
        }
    }

}
