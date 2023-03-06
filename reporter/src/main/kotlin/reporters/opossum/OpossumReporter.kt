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

package org.ossreviewtoolkit.reporter.reporters.opossum

import com.fasterxml.jackson.annotation.JsonInclude.Include
import com.fasterxml.jackson.databind.json.JsonMapper

import java.io.File
import java.time.LocalDateTime
import java.util.SortedMap
import java.util.SortedSet
import java.util.UUID

import kotlin.math.min

import org.apache.logging.log4j.kotlin.Logging

import org.ossreviewtoolkit.downloader.VcsHost
import org.ossreviewtoolkit.model.CuratedPackage
import org.ossreviewtoolkit.model.DependencyNode
import org.ossreviewtoolkit.model.Identifier
import org.ossreviewtoolkit.model.Issue
import org.ossreviewtoolkit.model.OrtResult
import org.ossreviewtoolkit.model.Package
import org.ossreviewtoolkit.model.Project
import org.ossreviewtoolkit.model.ScanResult
import org.ossreviewtoolkit.model.VcsInfo
import org.ossreviewtoolkit.model.utils.getPurlType
import org.ossreviewtoolkit.model.utils.toPurl
import org.ossreviewtoolkit.reporter.Reporter
import org.ossreviewtoolkit.reporter.ReporterInput
import org.ossreviewtoolkit.utils.common.packZip
import org.ossreviewtoolkit.utils.ort.createOrtTempDir
import org.ossreviewtoolkit.utils.spdx.SpdxExpression
import org.ossreviewtoolkit.utils.spdx.SpdxLicense
import org.ossreviewtoolkit.utils.spdx.getLicenseText

private const val ISSUE_PRIORITY = 900

/**
 * A function that resolves, concatenates, and normalizes Unix style paths.
 *
 * This function takes up to two Unix style paths and normalizes the concatenation:
 * - the result ends on a trailing slash if the last path had a trailing slash
 *   or isDirectory flag is set to true
 * - the result always starts with a leading slash
 */
internal fun resolvePath(left: String, right: String = "", isDirectory: Boolean? = null): String {
    val isDir = isDirectory == true || (isDirectory == null && (if (right != "") right else left).last() == '/')
    return "/$left/$right"
        .replace(Regex("/\\./"), "/")
        .replace(Regex("/*$"), if (isDir) "/" else "")
        .replace(Regex("/+"), "/")
        .ifEmpty { "/" }
}

internal fun resolvePath(pieces: List<String>) = pieces.reduce { right, left -> resolvePath(right, left) }

/**
 * A [Reporter] that generates an [OpossumInput].
 *
 * This reporter supports the following option:
 * - *scanner.maxDepth*: The depth to which the full file level scanner information is added
 */
class OpossumReporter : Reporter {
    companion object : Logging {
        const val OPTION_SCANNER_MAX_DEPTH = "scanner.maxDepth"
    }

    data class OpossumSignal(
        val source: String,
        val id: Identifier? = null,
        val url: String? = null,
        val license: SpdxExpression? = null,
        val copyright: String? = null,
        val comment: String? = null,
        val preselected: Boolean = false,
        val followUp: Boolean = false,
        val excludeFromNotice: Boolean = false,
        val uuid: UUID = UUID.randomUUID()
    ) {
        fun toJson(): Map<*, *> = sortedMapOf(
            uuid.toString() to sortedMapOf(
                "source" to sortedMapOf(
                    "name" to source,
                    "documentConfidence" to 80
                ),
                "attributionConfidence" to 80,
                "packageType" to id?.getPurlType(),
                "packageNamespace" to id?.namespace,
                "packageName" to id?.name,
                "packageVersion" to id?.version,
                "copyright" to copyright,
                "licenseName" to license?.toString(),
                "url" to url,
                "preSelected" to preselected,
                "followUp" to "FOLLOW_UP".takeIf { followUp },
                "excludeFromNotice" to excludeFromNotice,
                "comment" to comment
            )
        )

        fun matches(other: OpossumSignal): Boolean =
            source == other.source
                    && id == other.id
                    && url == other.url
                    && license == other.license
                    && copyright == other.copyright
                    && comment == other.comment
                    && preselected == other.preselected
    }

