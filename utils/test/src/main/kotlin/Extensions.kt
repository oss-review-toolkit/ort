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

import io.kotest.matchers.nulls.shouldNotBeNull

import org.ossreviewtoolkit.model.config.FileArchiverConfiguration
import org.ossreviewtoolkit.model.config.LicenseFilePatterns
import org.ossreviewtoolkit.model.utils.FileArchiver
import org.ossreviewtoolkit.model.utils.FileProvenanceFileStorage
import org.ossreviewtoolkit.utils.ort.storage.LocalFileStorage

infix fun <T : Any> T?.shouldNotBeNull(block: T.() -> Unit) {
    this.shouldNotBeNull()
    block()
}

fun FileArchiver.Companion.createDefault() =
    FileArchiver(
        patterns = LicenseFilePatterns.DEFAULT.allLicenseFilenames.map { "**/$it" },
        storage = FileProvenanceFileStorage(
            LocalFileStorage(DEFAULT_ARCHIVE_DIR),
            FileArchiverConfiguration.ARCHIVE_FILENAME
        )
    )
