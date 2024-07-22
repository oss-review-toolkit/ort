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

package org.ossreviewtoolkit.plugins.packagemanagers.bower

import java.io.File
import java.util.LinkedList

import org.ossreviewtoolkit.analyzer.AbstractPackageManagerFactory
import org.ossreviewtoolkit.analyzer.PackageManager
import org.ossreviewtoolkit.analyzer.parseAuthorString
import org.ossreviewtoolkit.downloader.VersionControlSystem
import org.ossreviewtoolkit.model.Identifier
import org.ossreviewtoolkit.model.Package
import org.ossreviewtoolkit.model.PackageReference
import org.ossreviewtoolkit.model.Project
import org.ossreviewtoolkit.model.ProjectAnalyzerResult
import org.ossreviewtoolkit.model.RemoteArtifact
import org.ossreviewtoolkit.model.Scope
import org.ossreviewtoolkit.model.VcsInfo
import org.ossreviewtoolkit.model.VcsType
import org.ossreviewtoolkit.model.config.AnalyzerConfiguration
import org.ossreviewtoolkit.model.config.RepositoryConfiguration
import org.ossreviewtoolkit.plugins.packagemanagers.bower.PackageInfo.Endpoint
import org.ossreviewtoolkit.utils.common.CommandLineTool
import org.ossreviewtoolkit.utils.common.Os
import org.ossreviewtoolkit.utils.common.stashDirectories

import org.semver4j.RangesList
import org.semver4j.RangesListFactory

/**
 * The [Bower](https://bower.io/) package manager for JavaScript.
 */
class Bower(
    name: String,
    analysisRoot: File,
    analyzerConfig: AnalyzerConfiguration,
    repoConfig: RepositoryConfiguration
) : PackageManager(name, analysisRoot, analyzerConfig, repoConfig), CommandLineTool {
    class Factory : AbstractPackageManagerFactory<Bower>("Bower") {
        override val globsForDefinitionFiles = listOf("bower.json")

        override fun create(
            analysisRoot: File,
            analyzerConfig: AnalyzerConfiguration,
            repoConfig: RepositoryConfiguration
        ) = Bower(type, analysisRoot, analyzerConfig, repoConfig)
    }

    override fun command(workingDir: File?) = if (Os.isWindows) "bower.cmd" else "bower"

    override fun getVersionRequirement(): RangesList = RangesListFactory.create(">=1.8.8")

    override fun beforeResolution(definitionFiles: List<File>) = checkVersion()

    override fun resolveDependencies(definitionFile: File, labels: Map<String, String>): List<ProjectAnalyzerResult> {
        val workingDir = definitionFile.parentFile

        stashDirectories(workingDir.resolve("bower_components")).use { _ ->
            val projectPackageInfo = getProjectPackageInfo(workingDir)

            val packages = projectPackageInfo
                .getTransitiveDependencies()
                .distinctBy { it.key }
                .mapTo(mutableSetOf()) { it.toPackage() }

            val scopes = SCOPE_NAMES.mapTo(mutableSetOf()) { scopeName ->
                Scope(
                    name = scopeName,
                    dependencies = parseDependencyTree(projectPackageInfo, scopeName)
                )
            }

            val projectPackage = projectPackageInfo.toPackage()
            val project = Project(
                id = projectPackage.id,
                definitionFilePath = VersionControlSystem.getPathInfo(definitionFile).path,
                authors = projectPackage.authors,
                declaredLicenses = projectPackage.declaredLicenses,
                vcs = projectPackage.vcs,
                vcsProcessed = processProjectVcs(workingDir, projectPackage.vcs, projectPackage.homepageUrl),
                homepageUrl = projectPackage.homepageUrl,
                scopeDependencies = scopes
            )

            return listOf(ProjectAnalyzerResult(project, packages))
        }
    }

    private fun getProjectPackageInfo(workingDir: File): PackageInfo {
        run(workingDir, "--allow-root", "install").requireSuccess()
        val json = run(workingDir, "--allow-root", "list", "--json").stdout
        return parsePackageInfoJson(json)
    }
}

