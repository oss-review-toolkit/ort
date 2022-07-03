/*
 * Copyright (C) 2017-2019 HERE Europe B.V.
 * Copyright (C) 2020 Bosch.IO GmbH
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

import com.fasterxml.jackson.annotation.JsonSubTypes
import com.fasterxml.jackson.annotation.JsonSubTypes.Type
import com.fasterxml.jackson.annotation.JsonTypeInfo

import org.ossreviewtoolkit.model.Package
import org.ossreviewtoolkit.model.Provenance
import org.ossreviewtoolkit.utils.ort.storage.FileStorage

/**
 * Root of a class hierarchy for configuration classes for scan storage implementations.
 *
 * Based on this hierarchy, it is possible to have multiple different scan storages enabled and to configure them
 * dynamically.
 */
@JsonTypeInfo(use = JsonTypeInfo.Id.MINIMAL_CLASS)
@JsonSubTypes(
    Type(ClearlyDefinedStorageConfiguration::class),
    Type(FileBasedStorageConfiguration::class),
    Type(PostgresStorageConfiguration::class),
    Type(Sw360StorageConfiguration::class)
)
sealed interface ScanStorageConfiguration

/**
 * The configuration model of a storage based on ClearlyDefined.
 */
data class ClearlyDefinedStorageConfiguration(
    /**
     * The URL of the ClearlyDefined server.
     */
    val serverUrl: String
) : ScanStorageConfiguration

/**
 * The configuration model of a file based storage.
 */
data class FileBasedStorageConfiguration(
    /**
     * The configuration of the [FileStorage] used to store the files.
     */
    val backend: FileStorageConfiguration,

    /**
     * The way that scan results are stored, defaults to [StorageType.PACKAGE_BASED].
     */
    val type: StorageType = StorageType.PACKAGE_BASED
) : ScanStorageConfiguration

/**
 * A class to hold the configuration for using Postgres as a storage.
 */
data class PostgresStorageConfiguration(
    /**
     * The configuration of the PostgreSQL database.
     */
    val connection: PostgresConnection,

    /**
     * The way that scan results are stored, defaults to [StorageType.PACKAGE_BASED].
     */
    val type: StorageType = StorageType.PACKAGE_BASED
) : ScanStorageConfiguration

/**
 * A class to hold the configuration for SW360.
 */
data class Sw360StorageConfiguration(
    /**
     * The configuration for SW360.
     */
    val connection: Sw360Connection
) : ScanStorageConfiguration

/**
 * An enum to describe different types of storages.
 */
enum class StorageType {
    /**
     * A storage that stores scan results by [Package].
     */
    PACKAGE_BASED,

    /**
     * A storage that stores scan results by [Provenance].
     */
    PROVENANCE_BASED
}
