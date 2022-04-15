/*
 * Copyright (C) 2017-2020 HERE Europe B.V.
 * Copyright (C) 2021 TNG Technology Consulting GmbH
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

package org.ossreviewtoolkit.reporter.reporters

import com.fasterxml.jackson.annotation.JsonInclude.Include
import com.fasterxml.jackson.databind.json.JsonMapper

import java.io.File
import java.io.FileOutputStream
import java.time.LocalDateTime
import java.util.SortedMap
import java.util.SortedSet
import java.util.UUID
import java.util.zip.Deflater

import kotlin.math.min

import org.apache.commons.compress.compressors.gzip.GzipCompressorOutputStream
import org.apache.commons.compress.compressors.gzip.GzipParameters

import org.ossreviewtoolkit.downloader.VcsHost
import org.ossreviewtoolkit.model.CuratedPackage
import org.ossreviewtoolkit.model.Identifier
import org.ossreviewtoolkit.model.OrtIssue
import org.ossreviewtoolkit.model.OrtResult
import org.ossreviewtoolkit.model.Package
import org.ossreviewtoolkit.model.PackageReference
import org.ossreviewtoolkit.model.Project
import org.ossreviewtoolkit.model.ScanResult
import org.ossreviewtoolkit.model.Scope
import org.ossreviewtoolkit.model.VcsInfo
import org.ossreviewtoolkit.model.utils.getPurlType
import org.ossreviewtoolkit.model.utils.toPurl
import org.ossreviewtoolkit.reporter.Reporter
import org.ossreviewtoolkit.reporter.ReporterInput
import org.ossreviewtoolkit.reporter.reporters.OpossumReporter.OpossumInput
import org.ossreviewtoolkit.utils.core.log
import org.ossreviewtoolkit.utils.spdx.SpdxExpression
import org.ossreviewtoolkit.utils.spdx.SpdxLicense
import org.ossreviewtoolkit.utils.spdx.getLicenseText

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
 * This reporter supports the following options:
 * - *scanner.maxDepth*: The depth to which the full file level scanner information is added
 * - *scopes.excluded*: Comma separated list of scopes that are excluded
 */
class OpossumReporter : Reporter {
    companion object {
        const val OPTION_SCANNER_MAX_DEPTH = "scanner.maxDepth"
        const val OPTION_EXCLUDED_SCOPES = "scopes.excluded"
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
            val pathPieces = path.split("/")
                .filter { it.isNotEmpty() }
            addResource(pathPieces)
        }

        fun isFile() = tree.isEmpty()

        fun isPathAFile(path: String): Boolean {
            val pathPieces = path.split("/")
                .filter { it.isNotEmpty() }
            return isPathAFile(pathPieces)
        }

        private fun isPathAFile(pathPieces: List<String>): Boolean {
            if (pathPieces.isEmpty()) return isFile()

            val head = pathPieces.first()
            val tail = pathPieces.drop(1)

            if (head !in tree) return true
            return tree.getValue(head).isPathAFile(tail)
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
                "frequentLicenses" to frequentLicenses.toList().map { it.toJson() },
                "baseUrlsForSources" to baseUrlsForSources,
                "externalAttributionSources" to externalAttributionSources
            )