    data class OpossumResources(
        val tree: MutableMap<String, OpossumResources> = mutableMapOf()
    ) {
        private fun addResource(pathPieces: List<String>) {
            if (pathPieces.isEmpty()) return

            val head = pathPieces.first()
            val tail = pathPieces.drop(1)

            if (head !in tree) tree[head] = OpossumResources()
            tree.getValue(head).addResource(tail)
        }

        fun addResource(path: String) {
            val pathPieces = path.split("/").filter { it.isNotEmpty() }

            addResource(pathPieces)
        }

        fun isFile() = tree.isEmpty()

        fun isPathAFile(path: String): Boolean {
            val pathPieces = path.split("/").filter { it.isNotEmpty() }

            return isPathAFile(pathPieces)
        }

        private fun isPathAFile(pathPieces: List<String>): Boolean {
            if (pathPieces.isEmpty()) return isFile()

            val head = pathPieces.first()
            val tail = pathPieces.drop(1)

            return head !in tree || tree.getValue(head).isPathAFile(tail)
        }

        fun toJson(): Map<*, *> = tree.mapValues { (_, v) -> if (v.isFile()) 1 else v.toJson() }

        fun toFileList(): Set<String> =
            tree.flatMapTo(mutableSetOf()) { (key, value) ->
                value.toFileList().map { resolvePath(key, it, isDirectory = false) }
            }.plus("/")
    }

    data class OpossumFrequentLicense(
        val shortName: String,
        val fullName: String?,
        val defaultText: String?
    ) : Comparable<OpossumFrequentLicense> {
        fun toJson(): Map<*, *> = sortedMapOf(
            "shortName" to shortName,
            "fullName" to fullName,
            "defaultText" to defaultText
        )

        override fun compareTo(other: OpossumFrequentLicense) =
            compareValuesBy(
                this,
                other,
                { it.shortName },
                { it.fullName },
                { it.defaultText }
            )
    }

    data class OpossumExternalAttributionSource(
        val name: String,
        val priority: Int,
    )

