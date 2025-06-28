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

package org.ossreviewtoolkit.plugins.packagemanagers.stack

import java.io.File
import java.io.IOException

import okhttp3.OkHttpClient

import org.apache.logging.log4j.kotlin.logger

import org.ossreviewtoolkit.analyzer.PackageManager
import org.ossreviewtoolkit.analyzer.PackageManager.Companion.processPackageVcs
import org.ossreviewtoolkit.analyzer.PackageManagerFactory
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
import org.ossreviewtoolkit.model.collectDependencies
import org.ossreviewtoolkit.model.config.AnalyzerConfiguration
import org.ossreviewtoolkit.model.config.Excludes
import org.ossreviewtoolkit.model.utils.toPurl
import org.ossreviewtoolkit.plugins.api.OrtPlugin
import org.ossreviewtoolkit.plugins.api.PluginDescriptor
import org.ossreviewtoolkit.utils.common.CommandLineTool
import org.ossreviewtoolkit.utils.common.ProcessCapture
import org.ossreviewtoolkit.utils.common.safeDeleteRecursively
import org.ossreviewtoolkit.utils.ort.downloadText
import org.ossreviewtoolkit.utils.ort.okHttpClient

import org.semver4j.range.RangeList
import org.semver4j.range.RangeListFactory

private const val EXTERNAL_SCOPE_NAME = "external"
private const val TEST_SCOPE_NAME = "test"
private const val BENCH_SCOPE_NAME = "bench"
private val SCOPE_NAMES = setOf(EXTERNAL_SCOPE_NAME, TEST_SCOPE_NAME, BENCH_SCOPE_NAME)

internal object StackCommand : CommandLineTool {
    override fun command(workingDir: File?) = "stack"

    override fun transformVersion(output: String) =
        output.removePrefix("Version ").substringBefore(',').substringBefore(' ')

    override fun getVersionRequirement(): RangeList = RangeListFactory.create(">=2.1.1")

    override fun run(vararg args: CharSequence, workingDir: File?, environment: Map<String, String>): ProcessCapture {
        // Delete any left-overs from interrupted stack runs.
        workingDir?.resolve(".stack-work")?.safeDeleteRecursively()

        return super.run(args = args, workingDir, environment)
    }
}

/**
 * The [Stack](https://haskellstack.org/) package manager for Haskell.
 */
@OrtPlugin(
    displayName = "Stack",
    description = "The Stack package manager for Haskell.",
    factory = PackageManagerFactory::class
)
class Stack(override val descriptor: PluginDescriptor = StackFactory.descriptor) : PackageManager("Stack") {
    override val globsForDefinitionFiles = listOf("stack.yaml")

    override fun beforeResolution(
        analysisRoot: File,
        definitionFiles: List<File>,
        analyzerConfig: AnalyzerConfiguration
    ) = StackCommand.checkVersion()

    override fun resolveDependencies(
        analysisRoot: File,
        definitionFile: File,
        excludes: Excludes,
        analyzerConfig: AnalyzerConfiguration,
        labels: Map<String, String>
    ): List<ProjectAnalyzerResult> {
        val workingDir = definitionFile.parentFile

        val dependenciesForScopeName = SCOPE_NAMES.associateWith { listDependencies(workingDir, it) }

        val packageForName = dependenciesForScopeName.values.flatten()
            .distinctBy { it.name }
            .filterNot { it.isProject() } // Do not add the project as a package.
            .associate { it.name to it.toPackage() }

        val scopes = dependenciesForScopeName.mapTo(mutableSetOf()) { (name, dependencies) ->
            dependencies.toScope(name, packageForName)
        }

        val referencedPackages = scopes.collectDependencies()
        val packages = packageForName.values.filterTo(mutableSetOf()) { it.id in referencedPackages }
        val project = getProject(definitionFile, scopes)

        return listOf(ProjectAnalyzerResult(project, packages))
    }

