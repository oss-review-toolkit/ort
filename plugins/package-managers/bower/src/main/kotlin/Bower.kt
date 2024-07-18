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

@file:Suppress("TooManyFunctions")

package org.ossreviewtoolkit.plugins.packagemanagers.bower

import java.io.File
import java.util.Stack

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
import org.ossreviewtoolkit.utils.common.CommandLineTool
import org.ossreviewtoolkit.utils.common.Os
import org.ossreviewtoolkit.utils.common.stashDirectories

import org.semver4j.RangesList
import org.semver4j.RangesListFactory

/**
 * The [Bower](https://bower.io/) package manager for JavaScript.
 */
@Suppress("TooManyFunctions")
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

        stashDirectories(workingDir.resolve("bower_components")).use {
            installDependencies(workingDir)
            val dependenciesJson = listDependencies(workingDir)
            val packageInfo = parsePackageInfoJson(dependenciesJson)

            val packages = parsePackages(packageInfo)
            val dependenciesScope = Scope(
                name = SCOPE_NAME_DEPENDENCIES,
                dependencies = parseDependencyTree(packageInfo, SCOPE_NAME_DEPENDENCIES)
            )
            val devDependenciesScope = Scope(
                name = SCOPE_NAME_DEV_DEPENDENCIES,
                dependencies = parseDependencyTree(packageInfo, SCOPE_NAME_DEV_DEPENDENCIES)
            )

            val projectPackage = parsePackage(packageInfo)
            val project = Project(
                id = projectPackage.id,
                definitionFilePath = VersionControlSystem.getPathInfo(definitionFile).path,
                authors = parseAuthors(packageInfo),
                declaredLicenses = projectPackage.declaredLicenses,
                vcs = projectPackage.vcs,
                vcsProcessed = processProjectVcs(workingDir, projectPackage.vcs, projectPackage.homepageUrl),
                homepageUrl = projectPackage.homepageUrl,
                scopeDependencies = setOf(dependenciesScope, devDependenciesScope)
            )

            return listOf(ProjectAnalyzerResult(project, packages.values.toSet()))
        }
    }

    private fun installDependencies(workingDir: File) = run(workingDir, "--allow-root", "install")

    private fun listDependencies(workingDir: File) = run(workingDir, "--allow-root", "list", "--json").stdout
}

private const val SCOPE_NAME_DEPENDENCIES = "dependencies"
private const val SCOPE_NAME_DEV_DEPENDENCIES = "devDependencies"

private fun parsePackageId(info: PackageInfo) =
    Identifier(
        type = "Bower",
        namespace = "",
        name = info.pkgMeta.name.orEmpty(),
        version = info.pkgMeta.version.orEmpty()
    )

private fun parseRepositoryType(info: PackageInfo) = VcsType.forName(info.pkgMeta.repository?.type.orEmpty())

private fun parseRepositoryUrl(info: PackageInfo) = info.pkgMeta.repository?.url ?: info.pkgMeta.source.orEmpty()

private fun parseRevision(info: PackageInfo): String =
    info.pkgMeta.resolution?.commit ?: info.pkgMeta.resolution?.tag.orEmpty()

private fun parseVcsInfo(info: PackageInfo) =
    VcsInfo(
        type = parseRepositoryType(info),
        url = parseRepositoryUrl(info),
        revision = parseRevision(info)
    )

/**
 * Parse information about the author. According to https://github.com/bower/spec/blob/master/json.md#authors,
 * there are two formats to specify the authors of a package (similar to NPM). The difference is that the
 * strings or objects are inside an array.
 */
private fun parseAuthors(info: PackageInfo): Set<String> =
    info.pkgMeta.authors.mapNotNullTo(mutableSetOf()) { parseAuthorString(it.name, '<', '(') }

