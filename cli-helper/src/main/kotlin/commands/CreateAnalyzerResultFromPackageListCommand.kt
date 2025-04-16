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

package org.ossreviewtoolkit.helper.commands

import com.fasterxml.jackson.databind.PropertyNamingStrategies
import com.fasterxml.jackson.module.kotlin.readValue

import com.github.ajalt.clikt.parameters.options.convert
import com.github.ajalt.clikt.parameters.options.default
import com.github.ajalt.clikt.parameters.options.option
import com.github.ajalt.clikt.parameters.options.required
import com.github.ajalt.clikt.parameters.types.file

import org.ossreviewtoolkit.helper.utils.OrtHelperCommand
import org.ossreviewtoolkit.helper.utils.writeOrtResult
import org.ossreviewtoolkit.model.AnalyzerResult
import org.ossreviewtoolkit.model.AnalyzerRun
import org.ossreviewtoolkit.model.Hash
import org.ossreviewtoolkit.model.Identifier
import org.ossreviewtoolkit.model.OrtResult
import org.ossreviewtoolkit.model.Package
import org.ossreviewtoolkit.model.PackageLinkage
import org.ossreviewtoolkit.model.PackageReference
import org.ossreviewtoolkit.model.Project
import org.ossreviewtoolkit.model.RemoteArtifact
import org.ossreviewtoolkit.model.Repository
import org.ossreviewtoolkit.model.Scope
import org.ossreviewtoolkit.model.VcsInfo
import org.ossreviewtoolkit.model.VcsType
import org.ossreviewtoolkit.model.config.Excludes
import org.ossreviewtoolkit.model.config.OrtConfiguration
import org.ossreviewtoolkit.model.config.RepositoryConfiguration
import org.ossreviewtoolkit.model.config.ScopeExclude
import org.ossreviewtoolkit.model.config.ScopeExcludeReason
import org.ossreviewtoolkit.model.mapper
import org.ossreviewtoolkit.model.orEmpty
import org.ossreviewtoolkit.model.utils.toPurl
import org.ossreviewtoolkit.plugins.packagecurationproviders.api.PackageCurationProviderFactory
import org.ossreviewtoolkit.utils.common.expandTilde
import org.ossreviewtoolkit.utils.config.setPackageCurations
import org.ossreviewtoolkit.utils.ort.Environment
import org.ossreviewtoolkit.utils.ort.ORT_CONFIG_FILENAME
import org.ossreviewtoolkit.utils.ort.ortConfigDirectory
import org.ossreviewtoolkit.utils.spdx.SpdxExpression

