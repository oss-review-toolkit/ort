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

package org.ossreviewtoolkit.plugins.packagemanagers.ortbom

import com.fasterxml.jackson.module.kotlin.readValue

import java.io.File

import kotlin.collections.orEmpty

import org.ossreviewtoolkit.analyzer.PackageManager
import org.ossreviewtoolkit.analyzer.PackageManagerFactory
import org.ossreviewtoolkit.model.Hash
import org.ossreviewtoolkit.model.Identifier
import org.ossreviewtoolkit.model.Issue
import org.ossreviewtoolkit.model.Package
import org.ossreviewtoolkit.model.PackageLinkage
import org.ossreviewtoolkit.model.PackageReference
import org.ossreviewtoolkit.model.Project
import org.ossreviewtoolkit.model.ProjectAnalyzerResult
import org.ossreviewtoolkit.model.RemoteArtifact
import org.ossreviewtoolkit.model.Scope
import org.ossreviewtoolkit.model.VcsInfo
import org.ossreviewtoolkit.model.VcsType
import org.ossreviewtoolkit.model.config.AnalyzerConfiguration
import org.ossreviewtoolkit.model.config.Excludes
import org.ossreviewtoolkit.model.mapper
import org.ossreviewtoolkit.model.orEmpty
import org.ossreviewtoolkit.plugins.api.OrtPlugin
import org.ossreviewtoolkit.plugins.api.PluginDescriptor

@OrtPlugin(
    displayName = "OrtBomFile",
    description = "The package manager that uses ORT-specific BOM file as package list source.",
    factory = PackageManagerFactory::class
)
class OrtBomFile(override val descriptor: PluginDescriptor = OrtBomFileFactory.descriptor) :
    PackageManager("OrtBomFile") {

    companion object {
        private const val DEFAULT_PROJECT_NAME = "unknown"
        private const val EXCLUDED_SCOPE_NAME = "excluded"
        private const val MAIN_SCOPE_NAME = "main"
        private const val PROJECT_TYPE = "Unmanaged" // This refers to the package manager (plugin) named "Unmanaged".
    }

    override val globsForDefinitionFiles = listOf("ort-bom.yml", "ort-bom.yaml", "ort-bom.json")

    override fun resolveDependencies(
        analysisRoot: File,
        definitionFile: File,
        excludes: Excludes,
        analyzerConfig: AnalyzerConfiguration,
        labels: Map<String, String>
    ): List<ProjectAnalyzerResult> {
        val parsedProject = definitionFile.mapper().copy().readValue<OrtBomProjectDto>(definitionFile)

        val projectWithIssues = extractProject(parsedProject)
        val packagesWithIssues = extractPackages(parsedProject)

        val res = ProjectAnalyzerResult(
            project = projectWithIssues.first,
            packages = packagesWithIssues.first,
            issues = projectWithIssues.second + packagesWithIssues.second
        )

        return listOf(res)
    }

    override fun mapDefinitionFiles(
        analysisRoot: File,
        definitionFiles: List<File>,
        analyzerConfig: AnalyzerConfiguration
    ): List<File> {
        require(definitionFiles.isNotEmpty()) {
            "Definition files must contain at least one definition file."
        }

        require(definitionFiles.size == 1) {
            "Only one definition file is allowed."
        }

        return definitionFiles
    }

    private fun extractProject(projectDto: OrtBomProjectDto): Pair<Project, List<Issue>> {
        val issues = mutableListOf<Issue>()
        val project = Project.EMPTY.copy(
            id = Identifier(
                name = projectDto.projectName?.takeUnless { it.isBlank() } ?: DEFAULT_PROJECT_NAME,
                type = PROJECT_TYPE,
                namespace = "",
                version = ""
            ),
            authors = projectDto.authors.orEmpty(),
            vcs = projectDto.vcs.toVcsInfo(),
            homepageUrl = projectDto.homepageUrl.orEmpty(),
            scopeDependencies = setOfNotNull(
                projectDto.dependencies.filterNot { it.isExcluded }.toScope(MAIN_SCOPE_NAME),
                projectDto.dependencies.filter { it.isExcluded }.toScope(EXCLUDED_SCOPE_NAME)
            )
        )
        return Pair(project, issues)
    }

    private fun extractPackages(project: OrtBomProjectDto): Pair<Set<Package>, List<Issue>> {
        val issues = mutableListOf<Issue>()
        val packages = mutableSetOf<Package>()

        project.dependencies.forEach {
            val packageWithIssues = it.toPackage()
            packages.add(packageWithIssues.first)
        }

        return Pair(packages, issues)
    }

    private fun DependencyDto.toPackage(): Pair<Package, List<Issue>> {
        try {
            val pkg = Package(
                id = Identifier.fromPurl(purl),
                purl = purl,
                sourceArtifact = sourceArtifact?.let {
                    RemoteArtifact(url = it.url.orEmpty(), if (it.hash != null) Hash.create(it.hash) else Hash.NONE)
                }.orEmpty(),
                vcs = vcs.toVcsInfo(),
                declaredLicenses = declaredLicenses,
                concludedLicense = concludedLicense,
                description = description.orEmpty(),
                homepageUrl = homepageUrl.orEmpty(),
                binaryArtifact = RemoteArtifact.EMPTY,
                labels = labels
            )

            return Pair(pkg, emptyList())
        } catch (ex: IllegalArgumentException) {
            val issue = Issue(
                message = ex.message.orEmpty(),
                source = "OrtBomFile"
            )
            return Pair(Package.EMPTY, listOf(issue))
        }
    }

    private fun VcsDto?.toVcsInfo(): VcsInfo =
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

    private fun Collection<DependencyDto>.toScope(name: String): Scope =
        Scope(
            name = name,
            dependencies = mapTo(mutableSetOf()) { dependency ->
                PackageReference(
                    id = Identifier.fromPurl(dependency.purl),
                    linkage = PackageLinkage.STATIC
                )
            }
        )
}