    private fun listDependencies(workingDir: File, scope: String): List<Dependency> {
        val scopeOptions = listOfNotNull(
            "--$scope",
            // Disable the default inclusion of external dependencies if another scope than "external" is specified.
            "--no-$EXTERNAL_SCOPE_NAME".takeIf { scope != EXTERNAL_SCOPE_NAME }
        )

        val dependenciesJson = StackCommand.run(
            // Use a hints file for global packages to not require installing the Glasgow Haskell Compiler (GHC).
            "ls", "dependencies", "json", "--global-hints", *scopeOptions.toTypedArray(), workingDir = workingDir
        ).requireSuccess().stdout

        return dependenciesJson.parseDependencies()
    }

    private fun getProject(definitionFile: File, scopes: Set<Scope>): Project {
        val workingDir = definitionFile.parentFile

        // Parse project information from the *.cabal file.
        val cabalFiles = workingDir.walk().filter {
            it.isFile && it.extension == "cabal"
        }.toList()

        val cabalFile = when (cabalFiles.size) {
            0 -> throw IOException("No *.cabal file found in '$workingDir'.")
            1 -> cabalFiles.first()
            else -> throw IOException("Multiple *.cabal files found in '$cabalFiles'.")
        }

        val projectPackage = parseCabalFile(cabalFile.readText(), projectType)

        return Project(
            id = projectPackage.id,
            definitionFilePath = VersionControlSystem.getPathInfo(definitionFile).path,
            authors = projectPackage.authors,
            declaredLicenses = projectPackage.declaredLicenses,
            vcs = projectPackage.vcs,
            vcsProcessed = processProjectVcs(workingDir, projectPackage.vcs, projectPackage.homepageUrl),
            homepageUrl = projectPackage.homepageUrl,
            scopeDependencies = scopes
        )
    }

    private fun Dependency.toPackage(): Package {
        val id = Identifier(
            type = "Hackage",
            namespace = "",
            name = name,
            version = version
        )

        if (location == null || location.type == Location.TYPE_HACKAGE) {
            okHttpClient.downloadCabalFile(id)?.let { return parseCabalFile(it, "Hackage") }
        }

        return Package.EMPTY.copy(
            id = id,
            purl = id.toPurl(),
            declaredLicenses = setOf(license)
        )
    }
}

private fun Dependency.isProject(): Boolean = location?.type == Location.TYPE_PROJECT

private fun Collection<Dependency>.toScope(scopeName: String, packageForName: Map<String, Package>): Scope {
    // TODO: Stack identifies dependencies only by name. Find out how dependencies with the same name but in
    //       different namespaces should be handled.
    val dependencyForName = associateBy { it.name }

    return Scope(
        name = scopeName,
        dependencies = single { it.isProject() }.dependencies.mapTo(mutableSetOf()) { name ->
            dependencyForName.getValue(name).toPackageReference(dependencyForName, packageForName)
        }
    )
}

private fun Dependency.toPackageReference(
    dependencyForName: Map<String, Dependency>,
    packageForName: Map<String, Package>
): PackageReference =
    PackageReference(
        id = packageForName.getValue(name).id,
        dependencies = dependencies.mapTo(mutableSetOf()) { name ->
            dependencyForName.getValue(name).toPackageReference(dependencyForName, packageForName)
        }
    )

