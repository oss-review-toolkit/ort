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

package org.ossreviewtoolkit.plugins.packagemanagers.swiftpm

import java.io.File

import org.ossreviewtoolkit.analyzer.AbstractPackageManagerFactory
import org.ossreviewtoolkit.analyzer.PackageManager
import org.ossreviewtoolkit.downloader.VcsHost
import org.ossreviewtoolkit.downloader.VersionControlSystem
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
import org.ossreviewtoolkit.model.config.RepositoryConfiguration
import org.ossreviewtoolkit.model.orEmpty
import org.ossreviewtoolkit.utils.common.CommandLineTool
import org.ossreviewtoolkit.utils.common.Os
import org.ossreviewtoolkit.utils.common.toUri
import org.ossreviewtoolkit.utils.ort.normalizeVcsUrl

private const val PACKAGE_SWIFT_NAME = "Package.swift"
private const val PACKAGE_RESOLVED_NAME = "Package.resolved"

private const val PACKAGE_TYPE = "Swift"

private const val DEPENDENCIES_SCOPE_NAME = "dependencies"

/**
 * The [Swift Package Manager](https://github.com/apple/swift-package-manager).
 */
class SwiftPm(
    name: String,
    analysisRoot: File,
    analyzerConfig: AnalyzerConfiguration,
    repoConfig: RepositoryConfiguration
) : PackageManager(name, "SwiftPM", analysisRoot, analyzerConfig, repoConfig), CommandLineTool {
    class Factory : AbstractPackageManagerFactory<SwiftPm>("SwiftPM") {
        override val globsForDefinitionFiles = listOf(PACKAGE_SWIFT_NAME, PACKAGE_RESOLVED_NAME)

        override fun create(
            analysisRoot: File,
            analyzerConfig: AnalyzerConfiguration,
            repoConfig: RepositoryConfiguration
        ) = SwiftPm(type, analysisRoot, analyzerConfig, repoConfig)
    }

    override fun command(workingDir: File?) = if (Os.isWindows) "swift.exe" else "swift"

    override fun transformVersion(output: String) = output.substringAfter("version ").substringBefore(" (")

    override fun mapDefinitionFiles(definitionFiles: List<File>): List<File> {
        return definitionFiles.filterNot { file -> file.path.contains(".build/checkouts") }
    }

    override fun resolveDependencies(definitionFile: File, labels: Map<String, String>): List<ProjectAnalyzerResult> {
        if (definitionFile.name != PACKAGE_RESOLVED_NAME) {
            requireLockfile(definitionFile.parentFile) { definitionFile.resolveSibling(PACKAGE_RESOLVED_NAME).isFile }
        }

        return when (definitionFile.name) {
            PACKAGE_SWIFT_NAME -> resolveDefinitionFileDependencies(definitionFile)
            else -> resolveLockfileDependencies(definitionFile)
        }
    }

    /**
     * Resolves dependencies when only a lockfile aka `Package.Resolved` is available. This commonly applies to e.g.
     * Xcode projects which only have a lockfile, but no `Package.swift` file.
     */
    private fun resolveLockfileDependencies(packageResolvedFile: File): List<ProjectAnalyzerResult> {
        val issues = mutableListOf<Issue>()
        val packages = mutableSetOf<Package>()
        val scopeDependencies = mutableSetOf<Scope>()

        parseLockfile(packageResolvedFile).onSuccess { pins ->
            pins.mapTo(packages) { it.toPackage() }
            scopeDependencies += Scope(
                name = DEPENDENCIES_SCOPE_NAME,
                dependencies = packages.mapTo(mutableSetOf()) { it.toReference(linkage = PackageLinkage.DYNAMIC) }
            )
        }.onFailure {
            issues += Issue(source = managerName, message = it.message.orEmpty())
        }

        return listOf(
            ProjectAnalyzerResult(
                project = projectFromDefinitionFile(packageResolvedFile, scopeDependencies),
                packages = packages,
                issues = issues
            )
        )
    }

    /**
     * Resolves dependencies of a `Package.swift` file.
     * This method parses dependencies from `swift package show-dependencies --format json` output.
     * Also, this method provides parent-child associations for parsed dependencies.
     */
    private fun resolveDefinitionFileDependencies(packageSwiftFile: File): List<ProjectAnalyzerResult> {
        val swiftPackage = getSwiftPackage(packageSwiftFile)

        val issues = mutableListOf<Issue>()
        val packages = mutableSetOf<Package>()
        val scopeDependencies = mutableSetOf<Scope>()
        val pinsByIdentity = mutableMapOf<String, PinV2>()

        val lockfile = packageSwiftFile.resolveSibling(PACKAGE_RESOLVED_NAME)
        if (lockfile.isFile) {
            // The command `swift package show-dependencies` does not create a (non-existing) lockfile in case there
            // are no non-local dependencies.
            parseLockfile(lockfile).onSuccess { pins ->
                pins.associateByTo(pinsByIdentity) { it.identity }
            }.onFailure {
                issues += Issue(source = managerName, message = it.message.orEmpty())
            }
        }

        swiftPackage.getTransitiveDependencies().mapTo(packages) { it.toPackage(pinsByIdentity) }
        scopeDependencies += Scope(
            name = DEPENDENCIES_SCOPE_NAME,
            dependencies = swiftPackage.dependencies.mapTo(mutableSetOf()) { it.toPackageReference(pinsByIdentity) }
        )

        return listOf(
            ProjectAnalyzerResult(
                project = projectFromDefinitionFile(packageSwiftFile, scopeDependencies),
                packages = packages,
                issues = issues
            )
        )
    }

    private fun getSwiftPackage(packageSwiftFile: File): SwiftPackage {
        // TODO: Handle errors from stderr.
        val result = run(
            packageSwiftFile.parentFile,
            "package",
            "show-dependencies",
            "--format",
            "json"
        ).stdout

        return parseSwiftPackage(result)
    }

    private fun projectFromDefinitionFile(definitionFile: File, scopeDependencies: Set<Scope>): Project {
        val vcsInfo = VersionControlSystem.forDirectory(definitionFile.parentFile)?.getInfo().orEmpty()

        val projectIdentifier = Identifier(
            type = managerName,
            version = vcsInfo.revision,
            namespace = "",
            name = getFallbackProjectName(analysisRoot, definitionFile)
        )

        return Project(
            vcs = VcsInfo.EMPTY,
            id = projectIdentifier,
            declaredLicenses = emptySet(),
            homepageUrl = "",
            scopeDependencies = scopeDependencies,
            vcsProcessed = processProjectVcs(definitionFile.parentFile),
            definitionFilePath = VersionControlSystem.getPathInfo(definitionFile).path
        )
    }
}

