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

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

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

@Serializable
internal data class PythonInspectorResult(
    @SerialName("resolved_dependencies") val resolvedDependencies: List<PythonInspectorResolvedDependency>,
    val packages: List<PythonInspectorPackage>
)

@Serializable
internal data class PythonInspectorResolvedDependency(
    val key: String,
    @SerialName("package_name") val packageName: String,
    @SerialName("installed_version") val installedVersion: String,
    val dependencies: List<PythonInspectorResolvedDependency>
)

@Serializable
internal data class PythonInspectorPackage(
    val type: String,
    val namespace: String?,
    val name: String,
    val version: String,
    val description: String,
    val parties: List<PythonInspectorParty>,
    @SerialName("homepage_url") val homepageUrl: String,
    @SerialName("download_url") val downloadUrl: String,
    val size: Long,
    val sha1: String?,
    val md5: String?,
    val sha256: String?,
    val sha512: String?,
    @SerialName("code_view_url") val codeViewUrl: String?,
    @SerialName("vcs_url") val vcsUrl: String?,
    val copyright: String?,
    @SerialName("license_expression") val licenseExpression: String?,
    @SerialName("declared_license") val declaredLicense: String,
    @SerialName("source_packages") val sourcePackages: List<String>,
    @SerialName("repository_homepage_url") val repositoryHomepageUrl: String?,
    @SerialName("repository_download_url") val repositoryDownloadUrl: String?,
    @SerialName("api_data_url") val apiDataUrl: String,
    val purl: String
)

@Serializable
internal class PythonInspectorParty(
    val type: String,
    val role: String,
    val name: String?,
    val email: String?,
    val url: String?
)