private fun parseKeyValue(i: ListIterator<String>, keyPrefix: String = ""): Map<String, String> {
    fun getIndentation(line: String) = line.takeWhile { it.isWhitespace() }.length

    var indentation: Int? = null
    val map = mutableMapOf<String, String>()

    while (i.hasNext()) {
        val line = i.next()

        // Skip blank lines and comments.
        if (line.isBlank() || line.trimStart().startsWith("--")) continue

        if (indentation == null) {
            indentation = getIndentation(line)
        } else if (indentation != getIndentation(line)) {
            // Stop if the indentation level changes.
            i.previous()
            break
        }

        val keyValue = line.split(':', limit = 2).map { it.trim() }
        when (keyValue.size) {
            1 -> {
                // Handle lines without a colon.
                val nestedMap = parseKeyValue(i, keyPrefix + keyValue[0].replace(" ", "-") + "-")
                map += nestedMap
            }

            2 -> {
                // Handle lines with a colon.
                val key = (keyPrefix + keyValue[0]).lowercase()

                val valueLines = mutableListOf<String>()

                var isBlock = false
                if (keyValue[1].isNotEmpty()) {
                    if (keyValue[1] == "{") {
                        // Support multi-line values that use curly braces instead of indentation.
                        isBlock = true
                    } else {
                        valueLines += keyValue[1]
                    }
                }

                // Parse a multi-line value.
                while (i.hasNext()) {
                    var indentedLine = i.next()

                    if (isBlock) {
                        if (indentedLine == "}") {
                            // Stop if a block closes.
                            break
                        }
                    } else {
                        if (indentedLine.isNotBlank() && getIndentation(indentedLine) <= indentation) {
                            // Stop if the indentation level does not increase.
                            i.previous()
                            break
                        }
                    }

                    indentedLine = indentedLine.trim()

                    // Within a multi-line value, lines with only a dot mark empty lines.
                    if (indentedLine == ".") {
                        if (valueLines.isNotEmpty()) valueLines += ""
                    } else {
                        valueLines += indentedLine
                    }
                }

                val trimmedValueLines = valueLines.dropWhile { it.isBlank() }.dropLastWhile { it.isBlank() }
                map[key] = trimmedValueLines.joinToString("\n")
            }
        }
    }

    return map
}

// TODO: Consider replacing this with a Haskell helper script that calls "readGenericPackageDescription" and dumps
//       it as JSON to the console.
private fun parseCabalFile(cabal: String, identifierType: String): Package {
    // For an example file see
    // https://hackage.haskell.org/package/transformers-compat-0.5.1.4/src/transformers-compat.cabal
    val map = parseKeyValue(cabal.lines().listIterator())

    val id = Identifier(
        type = identifierType,
        namespace = map["category"].orEmpty(),
        name = map["name"].orEmpty(),
        version = map["version"].orEmpty()
    )

    val artifact = RemoteArtifact.EMPTY.copy(
        url = "${getPackageUrl(id.name, id.version)}/${id.name}-${id.version}.tar.gz"
    )

    val vcs = VcsInfo(
        type = VcsType.forName((map["source-repository-this-type"] ?: map["source-repository-head-type"]).orEmpty()),
        url = (map["source-repository-this-location"] ?: map["source-repository-head-location"]).orEmpty(),
        path = (map["source-repository-this-subdir"] ?: map["source-repository-head-subdir"]).orEmpty(),
        revision = map["source-repository-this-tag"].orEmpty()
    )

    val homepageUrl = map["homepage"].orEmpty()

    return Package(
        id = id,
        authors = parseAuthorString(map["author"]).mapNotNullTo(mutableSetOf()) { it.name },
        declaredLicenses = setOfNotNull(map["license"]),
        description = map["description"].orEmpty(),
        homepageUrl = homepageUrl,
        binaryArtifact = RemoteArtifact.EMPTY,
        sourceArtifact = artifact,
        vcs = vcs,
        vcsProcessed = processPackageVcs(vcs, homepageUrl)
    )
}

private fun getPackageUrl(name: String, version: String) = "https://hackage.haskell.org/package/$name-$version"

private fun OkHttpClient.downloadCabalFile(pkgId: Identifier): String? {
    val url = "${getPackageUrl(pkgId.name, pkgId.version)}/src/${pkgId.name}.cabal"

    return okHttpClient.downloadText(url).onFailure {
        logger.warn { "Unable to retrieve Hackage metadata for package '${pkgId.toCoordinates()}'." }
    }.getOrNull()
}