private fun SwiftPackage.toId(pinsByIdentity: Map<String, PinV2>): Identifier =
    pinsByIdentity[identity]?.toId() ?: Identifier(
        type = PACKAGE_TYPE,
        namespace = "",
        name = getCanonicalName(url),
        version = version.takeUnless { it == "unspecified" }.orEmpty()
    )

private fun SwiftPackage.toVcsInfo(pinsByIdentity: Map<String, PinV2>): VcsInfo =
    pinsByIdentity[identity]?.toVcsInfo() ?: VcsHost.parseUrl(url)

private fun SwiftPackage.toPackage(pinsByIdentity: Map<String, PinV2>): Package =
    createPackage(toId(pinsByIdentity), toVcsInfo(pinsByIdentity))

private fun SwiftPackage.toPackageReference(pinsByIdentity: Map<String, PinV2>): PackageReference =
    PackageReference(
        id = toId(pinsByIdentity),
        dependencies = dependencies.mapTo(mutableSetOf()) { it.toPackageReference(pinsByIdentity) }
    )

private fun SwiftPackage.getTransitiveDependencies(): Set<SwiftPackage> {
    val queue = dependencies.toMutableList()
    val result = mutableSetOf<SwiftPackage>()

    while (queue.isNotEmpty()) {
        val swiftPackage = queue.removeFirst()
        result += swiftPackage
        queue += swiftPackage.dependencies
    }

    return result
}

private fun PinV2.toId(): Identifier =
    Identifier(
        type = PACKAGE_TYPE,
        namespace = "",
        name = getCanonicalName(location),
        version = state?.run {
            when {
                !version.isNullOrBlank() -> version
                !revision.isNullOrBlank() -> "revision-$revision"
                !branch.isNullOrBlank() -> "branch-$branch"
                else -> ""
            }
        }.orEmpty()
    )

private fun PinV2.toVcsInfo(): VcsInfo {
    if (kind != PinV2.Kind.REMOTE_SOURCE_CONTROL) return VcsInfo.EMPTY

    return VcsInfo(
        type = VcsType.GIT,
        url = normalizeVcsUrl(location),
        revision = state?.revision.orEmpty()
    )
}

private fun PinV2.toPackage(): Package = createPackage(toId(), toVcsInfo())

private fun createPackage(id: Identifier, vcsInfo: VcsInfo) =
    Package(
        vcs = vcsInfo,
        description = "",
        id = id,
        binaryArtifact = RemoteArtifact.EMPTY,
        sourceArtifact = RemoteArtifact.EMPTY,
        declaredLicenses = emptySet(), // SPM files do not declare any licenses.
        homepageUrl = ""
    )

/**
 * Return the canonical name for a package based on the given [repositoryUrl].
 * The algorithm assumes that the repository URL does not point to the local file
 * system, as support for local dependencies is not implemented yet in ORT. Otherwise,
 * the algorithm tries to effectively mimic the algorithm described in
 * https://github.com/apple/swift-package-manager/blob/24bfdd180afdf78160e7a2f6f6deb2c8249d40d3/Sources/PackageModel/PackageIdentity.swift#L345-L415.
 */
internal fun getCanonicalName(repositoryUrl: String): String {
    val normalizedUrl = normalizeVcsUrl(repositoryUrl)
    return normalizedUrl.toUri {
        it.host + it.path.removeSuffix(".git")
    }.getOrDefault(normalizedUrl).lowercase()
}
