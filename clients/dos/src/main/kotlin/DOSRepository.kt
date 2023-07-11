package org.ossreviewtoolkit.clients.dos

import java.io.File

import okhttp3.MediaType.Companion.toMediaType
import okhttp3.RequestBody.Companion.toRequestBody
import org.ossreviewtoolkit.clients.dos.DOSService.Companion.logger

class DOSRepository(private val dosService: DOSService) {
    /**
     * Upload a file to S3, using presigned URL, and if successful,
     * delete the file from local storage.
     */
    suspend fun uploadFile(presignedUrl: String, filePath: String): Boolean {
        val file = File(filePath)
        val requestBody = file.readBytes().toRequestBody("application/zip".toMediaType())
        val response = dosService.putS3File(presignedUrl, requestBody)

        if (!response.isSuccessful) {
            logger.error { "Failed to upload packet to S3: ${response.message()}" }
            return false
        } else {
            logger.info { "Packet successfully uploaded to S3!" }
            return true
        }
    }

    /**
     * Request earlier scan results from DOS API, using Package URL for
     * identifying the package.
     */
    suspend fun getScanResults(purl: String?): DOSService.ScanResultsResponseBody {
        val requestBody = DOSService.ScanResultsRequestBody(purl)
        val response = dosService.getScanResults(requestBody)

        logger.info { "Scan results from API: $response" }
        return response
    }

    /**
     * Send info to API about a new zipped package awaiting in S3 to scan.
     * Response: (unzipped) folder name at S3.
     */
    suspend fun getScanFolder(zipFile: String?): String? {
        val requestBody = DOSService.PackageRequestBody(zipFile)
        val response = dosService.getScanFolder(requestBody).folderName

        logger.info { "S3 folder to scan: $response" }
        return response
    }

}
