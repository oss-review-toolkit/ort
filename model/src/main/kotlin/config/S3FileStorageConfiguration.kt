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

package org.ossreviewtoolkit.model.config

/**
 * A class to hold the configuration for using an AWS S3 bucket as a storage.
 */
data class S3FileStorageConfiguration(
    /** The AWS access key */
    val accessKeyId: String? = null,

    /** The AWS region to be used. */
    val awsRegion: String? = null,

    /** The name of the S3 bucket used to store files in. */
    val bucketName: String,

    /** Whether to use compression for storing files or not. Defaults to false. */
    val compression: Boolean = false,

    /** Custom endpoint to perform AWS API Requests */
    val customEndpoint: String? = null,

    /** Whether to enable path style access or not. Required for many non-AWS S3 providers. Defaults to false. */
    val pathStyleAccess: Boolean = false,

    /** The AWS secret for the access key. */
    val secretAccessKey: String? = null
)
