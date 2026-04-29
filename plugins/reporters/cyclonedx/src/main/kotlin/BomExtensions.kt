/*
 * Copyright (C) 2019 The ORT Project Copyright Holders <https://github.com/oss-review-toolkit/ort/blob/main/NOTICE>
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

package org.ossreviewtoolkit.plugins.reporters.cyclonedx

import java.io.File

import org.cyclonedx.Format
import org.cyclonedx.Version
import org.cyclonedx.generators.BomGeneratorFactory
import org.cyclonedx.model.Bom

/**
 * Return the string representation for this [Bom], [schemaVersion] and [format].
 */
internal fun Bom.createFormat(schemaVersion: Version, format: Format): String =
    when (format) {
        Format.XML -> BomGeneratorFactory.createXml(schemaVersion, this).toXmlString()
        Format.JSON -> BomGeneratorFactory.createJson(schemaVersion, this).toJsonString()
    }

/**
 * Write this [Bom] at the given [schemaVersion] in all [outputFormats] to the [outputDir] with [outputName] as
 * prefixes.
 */
internal fun Bom.writeFormats(
    schemaVersion: Version,
    outputDir: File,
    outputName: String,
    outputFormats: Set<Format>
): List<Result<File>> =
    outputFormats.map { format ->
        runCatching {
            val bomString = createFormat(schemaVersion, format)

            outputDir.resolve("$outputName.${format.extension}").apply {
                bufferedWriter().use { it.write(bomString) }
            }
        }
    }
