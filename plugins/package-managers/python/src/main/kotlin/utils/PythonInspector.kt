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

package org.ossreviewtoolkit.plugins.packagemanagers.python.utils

import java.io.File

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonNamingStrategy
import kotlinx.serialization.json.decodeFromStream

import org.apache.logging.log4j.kotlin.logger

import org.ossreviewtoolkit.utils.common.CommandLineTool
import org.ossreviewtoolkit.utils.common.safeDeleteRecursively
import org.ossreviewtoolkit.utils.ort.createOrtTempFile

import org.semver4j.range.RangeList
import org.semver4j.range.RangeListFactory

private val json = Json {
    ignoreUnknownKeys = true
    namingStrategy = JsonNamingStrategy.SnakeCase
}

internal const val DEFAULT_PYTHON_VERSION = "3.13"

internal object PythonInspector : CommandLineTool {
    override fun command(workingDir: File?) = "python-inspector"

    override fun transformVersion(output: String) = output.removePrefix("Python-inspector version: ")

    override fun getVersionRequirement(): RangeList = RangeListFactory.create("[0.9.2,)")

    fun getSupportedPythonVersions(): List<String> {
        val stderr = run("--python-version", "x").stderr
        val versions = stderr.substringAfter("'x' is not one of ").substringBeforeLast('.')
        return versions.split(',', ' ').mapNotNull { version ->
            version.takeIf { "." in it }?.removeSurrounding("'")
        }
    }

    fun inspect(
        workingDir: File,
        definitionFile: File,
        pythonVersion: String,
        operatingSystem: String,
        analyzeSetupPyInsecurely: Boolean
    ): Result {
        val outputFile = createOrtTempFile(prefix = "python-inspector", suffix = ".json")

        val commandLineOptions = buildList {
            add("--python-version")
            add(pythonVersion)

            add("--operating-system")
            add(operatingSystem)

            add("--json-pdt")
            add(outputFile.absolutePath)

            if (analyzeSetupPyInsecurely) {
                add("--analyze-setup-py-insecurely")
            }

            if (definitionFile.name == "setup.py") {
                add("--setup-py")
            } else {
                add("--requirement")
            }

            add(definitionFile.absolutePath)

            if (definitionFile.name != "setup.py") {
                // If a setup.py file exists, add it to the analysis to capture additional project metadata.
                val setupFile = definitionFile.resolveSibling("setup.py")
                if (setupFile.isFile) {
                    add("--setup-py")
                    add(setupFile.absolutePath)
                }
            }
        }

        return try {
            run(
                *commandLineOptions.toTypedArray(),
                workingDir = workingDir,
                environment = mapOf("LC_ALL" to "en_US.UTF-8")
            ).requireSuccess()
            val binaryResult = outputFile.inputStream().use { json.decodeFromStream<Result>(it) }

            // Do a consistency check on the binary packages.
            val packagePurls = mutableSetOf<String>()
            binaryResult.projects.forEach { project ->
                project.packageData.forEach { data ->
                    data.dependencies.mapNotNullTo(packagePurls) { it.purl }
                }
            }

            if (packagePurls.size != binaryResult.packages.size) {
                logger.warn {
                    "The number of unique dependencies (${packagePurls.size}) does not match the number of packages " +
                        "(${binaryResult.packages.size}), which might indicate a bug in python-inspector."
                }

                val resultsPurls = binaryResult.packages.mapNotNullTo(mutableSetOf()) { it.purl }
                logger.warn { "Packages that are not contained as dependencies: ${packagePurls - resultsPurls}" }
                logger.warn { "Dependencies that are not contained as packages: ${resultsPurls - packagePurls}" }
            }

            // TODO: Avoid this terrible hack to run once more with `--prefer-source` to work around
            //       https://github.com/aboutcode-org/python-inspector/issues/229.
            run(
                *(commandLineOptions + "--prefer-source").toTypedArray(),
                workingDir = workingDir,
                environment = mapOf("LC_ALL" to "en_US.UTF-8")
            ).requireSuccess()
            val sourceResult = outputFile.inputStream().use { json.decodeFromStream<Result>(it) }

            binaryResult.copy(packages = binaryResult.packages + sourceResult.packages)
        } finally {
            outputFile.parentFile.safeDeleteRecursively()
        }
    }

    @Serializable
    internal data class Result(
        @SerialName("files") val projects: List<Project>,
        val resolvedDependenciesGraph: List<ResolvedDependency>,
        val packages: List<Package>
    )

    @Serializable
    internal data class Project(
        val path: String,
        val packageData: List<PackageData>
    )

    @Serializable
    internal data class PackageData(
        val namespace: String?,
        val name: String?,
        val version: String?,
        val description: String?,
        val parties: List<Party>,
        val homepageUrl: String?,
        val declaredLicense: DeclaredLicense?,
        val dependencies: List<Dependency>
    )

    @Serializable
    internal data class DeclaredLicense(
        val license: String? = null,
        val classifiers: List<String> = emptyList()
    )

    @Serializable
    internal data class Dependency(
        val purl: String?,
        val scope: String,
        val isRuntime: Boolean,
        val isOptional: Boolean,
        val isResolved: Boolean
    )

    @Serializable
    internal data class ResolvedDependency(
        val key: String,
        val packageName: String,
        val installedVersion: String,
        val dependencies: List<ResolvedDependency>
    )

    @Serializable
    internal data class Package(
        val type: String,
        val namespace: String?,
        val name: String,
        val version: String,
        val description: String,
        val parties: List<Party>,
        val homepageUrl: String?,
        val downloadUrl: String,
        val size: Long,
        val sha1: String?,
        val md5: String?,
        val sha256: String?,
        val sha512: String?,
        val codeViewUrl: String?,
        val vcsUrl: String?,
        val copyright: String?,
        val licenseExpression: String?,
        val declaredLicense: DeclaredLicense?,
        val sourcePackages: List<String>,
        val repositoryHomepageUrl: String?,
        val repositoryDownloadUrl: String?,
        val apiDataUrl: String,
        val purl: String?
    )

    @Serializable
    internal class Party(
        val type: String,
        val role: String,
        val name: String?,
        val email: String?,
        val url: String?
    )
}
