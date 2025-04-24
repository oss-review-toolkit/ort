/*
 * Copyright (C) 2023 The ORT Project Authors (see <https://github.com/oss-review-toolkit/ort/blob/main/NOTICE>)
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     https://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 *
 * SPDX-License-Identifier: Apache-2.0
 * License-Filename: LICENSE
 */

package org.ossreviewtoolkit.utils.ort.storage

import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream
import java.io.File
import java.io.InputStream
import java.net.URI

import org.apache.commons.compress.compressors.xz.XZCompressorInputStream
import org.apache.commons.compress.compressors.xz.XZCompressorOutputStream
import org.apache.logging.log4j.kotlin.logger

import org.ossreviewtoolkit.utils.common.collectMessages

import software.amazon.awssdk.auth.credentials.AwsBasicCredentials
import software.amazon.awssdk.auth.credentials.StaticCredentialsProvider
import software.amazon.awssdk.core.sync.RequestBody
import software.amazon.awssdk.regions.Region
import software.amazon.awssdk.services.s3.S3Client
import software.amazon.awssdk.services.s3.model.DeleteObjectRequest
import software.amazon.awssdk.services.s3.model.GetObjectRequest
import software.amazon.awssdk.services.s3.model.HeadObjectRequest
import software.amazon.awssdk.services.s3.model.NoSuchKeyException
import software.amazon.awssdk.services.s3.model.PutObjectRequest
import software.amazon.awssdk.services.s3.model.S3Exception

/**
 * A [FileStorage] that stores files in an AWS S3 bucket [bucketName]. The [read] and [exists] operations are
 * blocking, but the [write] operation is asynchronous unless a [customEndpoint] is provided. Contents are compressed
 * before store if the [compression] flag is set to true.
 */
class S3FileStorage(
    /** The AWS access key */
    private val accessKeyId: String? = null,

    /** The AWS region to be used. */
    private val awsRegion: String? = null,

    /** The name of the S3 bucket used to store files in. */
    private val bucketName: String,

    /** Whether to use compression for storing files or not. Defaults to false. */
    private val compression: Boolean = false,

    /** Custom endpoint to perform AWS API Requests */
    private val customEndpoint: String? = null,

    /** Whether to enable path style access or not. Required for many non-AWS S3 providers. Defaults to false. */
    private val pathStyleAccess: Boolean = false,

    /** The AWS secret for the access key. */
    private val secretAccessKey: String? = null
) : FileStorage {
    private val s3Client: S3Client by lazy {
        S3Client.builder().apply {
            if (awsRegion != null) {
                region(Region.of(awsRegion))
            }

            if (accessKeyId != null && secretAccessKey != null) {
                credentialsProvider(
                    StaticCredentialsProvider.create(
                        AwsBasicCredentials.create(
                            accessKeyId,
                            secretAccessKey
                        )
                    )
                )
            }

            if (customEndpoint != null) {
                endpointOverride(URI(customEndpoint))
                serviceConfiguration {
                    it.pathStyleAccessEnabled(pathStyleAccess)
                }
            }
        }.build()
    }

    override fun exists(path: String): Boolean {
        val request = HeadObjectRequest.builder()
            .key(path)
            .bucket(bucketName)
            .build()

        return runCatching { s3Client.headObject(request) }.onFailure { exception ->
            if (exception !is NoSuchKeyException) {
                logger.warn { "Unable to read '$path' from S3 bucket '$bucketName': ${exception.collectMessages()}" }
            }
        }.isSuccess
    }

    override fun read(path: String): InputStream {
        val request = GetObjectRequest.builder()
            .key(path)
            .bucket(bucketName)
            .build()

        return runCatching {
            val response = s3Client.getObjectAsBytes(request)
            val stream = ByteArrayInputStream(response.asByteArray())
            if (compression) XZCompressorInputStream(stream) else stream
        }.onFailure { exception ->
            if (exception is NoSuchKeyException) throw NoSuchFileException(File(path))
        }.getOrThrow()
    }

    override fun write(path: String, inputStream: InputStream) {
        val request = PutObjectRequest.builder()
            .key(path)
            .bucket(bucketName)
            .build()

        val body = inputStream.use {
            if (compression) {
                val stream = ByteArrayOutputStream()
                XZCompressorOutputStream(stream).write(it.readBytes())
                RequestBody.fromBytes(stream.toByteArray())
            } else {
                RequestBody.fromBytes(it.readBytes())
            }
        }

        runCatching {
            s3Client.putObject(request, body)
        }.onFailure { exception ->
            if (exception is S3Exception) {
                logger.warn { "Can not write '$path' to S3 bucket '$bucketName': ${exception.collectMessages()}" }
            }
        }
    }

    override fun delete(path: String): Boolean {
        val request = DeleteObjectRequest.builder()
            .key(path)
            .bucket(bucketName)
            .build()

        val response = s3Client.deleteObject(request)
        return response.sdkHttpResponse().isSuccessful
    }
}
