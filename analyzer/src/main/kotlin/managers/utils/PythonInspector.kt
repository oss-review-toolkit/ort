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

import com.vdurmont.semver4j.Requirement

import java.io.File

import org.ossreviewtoolkit.utils.common.CommandLineTool
import org.ossreviewtoolkit.utils.common.ProcessCapture

internal object PythonInspector : CommandLineTool {
    override fun command(workingDir: File?) = "python-inspector"

    override fun transformVersion(output: String) = output.removePrefix("Python-inspector version: ")

    override fun getVersionRequirement(): Requirement = Requirement.buildIvy("[0.7.1,)")

    fun run(
        workingDir: File,
        outputFile: String,
        definitionFile: File,
        pythonVersion: String = "38",
    ): ProcessCapture {
        val commandLineOptions = buildList {
            add("--python-version")
            add(pythonVersion)

            add("--json-pdt")
            add(outputFile)

            add("--analyze-setup-py-insecurely")

            if (definitionFile.name == "setup.py") {
                add("--setup-py")
            } else {
                add("--requirement")
            }

            add(definitionFile.absolutePath)
        }

        return run(workingDir, *commandLineOptions.toTypedArray())
    }
}