private fun parsePackage(info: PackageInfo) =
    Package(
        id = parsePackageId(info),
        authors = parseAuthors(info),
        declaredLicenses = setOfNotNull(info.pkgMeta.license?.takeUnless { it.isEmpty() }),
        description = info.pkgMeta.description.orEmpty(),
        homepageUrl = info.pkgMeta.homepage.orEmpty(),
        binaryArtifact = RemoteArtifact.EMPTY,
        sourceArtifact = RemoteArtifact.EMPTY, // TODO: implement me!
        vcs = parseVcsInfo(info)
    )

private fun getDependencyInfos(info: PackageInfo): Sequence<PackageInfo> =
    info.dependencies.asSequence().map { it.value }

private fun parsePackages(info: PackageInfo): Map<String, Package> {
    val result = mutableMapOf<String, Package>()

    val stack = Stack<PackageInfo>()
    stack += getDependencyInfos(info)

    while (!stack.empty()) {
        val currentInfo = stack.pop()
        val pkg = parsePackage(currentInfo)
        result["${pkg.id.name}:${pkg.id.version}"] = pkg

        stack += getDependencyInfos(currentInfo)
    }

    return result
}

private fun hasCompleteDependencies(info: PackageInfo, scopeName: String): Boolean {
    val dependencyKeys = info.dependencies.keys
    val dependencyRefKeys = info.pkgMeta.getDependencies(scopeName).keys

    return dependencyKeys.containsAll(dependencyRefKeys)
}

private fun dependencyKeyOf(info: PackageInfo): String? {
    // As non-null dependency keys are supposed to define an equivalence relation for parsing 'missing' nodes,
    // only the name and version attributes can be used. Typically, those attributes should be not null
    // however in particular for root projects the null case also happens.
    val name = info.pkgMeta.name.orEmpty()
    val version = info.pkgMeta.version.orEmpty()
    return "$name:$version".takeUnless { name.isEmpty() || version.isEmpty() }
}

private fun getNodesWithCompleteDependencies(info: PackageInfo): Map<String, PackageInfo> {
    val result = mutableMapOf<String, PackageInfo>()

    val stack = Stack<PackageInfo>().apply { push(info) }
    while (!stack.empty()) {
        val currentInfo = stack.pop()

        dependencyKeyOf(currentInfo)?.let { key ->
            if (hasCompleteDependencies(info, SCOPE_NAME_DEPENDENCIES) &&
                hasCompleteDependencies(info, SCOPE_NAME_DEV_DEPENDENCIES)
            ) {
                result[key] = currentInfo
            }
        }

        stack += getDependencyInfos(currentInfo)
    }

    return result
}

private fun parseDependencyTree(
    info: PackageInfo,
    scopeName: String,
    alternativeNodes: Map<String, PackageInfo> = getNodesWithCompleteDependencies(info)
): Set<PackageReference> {
    val result = mutableSetOf<PackageReference>()

    if (!hasCompleteDependencies(info, scopeName)) {
        // Bower leaves out a dependency entry for a child if there exists a similar node to its parent node
        // with the exact same name and resolved target. This makes it necessary to retrieve the information
        // about the subtree rooted at the parent from that other node containing the full dependency
        // information.
        // See https://github.com/bower/bower/blob/6bc778d/lib/core/Manager.js#L557 and below.
        val alternativeNode = checkNotNull(alternativeNodes[dependencyKeyOf(info)])
        return parseDependencyTree(alternativeNode, scopeName, alternativeNodes)
    }

    info.pkgMeta.getDependencies(scopeName).keys.forEach {
        val childNode = info.dependencies.getValue(it)
        val childScope = SCOPE_NAME_DEPENDENCIES
        val childDependencies = parseDependencyTree(childNode, childScope, alternativeNodes)
        val packageReference = PackageReference(
            id = parsePackageId(childNode),
            dependencies = childDependencies
        )
        result += packageReference
    }

    return result
}

private fun PackageMeta.getDependencies(scopeName: String) =
    when (scopeName) {
        SCOPE_NAME_DEPENDENCIES -> dependencies
        SCOPE_NAME_DEV_DEPENDENCIES -> devDependencies
        else -> error("Invalid scope name: '$scopeName'.")
    }