    data class OpossumInput(
        val resources: OpossumResources = OpossumResources(),
        val signals: MutableList<OpossumSignal> = mutableListOf(),
        val pathToSignal: SortedMap<String, SortedSet<UUID>> = sortedMapOf(),
        val packageToRoot: SortedMap<Identifier, SortedMap<String, Int>> = sortedMapOf(),
        val attributionBreakpoints: SortedSet<String> = sortedSetOf(),
        val filesWithChildren: SortedSet<String> = sortedSetOf(),
        val frequentLicenses: SortedSet<OpossumFrequentLicense> = sortedSetOf(),
        val baseUrlsForSources: SortedMap<String, String> = sortedMapOf(),
        val externalAttributionSources: SortedMap<String, OpossumExternalAttributionSource> = sortedMapOf()
    ) {
        companion object : Logging

        fun toJson(): Map<*, *> =
            sortedMapOf(
                "metadata" to sortedMapOf(
                    "projectId" to "0",
                    "fileCreationDate" to LocalDateTime.now().toString()
                ),
                "resources" to resources.toJson(),
                "externalAttributions" to signals
                    .map { it.toJson() }
                    .flatMap { it.toList() }
                    .toMap(),
                "resourcesToAttributions" to pathToSignal.mapKeys {
                    val trailingSlash = if (resources.isPathAFile(it.key)) "" else "/"
                    resolvePath("${it.key}$trailingSlash")
                },
                "attributionBreakpoints" to attributionBreakpoints,
                "filesWithChildren" to filesWithChildren,
                "frequentLicenses" to frequentLicenses.map { it.toJson() },
                "baseUrlsForSources" to baseUrlsForSources,
                "externalAttributionSources" to externalAttributionSources
            )

        fun getSignalsForFile(file: String): List<OpossumSignal> =
            pathToSignal[file].orEmpty().mapNotNull { uuid -> signals.find { it.uuid == uuid } }

        private fun addAttributionBreakpoint(breakpoint: String) {
            attributionBreakpoints += resolvePath(breakpoint, isDirectory = true)
        }

        private fun addFileWithChildren(fileWithChildren: String) {
            filesWithChildren += resolvePath(fileWithChildren, isDirectory = true)
        }

        fun addBaseURL(path: String, vcs: VcsInfo) {
            val idFromPath = resolvePath(path, isDirectory = true)

            if (idFromPath in baseUrlsForSources) return

            if (VcsHost.GITHUB.isApplicable(vcs)) {
                val revision = vcs.revision.takeIf { it.isNotBlank() } ?: "HEAD"
                val baseUrl = vcs.url
                    .replace("ssh://git@github.com/", "https://github.com/")
                    .replace(Regex("\\.git$"), "")
                    .plus("/tree/$revision/${vcs.path}/{path}")

                baseUrlsForSources[idFromPath] = baseUrl
            }
        }

        fun addExternalAttributionSource(key: String, name: String, priority: Int): String {
            if (key !in externalAttributionSources) {
                externalAttributionSources[key] = OpossumExternalAttributionSource(name, priority)
            }
            return key
        }

        private fun addPackageRoot(id: Identifier, path: String, level: Int = 0, vcs: VcsInfo = VcsInfo.EMPTY) {
            val mapOfId = packageToRoot.getOrPut(id) { sortedMapOf() }
            val oldLevel = mapOfId.getOrDefault(path, level)
            mapOfId[path] = min(level, oldLevel)

            resources.addResource(path)
            addBaseURL(path, vcs)
        }

        private fun addSignal(signal: OpossumSignal, paths: SortedSet<String>) {
            if (paths.isEmpty()) return

            val matchingSignal = signals.find { it.matches(signal) }

            val uuidOfSignal = if (matchingSignal == null) {
                signals += signal
                signal.uuid
            } else {
                matchingSignal.uuid
            }

            paths.forEach { path ->
                logger.debug { "add signal ${signal.id} of source ${signal.source} to $path" }
                resources.addResource(path)
                pathToSignal.getOrPut(resolvePath(path)) { sortedSetOf() } += uuidOfSignal
            }
        }

        private fun signalFromPkg(pkg: Package): OpossumSignal {
            val source = addExternalAttributionSource("ORT-Package", "ORT-Package", 180)

            return OpossumSignal(
                source,
                id = pkg.id,
                url = pkg.homepageUrl,
                license = pkg.concludedLicense ?: pkg.declaredLicensesProcessed.spdxExpression,
                preselected = true
            )
        }

        private fun addDependency(
            dependency: DependencyNode,
            ortResult: OrtResult,
            relRoot: String,
            level: Int = 1
        ) {
            val dependencyId = dependency.id

            logger.debug { "$relRoot - ${dependencyId.toCoordinates()} - Dependency" }

            val dependencyPath =
                resolvePath(listOf(relRoot, dependencyId.namespace, "${dependencyId.name}@${dependencyId.version}"))
            val dependencyPackage = ortResult.getPackage(dependencyId)?.metadata
                ?: Package.EMPTY.copy(id = dependencyId)

            addPackageRoot(dependencyId, dependencyPath, level, dependencyPackage.vcsProcessed)
            addSignal(signalFromPkg(dependencyPackage), sortedSetOf(dependencyPath))

            val dependencies = dependency.getDependencies()

            if (dependencies.isNotEmpty()) {
                val rootForDependencies = resolvePath(dependencyPath, "dependencies")
                addAttributionBreakpoint(rootForDependencies)
                dependencies.map { addDependency(it, ortResult, rootForDependencies, level + 1) }
            }
        }

        private fun getRootForProject(project: Project, relRoot: String): String {
            val vcsPath = resolvePath(relRoot, project.vcs.path)
            val definitionFilePath = resolvePath(relRoot, project.definitionFilePath)

            return if (definitionFilePath.startsWith(vcsPath)) {
                vcsPath
            } else {
                relRoot
            }
        }

        fun addProject(
            project: Project,
            ortResult: OrtResult,
            relRoot: String = "/"
        ) {
            val projectId = project.id
            val definitionFilePath = resolvePath(relRoot, project.definitionFilePath)
            logger.debug { "$definitionFilePath - $projectId - Project" }
            val projectRoot = getRootForProject(project, relRoot)

            addPackageRoot(projectId, projectRoot, 0, project.toPackage().vcsProcessed)
            addFileWithChildren(definitionFilePath)

            val source = addExternalAttributionSource("ORT-Project", "ORT-Project", 200)
            val signalFromProject = OpossumSignal(
                source,
                id = projectId,
                url = project.homepageUrl,
                license = project.declaredLicensesProcessed.spdxExpression,
                copyright = project.authors.joinToString(separator = "\n"),
                preselected = true
            )

            addSignal(signalFromProject, sortedSetOf(definitionFilePath))

            val scopeNames = ortResult.dependencyNavigator.scopeNames(project).filterNot {
                ortResult.getExcludes().isScopeExcluded(it)
            }

            scopeNames.forEach { scopeName ->
                logger.debug { "$definitionFilePath - $scopeName - DependencyScope" }

                val rootForScope = resolvePath(definitionFilePath, scopeName)
                val dependencies = ortResult.dependencyNavigator.directDependencies(project, scopeName).toList()

                if (dependencies.isNotEmpty()) addAttributionBreakpoint(rootForScope)

                dependencies.forEach { dependency ->
                    addDependency(dependency, ortResult, rootForScope)
                }
            }
        }

        private fun addScannerResult(id: Identifier, result: ScanResult, maxDepth: Int) {
            val scanner = "${result.scanner.name}@${result.scanner.version}"
            val roots = packageToRoot[id]

            if (roots == null) {
                logger.info { "No root for $id from $scanner" }
                return
            }

            logger.debug { "add scanner results for $id from $scanner to ${roots.size} roots" }

            val licenseFindings = result.summary.licenseFindings
            val copyrightFindings = result.summary.copyrightFindings
            val source = addExternalAttributionSource("ORT-Scanner-$scanner", "ORT-Scanner $scanner", 20)
            val rootsBelowMaxDepth = roots.filter { it.value <= maxDepth }.map { it.key }

            if (rootsBelowMaxDepth.isNotEmpty()) {
                val pathsFromFindings = licenseFindings
                    .map { it.location.path }
                    .union(copyrightFindings.map { it.location.path })

                pathsFromFindings.forEach { pathFromFinding ->
                    val copyright = copyrightFindings
                        .filter { it.location.path == pathFromFinding }
                        .distinct()
                        .joinToString(separator = "\n") { it.statement }

                    val license = licenseFindings
                        .filter { it.location.path == pathFromFinding }
                        .map { it.license }
                        .distinct()
                        .reduceRightOrNull { left, right -> left and right }

                    val pathSignal = OpossumSignal(
                        source,
                        copyright = copyright,
                        license = license
                    )

                    addSignal(
                        pathSignal,
                        rootsBelowMaxDepth.map { resolvePath(it, pathFromFinding) }.toSortedSet()
                    )
                }
            }

            val rootsAboveMaxDepth = roots.filter { it.value > maxDepth }.map { it.key }

            if (rootsAboveMaxDepth.isNotEmpty()) {
                val copyright = copyrightFindings
                    .distinct()
                    .joinToString(separator = "\n") { it.statement }

                val license = licenseFindings
                    .map { it.license }
                    .distinct()
                    .reduceRightOrNull { left, right -> left and right }

                val rootSignal = OpossumSignal(
                    source,
                    copyright = copyright,
                    license = license
                )
                addSignal(rootSignal, rootsAboveMaxDepth.toSortedSet())
            }

            val issueSource = addExternalAttributionSource(
                key = "ORT-Scanner-Issue-$scanner",
                name = "ORT-Scanner Issue $scanner",
                priority = ISSUE_PRIORITY
            )

            result.summary.issues.forEach { addIssue(it, id, issueSource) }
        }

        fun addScannerResults(id: Identifier, results: List<ScanResult>, maxDepth: Int) {
            results.forEach { addScannerResult(id, it, maxDepth) }
        }

        fun addIssue(issue: Issue, id: Identifier, source: String) {
            val roots = packageToRoot[id]

            val paths = if (roots.isNullOrEmpty()) {
                logger.info { "No root for $id" }
                mutableSetOf("/")
            } else {
                roots.keys
            }

            val signal =
                OpossumSignal(source, comment = issue.toString(), followUp = true, excludeFromNotice = true)
            addSignal(signal, paths.map { resolvePath(it) }.toSortedSet())
        }

        fun addPackagesThatAreRootless(analyzerResultPackages: Set<CuratedPackage>) {
            val rootlessPackages = analyzerResultPackages.filter { packageToRoot[it.metadata.id] == null }

            rootlessPackages.forEach {
                val path = resolvePath("/lost+found", it.metadata.id.toPurl())
                addSignal(signalFromPkg(it.metadata), sortedSetOf(path))
                addPackageRoot(it.metadata.id, path, Int.MAX_VALUE, it.metadata.vcsProcessed)
            }

            if (rootlessPackages.isNotEmpty()) {
                logger.warn { "There are ${rootlessPackages.size} packages that had no root." }
            }
        }
    }