        fun getSignalsForFile(file: String): List<OpossumSignal> =
            pathToSignal[file]
                ?.mapNotNull { uuid -> signals.find { it.uuid == uuid } }
                .orEmpty()

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
                log.debug { "add signal ${signal.id} of source ${signal.source} to $path" }
                resources.addResource(path)
                pathToSignal.getOrPut(resolvePath(path)) { sortedSetOf() } += uuidOfSignal
            }
        }

        private fun signalFromPkg(pkg: Package, id: Identifier = pkg.id): OpossumSignal {
            val source = addExternalAttributionSource("ORT-Package", "ORT-Package", 180)
            return OpossumSignal(
                source,
                id = id,
                url = pkg.homepageUrl,
                license = pkg.concludedLicense ?: pkg.declaredLicensesProcessed.spdxExpression,
                preselected = true
            )
        }

        private fun addDependency(
            dependency: PackageReference,
            curatedPackages: SortedSet<CuratedPackage>,
            relRoot: String,
            level: Int = 1
        ) {
            val dependencyId = dependency.id
            log.debug { "$relRoot - ${dependencyId.toCoordinates()} - Dependency" }
            val dependencyPath =
                resolvePath(listOf(relRoot, dependencyId.namespace, "${dependencyId.name}@${dependencyId.version}"))

            val dependencyPackage = curatedPackages
                .find { curatedPackage -> curatedPackage.pkg.id == dependencyId }
                ?.pkg ?: Package.EMPTY

            addPackageRoot(dependencyId, dependencyPath, level, dependencyPackage.vcsProcessed)

            addSignal(signalFromPkg(dependencyPackage, dependencyId), sortedSetOf(dependencyPath))

            if (dependency.dependencies.isNotEmpty()) {
                val rootForDependencies = resolvePath(dependencyPath, "dependencies")
                addAttributionBreakpoint(rootForDependencies)
                dependency.dependencies.map { addDependency(it, curatedPackages, rootForDependencies, level + 1) }
            }
        }

        private fun addDependencyScope(
            scope: Scope,
            curatedPackages: SortedSet<CuratedPackage>,
            relRoot: String = "/"
        ) {
            val name = scope.name
            log.debug { "$relRoot - $name - DependencyScope" }

            val rootForScope = resolvePath(relRoot, name)
            if (scope.dependencies.isNotEmpty()) addAttributionBreakpoint(rootForScope)
            scope.dependencies.forEachIndexed { index, dependency ->
                log.debug { "scope -> dependency ${index + 1} of ${scope.dependencies.size}" }
                addDependency(dependency, curatedPackages, rootForScope)
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
            curatedPackages: SortedSet<CuratedPackage>,
            excludedScopes: Set<String>,
            relRoot: String = "/"
        ) {
            val projectId = project.id
            val definitionFilePath = resolvePath(relRoot, project.definitionFilePath)
            log.debug { "$definitionFilePath - $projectId - Project" }
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

            project.scopes
                .filterNot { it.name in excludedScopes }
                .forEachIndexed { index, scope ->
                    log.debug { "analyzerResultProject -> scope ${index + 1} of ${project.scopes.size}" }
                    addDependencyScope(scope, curatedPackages, definitionFilePath)
                }
        }

        private fun addScannerResult(id: Identifier, result: ScanResult, maxDepth: Int) {
            val scanner = "${result.scanner.name}@${result.scanner.version}"
            val roots = packageToRoot[id]
            if (roots == null) {
                log.info { "No root for $id from $scanner" }
                return
            }

            log.debug { "add scanner results for $id from $scanner to ${roots.size} roots" }

            val licenseFindings = result.summary.licenseFindings
            val copyrightFindings = result.summary.copyrightFindings

            val source = addExternalAttributionSource("ORT-Scanner-$scanner", "ORT-Scanner $scanner", 20)

            val rootsBelowMaxDepth = roots
                .filter { it.value <= maxDepth }
                .map { it.key }
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

            val rootsAboveMaxDepth = roots
                .filter { it.value > maxDepth }
                .map { it.key }
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

            val issueSource =
                addExternalAttributionSource("ORT-Scanner-Issue-$scanner", "ORT-Scanner Issue $scanner", 900)
            result.summary.issues.forEach { addIssue(it, id, issueSource) }
        }

        fun addScannerResults(id: Identifier, results: List<ScanResult>, maxDepth: Int) {
            results.forEach { addScannerResult(id, it, maxDepth) }
        }

        fun addIssue(issue: OrtIssue, id: Identifier, source: String) {
            val roots = packageToRoot[id]
            val paths = if (roots.isNullOrEmpty()) {
                log.info { "No root for $id" }
                mutableSetOf("/")
            } else {
                roots.keys
            }
            val signal =
                OpossumSignal(source, comment = issue.toString(), followUp = true, excludeFromNotice = true)
            addSignal(signal, paths.map { resolvePath(it) }.toSortedSet())
        }

        fun addPackagesThatAreRootless(analyzerResultPackages: SortedSet<CuratedPackage>) {
            val rootlessPackages = analyzerResultPackages.filter { packageToRoot[it.pkg.id] == null }

            rootlessPackages.forEach {
                val path = resolvePath("/lost+found", it.pkg.id.toPurl())
                addSignal(signalFromPkg(it.pkg), sortedSetOf(path))
                addPackageRoot(it.pkg.id, path, Int.MAX_VALUE, it.pkg.vcsProcessed)
            }

            if (rootlessPackages.isNotEmpty()) {
                log.warn { "There are ${rootlessPackages.size} packages that had no root." }
            }
        }
    }

    override val reporterName = "Opossum"

    private fun writeReport(outputFile: File, opossumInput: OpossumInput) {
        FileOutputStream(outputFile, /* append = */ false).use { outputStream ->
            val gzipParameters = GzipParameters().apply {
                compressionLevel = Deflater.BEST_COMPRESSION
            }
            GzipCompressorOutputStream(outputStream, gzipParameters).bufferedWriter().use { gzipWriter ->
                JsonMapper()
                    .setSerializationInclusion(Include.NON_NULL)
                    .writeValue(gzipWriter, opossumInput.toJson())
            }
        }
    }

    fun generateOpossumInput(
        ortResult: OrtResult,
        excludedScopes: Set<String>,
        maxDepth: Int = Int.MAX_VALUE
    ): OpossumInput {
        val opossumInput = OpossumInput()

        opossumInput.addBaseURL("/", ortResult.repository.vcs)

        SpdxLicense.values().forEach {
            val licenseText = getLicenseText(it.id)
            opossumInput.frequentLicenses += OpossumFrequentLicense(it.id, it.fullName, licenseText)
        }

        val analyzerResult = ortResult.analyzer?.result?.withResolvedScopes() ?: return opossumInput
        val analyzerResultProjects = analyzerResult.projects
        val analyzerResultPackages = analyzerResult.packages
        analyzerResultProjects.forEachIndexed { index, project ->
            log.debug { "analyzerResultProject ${index + 1} of ${analyzerResultProjects.size}" }
            opossumInput.addProject(project, analyzerResultPackages, excludedScopes)
        }
        if (excludedScopes.isEmpty()) {
            opossumInput.addPackagesThatAreRootless(analyzerResultPackages)
        }

        analyzerResult.issues.entries.forEach {
            val identifier = it.key
            val issues = it.value
            issues.forEach { issue ->
                val issueSource =
                    opossumInput.addExternalAttributionSource("ORT-Analyzer-Issues", "ORT-Analyzer Issues", 900)
                opossumInput.addIssue(issue, identifier, issueSource)
            }
        }

        val scannerResults = ortResult.scanner?.results?.scanResults ?: return OpossumInput()
        scannerResults.entries.forEachIndexed { index, entry ->
            log.debug { "scannerResult ${index + 1} of ${scannerResults.entries.size}" }
            opossumInput.addScannerResults(entry.key, entry.value, maxDepth)
        }

        return opossumInput
    }

    override fun generateReport(
        input: ReporterInput,
        outputDir: File,
        options: Map<String, String>
    ): List<File> {
        val maxDepth = options.getOrDefault(OPTION_SCANNER_MAX_DEPTH, "3").toInt()
        val excludedScopes = options.getOrDefault(OPTION_EXCLUDED_SCOPES, "devDependencies,test").split(",").toSet()
        val opossumInput = generateOpossumInput(input.ortResult, excludedScopes, maxDepth)

        val outputFile = outputDir.resolve("opossum.input.json.gz")
        writeReport(outputFile, opossumInput)
        return listOf(outputFile)
    }
}