internal class CreateAnalyzerResultFromPackageListCommand : OrtHelperCommand(
    help = "A command which turns a package list file into an analyzer result."
) {
    private val packageListFile by option(
        "--package-list-file", "-i",
        help = "The package list file to read the packages metadata and the project metadata from."
    ).convert { it.expandTilde() }
        .file(mustExist = false, canBeFile = true, canBeDir = false, mustBeWritable = false, mustBeReadable = false)
        .convert { it.absoluteFile.normalize() }
        .required()

    private val ortFile by option(
        "--ort-file", "-o",
        help = "The ORT file to write the generated analyzer result to."
    ).convert { it.expandTilde() }
        .file(mustExist = false, canBeFile = true, canBeDir = false, mustBeWritable = false, mustBeReadable = false)
        .convert { it.absoluteFile.normalize() }
        .required()

    private val configFile by option(
        "--config",
        help = "The path to the ORT configuration file that configures the package curation providers."
    ).convert { it.expandTilde() }
        .file(mustExist = true, canBeFile = true, canBeDir = false, mustBeWritable = false, mustBeReadable = true)
        .convert { it.absoluteFile.normalize() }
        .default(ortConfigDirectory.resolve(ORT_CONFIG_FILENAME))

    override fun run() {
        val packageList = packageListFile.mapper().copy().apply {
            // Use camel case already now (even if in all places snake case is used), because there is a plan
            // to migrate from snake case to camel case in context of
            // https://github.com/oss-review-toolkit/ort/issues/3904.
            propertyNamingStrategy = PropertyNamingStrategies.LOWER_CAMEL_CASE
        }.readValue<PackageList>(packageListFile)

        val projectName = packageList.projectName?.takeUnless { it.isBlank() } ?: DEFAULT_PROJECT_NAME
        val projectVcs = packageList.projectVcs.toVcsInfo()

        val project = Project.EMPTY.copy(
            id = Identifier("$PROJECT_TYPE::$projectName:"),
            vcs = projectVcs,
            vcsProcessed = projectVcs.normalize(),
            scopeDependencies = setOfNotNull(
                packageList.dependencies.filterNot { it.isExcluded }.toScope(MAIN_SCOPE_NAME),
                packageList.dependencies.filter { it.isExcluded }.toScope(EXCLUDED_SCOPE_NAME)
            )
        )

        val ortConfig = OrtConfiguration.load(emptyMap(), configFile)
        val packageCurationProviders = PackageCurationProviderFactory.create(ortConfig.packageCurationProviders)

        val ortResult = OrtResult(
            analyzer = AnalyzerRun.EMPTY.copy(
                result = AnalyzerResult(
                    projects = setOf(project),
                    packages = packageList.dependencies.mapTo(mutableSetOf()) { it.toPackage() }
                ),
                environment = Environment()
            ),
            repository = Repository(
                vcs = projectVcs.normalize(),
                config = RepositoryConfiguration(
                    excludes = Excludes(
                        scopes = listOf(
                            ScopeExclude(EXCLUDED_SCOPE_NAME, ScopeExcludeReason.DEV_DEPENDENCY_OF)
                        )
                    )
                )
            )
        ).setPackageCurations(packageCurationProviders)

        writeOrtResult(ortResult, ortFile)
    }
}

private const val DEFAULT_PROJECT_NAME = "unknown"
private const val EXCLUDED_SCOPE_NAME = "excluded"
private const val MAIN_SCOPE_NAME = "main"
private const val PROJECT_TYPE = "Unmanaged" // This refers to the package manager (plugin) named "Unmanaged".

private data class PackageList(
    val projectName: String? = null,
    val projectVcs: Vcs? = null,
    val dependencies: List<Dependency> = emptyList()
)

private data class Dependency(
    val id: Identifier,
    val purl: String? = null,
    val vcs: Vcs? = null,
    val sourceArtifact: SourceArtifact? = null,
    val declaredLicenses: Set<String> = emptySet(),
    val concludedLicense: SpdxExpression? = null,
    val isExcluded: Boolean = false,
    val isDynamicallyLinked: Boolean = false,
    val labels: Map<String, String> = emptyMap()
)

private data class SourceArtifact(
    val url: String,
    val hash: Hash? = null
)

private data class Vcs(
    val type: String? = null,
    val url: String? = null,
    val revision: String? = null,
    val path: String? = null
)

private fun Vcs?.toVcsInfo(): VcsInfo =
    if (this == null) {
        VcsInfo.EMPTY
    } else {
        VcsInfo(
            type = type?.let { VcsType.forName(it) } ?: VcsType.UNKNOWN,
            url = url.orEmpty(),
            revision = revision.orEmpty(),
            path = path.orEmpty()
        )
    }

private fun Collection<Dependency>.toScope(name: String): Scope =
    Scope(
        name = name,
        dependencies = mapTo(mutableSetOf()) { dependency ->
            PackageReference(
                id = dependency.id,
                linkage = PackageLinkage.STATIC.takeUnless { dependency.isDynamicallyLinked } ?: PackageLinkage.DYNAMIC
            )
        }
    )

private fun Dependency.toPackage(): Package {
    val vcsInfo = vcs.toVcsInfo()

    return Package(
        id = id,
        purl = purl ?: id.toPurl(),
        sourceArtifact = sourceArtifact?.let { RemoteArtifact(url = it.url, it.hash ?: Hash.NONE) }.orEmpty(),
        vcs = vcsInfo,
        declaredLicenses = declaredLicenses,
        concludedLicense = concludedLicense,
        description = "",
        homepageUrl = "",
        binaryArtifact = RemoteArtifact.EMPTY,
        labels = labels
    )
}
