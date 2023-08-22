/*
 * Copyright (C) 2019 The ORT Project Authors (see <https://github.com/oss-review-toolkit/ort/blob/main/NOTICE>)
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

import aws.sdk.kotlin.runtime.auth.credentials.StaticCredentialsProvider
import aws.sdk.kotlin.services.s3.S3Client
import aws.sdk.kotlin.services.s3.model.GetObjectRequest
import aws.sdk.kotlin.services.s3.model.HeadObjectRequest
import aws.sdk.kotlin.services.s3.model.NoSuchKey
import aws.sdk.kotlin.services.s3.model.NotFound
import aws.sdk.kotlin.services.s3.model.PutObjectRequest
import aws.sdk.kotlin.services.s3.model.S3Exception
import aws.smithy.kotlin.runtime.auth.awscredentials.CachedCredentialsProvider
import aws.smithy.kotlin.runtime.auth.awscredentials.Credentials
import aws.smithy.kotlin.runtime.content.ByteStream
import aws.smithy.kotlin.runtime.content.toByteArray
import aws.smithy.kotlin.runtime.net.Url

import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream
import java.io.File
import java.io.InputStream

import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking

import org.apache.commons.compress.compressors.xz.XZCompressorInputStream
import org.apache.commons.compress.compressors.xz.XZCompressorOutputStream
import org.apache.logging.log4j.kotlin.Logging

/**
 * A [FileStorage] that stores files in an AWS S3 bucket [bucketName]. The [read] and [exists] operations are
 * blocking, but the [write] operation is asynchronous unless a [customEndpoint] is provided. Contents are compressed
 * before store if the [compression] flag is set to true.
 */
@Suppress("SwallowedException")
class S3FileStorage(
    /**
     * The name of the S3 bucket used to store files in.
     */
    private val bucketName: String,
    /**
     * The AWS region to be used.
     */
    private val awsRegion: String? = null,
    /**
     * The AWS access key.
     */
    private val accessKeyId: String? = null,
    /**
     * The AWS secret for the access key.
     */
    private val secretAccessKey: String? = null,
    /**
     * Non-public endpoint for testing.
     */
    private val customEndpoint: String? = null,
    /**
     * Whether to use compression for storing files or not. Defaults to true.
     */
    private val compression: Boolean = false
) : FileStorage {
    private companion object : Logging

    private val scope = CoroutineScope(Dispatchers.Default)

    val s3 by lazy {
        val provider = if (accessKeyId != null && secretAccessKey != null) {
            CachedCredentialsProvider(StaticCredentialsProvider(Credentials(accessKeyId, secretAccessKey)))
        } else {
            null
        }

        val client = if (awsRegion != null && provider != null) {
            S3Client {
                region = awsRegion
                credentialsProvider = provider
                endpointUrl = if (customEndpoint != null) Url.parse(customEndpoint) else null
            }
        } else {
            runBlocking {
                if (awsRegion != null) {
                    S3Client.fromEnvironment {
                        region = awsRegion
                    }
                } else {
                    S3Client.fromEnvironment()
                }
            }
        }

        client
    }

    override fun exists(path: String): Boolean {
        val request = HeadObjectRequest {
            key = path
            bucket = bucketName
        }

        var fileExists = true

        runBlocking {
            try {
                s3.headObject(request)
            } catch (e: NotFound) {
                fileExists = false
            }
        }

        return fileExists
    }

    override fun read(path: String): InputStream {
        val request = GetObjectRequest {
            key = path
            bucket = bucketName
        }

        val job = scope.async {
            try {
                s3.getObject(request) { resp ->
                    val stream = ByteArrayInputStream(resp.body?.toByteArray())
                    if (compression) XZCompressorInputStream(stream) else stream
                }
            } catch (e: NoSuchKey) {
                throw NoSuchFileException(File(path))
            }
        }

        val stream: InputStream

        runBlocking {
            stream = job.await()
        }

        return stream
    }

    override fun write(path: String, inputStream: InputStream) {
        val job = scope.launch {
            inputStream.use {
                PutObjectRequest {
                    key = path
                    bucket = bucketName
                    body = if (compression) {
                        val stream = ByteArrayOutputStream()
                        XZCompressorOutputStream(stream).write(it.readBytes())
                        ByteStream.fromBytes(stream.toByteArray())
                    } else {
                        ByteStream.fromBytes(it.readBytes())
                    }
                }
            }.let {
                try {
                    s3.putObject(it)
                } catch (e: S3Exception) {
                    logger.warn("Can not write $path to S3 bucket $bucketName")
                }
            }
        }

        // Local server doesn't play nice with auto scheduled coroutines. Run blocking instead
        if (customEndpoint != null) {
            runBlocking {
                job.join()
            }
        }
    }
}
