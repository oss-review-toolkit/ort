/*
 * Copyright (C) 2017 The ORT Project Authors (see <https://github.com/oss-review-toolkit/ort/blob/main/NOTICE>)
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

package org.ossreviewtoolkit.utils.test

import com.fasterxml.jackson.module.kotlin.readValue

import io.kotest.core.TestConfiguration

import org.ossreviewtoolkit.model.FileFormat
import org.ossreviewtoolkit.model.OrtResult
import org.ossreviewtoolkit.model.config.FileArchiverConfiguration
import org.ossreviewtoolkit.model.config.LicenseFilePatterns
import org.ossreviewtoolkit.model.utils.FileArchiver
import org.ossreviewtoolkit.model.utils.FileProvenanceFileStorage
import org.ossreviewtoolkit.utils.ort.storage.LocalFileStorage

fun FileArchiver.Companion.createDefault() =
    FileArchiver(
        patterns = LicenseFilePatterns.DEFAULT.allLicenseFilenames.map { "**/$it" },
        storage = FileProvenanceFileStorage(
            LocalFileStorage(DEFAULT_ARCHIVE_DIR),
            FileArchiverConfiguration.ARCHIVE_FILENAME
        )
    )

fun TestConfiguration.getResource(name: String) = checkNotNull(javaClass.getResource(name))

fun TestConfiguration.readResource(name: String) = getResource(name).readText()

fun TestConfiguration.readOrtResult(name: String) =
    FileFormat.forExtension(name.substringAfterLast('.')).mapper
        .readValue<OrtResult>(patchExpectedResult(readResource(name)))

inline fun <reified T : Any> TestConfiguration.readResourceValue(name: String): T =
    FileFormat.forExtension(name.substringAfterLast('.')).mapper.readValue<T>(getResource(name))
