/*
 * Copyright (C) 2022 The ORT Project Authors (see <https://github.com/oss-review-toolkit/ort/blob/main/NOTICE>)
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

package org.ossreviewtoolkit.analyzer.managers.utils

import io.kotest.core.spec.style.StringSpec
import io.kotest.matchers.collections.haveSize
import io.kotest.matchers.should

import java.io.File

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.decodeFromStream

import org.ossreviewtoolkit.utils.common.safeDeleteRecursively
import org.ossreviewtoolkit.utils.test.createSpecTempDir

class PythonInspectorFunTest : StringSpec({
    val projectsDir = File("src/funTest/assets/projects").absoluteFile
    val json = Json { ignoreUnknownKeys = true }

    "python-inspector output can be deserialized" {
        val definitionFile = projectsDir.resolve("synthetic/pip/requirements.txt")
        val workingDir = definitionFile.parentFile
        val outputFile = createSpecTempDir().resolve("python-inspector.json")

        try {
            PythonInspector.run(
                workingDir = workingDir,
                outputFile = outputFile.absolutePath,
                definitionFile = definitionFile,
                pythonVersion = "27"
            )
        } finally {
            workingDir.resolve(".cache").safeDeleteRecursively(force = true)
        }

        val result = withContext(Dispatchers.IO) {
            outputFile.inputStream().use {
                json.decodeFromStream<PythonInspectorResult>(it)
            }
        }

        result.resolvedDependencies should haveSize(1)
        result.packages should haveSize(10)
    }
})