private const val SCOPE_NAME_DEPENDENCIES = "dependencies"
private const val SCOPE_NAME_DEV_DEPENDENCIES = "devDependencies"
private val SCOPE_NAMES = setOf(SCOPE_NAME_DEPENDENCIES, SCOPE_NAME_DEV_DEPENDENCIES)

private val PackageInfo.key: Endpoint
    get() = endpoint

private fun PackageInfo.getScopeDependencies(scopeName: String): Set<String> =
    when (scopeName) {
        SCOPE_NAME_DEPENDENCIES -> pkgMeta.dependencies.keys
        SCOPE_NAME_DEV_DEPENDENCIES -> pkgMeta.devDependencies.keys
        else -> error("Invalid scope name: '$scopeName'.")
    }

private fun PackageInfo.getTransitiveDependencies(): List<PackageInfo> {
    val result = LinkedList<PackageInfo>()
    val queue = LinkedList(dependencies.values)

    while (queue.isNotEmpty()) {
        val info = queue.removeFirst()
        result += info
        queue += info.dependencies.values
    }

    return result
}

private fun PackageInfo.toId() =
    Identifier(
        type = "Bower",
        namespace = "",
        name = pkgMeta.name.orEmpty(),
        version = pkgMeta.version.orEmpty()
    )

private fun PackageInfo.toVcsInfo() =
    VcsInfo(
        type = VcsType.forName(pkgMeta.repository?.type.orEmpty()),
        url = pkgMeta.repository?.url ?: pkgMeta.source.orEmpty(),
        revision = pkgMeta.resolution?.commit ?: pkgMeta.resolution?.tag.orEmpty()
    )

private fun PackageInfo.toPackage() =
    Package(
        id = toId(),
        // See https://github.com/bower/spec/blob/master/json.md#authors.
        authors = pkgMeta.authors.mapNotNullTo(mutableSetOf()) { parseAuthorString(it.name, '<', '(') },
        declaredLicenses = setOfNotNull(pkgMeta.license?.takeUnless { it.isEmpty() }),
        description = pkgMeta.description.orEmpty(),
        homepageUrl = pkgMeta.homepage.orEmpty(),
        binaryArtifact = RemoteArtifact.EMPTY,
        sourceArtifact = RemoteArtifact.EMPTY, // TODO: implement me!
        vcs = toVcsInfo()
    )

private fun hasCompleteDependencies(info: PackageInfo, scopeName: String): Boolean {
    val dependencyKeys = info.dependencies.keys
    val dependencyRefKeys = info.getScopeDependencies(scopeName)

    return dependencyKeys.containsAll(dependencyRefKeys)
}

private fun getPackageInfosWithCompleteDependencies(root: PackageInfo): Map<Endpoint, PackageInfo> =
    (root.getTransitiveDependencies() + root).associateBy { it.key }.filter { (_, info) ->
        hasCompleteDependencies(info, SCOPE_NAME_DEPENDENCIES)
            && hasCompleteDependencies(info, SCOPE_NAME_DEV_DEPENDENCIES)
    }

private fun parseDependencyTree(
    info: PackageInfo,
    scopeName: String,
    alternativeInfos: Map<Endpoint, PackageInfo> = getPackageInfosWithCompleteDependencies(info)
): Set<PackageReference> {
    val result = mutableSetOf<PackageReference>()

    if (!hasCompleteDependencies(info, scopeName)) {
        // Bower leaves out a dependency entry for a child if there exists a similar entry to its parent entry
        // with the exact same name and resolved target. This makes it necessary to retrieve the information
        // about the subtree rooted at the parent from that other entry containing the full dependency
        // information.
        // See https://github.com/bower/bower/blob/6bc778d/lib/core/Manager.js#L557 and below.
        val alternativeInfo = alternativeInfos.getValue(info.key)
        return parseDependencyTree(alternativeInfo, scopeName, alternativeInfos)
    }

    info.getScopeDependencies(scopeName).forEach {
        val childInfo = info.dependencies.getValue(it)
        val childScope = SCOPE_NAME_DEPENDENCIES
        val childDependencies = parseDependencyTree(childInfo, childScope, alternativeInfos)
        val packageReference = PackageReference(
            id = childInfo.toId(),
            dependencies = childDependencies
        )
        result += packageReference
    }

    return result
}