    override val type = "Opossum"

    private fun writeReport(outputFile: File, opossumInput: OpossumInput) {
        val jsonFile = createOrtTempDir().resolve("input.json")
        JsonMapper().setSerializationInclusion(Include.NON_NULL).writeValue(jsonFile, opossumInput.toJson())
        jsonFile.packZip(outputFile)
        jsonFile.delete()
    }

    fun generateOpossumInput(
        ortResult: OrtResult,
        maxDepth: Int = Int.MAX_VALUE
    ): OpossumInput {
        val opossumInput = OpossumInput()

        opossumInput.addBaseURL("/", ortResult.repository.vcs)

        SpdxLicense.values().forEach {
            val licenseText = getLicenseText(it.id)
            opossumInput.frequentLicenses += OpossumFrequentLicense(it.id, it.fullName, licenseText)
        }

        ortResult.getProjects().forEach { project ->
            opossumInput.addProject(project, ortResult)
        }

        if (ortResult.getExcludes().scopes.isEmpty()) {
            opossumInput.addPackagesThatAreRootless(ortResult.getPackages())
        }

        ortResult.analyzer?.result?.issues.orEmpty().forEach { (id, issues) ->
            issues.forEach { issue ->
                val source = opossumInput.addExternalAttributionSource(
                    key = "ORT-Analyzer-Issues",
                    name = "ORT-Analyzer Issues",
                    priority = ISSUE_PRIORITY
                )

                opossumInput.addIssue(issue, id, source)
            }
        }

        ortResult.scanner?.scanResults.orEmpty().forEach { (id, results) ->
            opossumInput.addScannerResults(id, results, maxDepth)
        }

        return opossumInput
    }

    override fun generateReport(
        input: ReporterInput,
        outputDir: File,
        options: Map<String, String>
    ): List<File> {
        val maxDepth = options.getOrDefault(OPTION_SCANNER_MAX_DEPTH, "3").toInt()
        val opossumInput = generateOpossumInput(input.ortResult, maxDepth)
        val outputFile = outputDir.resolve("report.opossum")

        writeReport(outputFile, opossumInput)

        return listOf(outputFile)
    }
}

private fun DependencyNode.getDependencies(): List<DependencyNode> =
    buildList {
        visitDependencies { dependencyNodes ->
            this += dependencyNodes.map { it.getStableReference() }
        }
    }
