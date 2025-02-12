/*
 * Copyright (C) 2025 The ORT Project Authors (see <https://github.com/oss-review-toolkit/ort/blob/main/NOTICE>)
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

package org.ossreviewtoolkit.plugins.packagemanagers.maven

import io.kotest.matchers.shouldBe

import java.io.File

import org.ossreviewtoolkit.plugins.packagemanagers.maven.utils.EXTENSIONS_PATH

/** The content of a Tycho extension file. */
private const val TYCHO_EXTENSION_XML = """
<extensions>
  <extension>
    <groupId>org.eclipse.tycho</groupId>
    <artifactId>tycho-build</artifactId>
    <version>4.0.0</version>
  </extension>
</extensions>
"""

/**
 * Add the required structures to this [File] to make it a Tycho project. Write the given [content] in the extensions
 * file.
 */
internal fun File.addTychoExtension(content: String = TYCHO_EXTENSION_XML) =
    with(resolve(EXTENSIONS_PATH)) {
        parentFile.mkdirs() shouldBe true
        writeText(content)
    }
