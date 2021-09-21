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

import org.apache.commons.compress.compressors.gzip.GzipCompressorOutputStream
import org.apache.commons.compress.compressors.gzip.GzipParameters

import org.ossreviewtoolkit.model.*
import org.ossreviewtoolkit.model.utils.getPurlType
import org.ossreviewtoolkit.model.utils.toPurl
import org.ossreviewtoolkit.reporter.Reporter
import org.ossreviewtoolkit.reporter.ReporterInput
import org.ossreviewtoolkit.spdx.*
import org.ossreviewtoolkit.utils.log
import java.io.File

import java.io.FileOutputStream

import java.time.LocalDateTime

import java.util.*
import java.util.zip.Deflater
import kotlin.math.min

fun pathResolve(left: String, right: String = "", is_dir_flag: Boolean? = null): String {
    val isDir = is_dir_flag == true || (is_dir_flag == null && (if (right != "") {
        right
    } else {
        left
    }).last() == '/')
    return "/${left}/${right}"
        .replace(
            Regex("/*$"), if (isDir) {
                "/"
            } else {
                ""
            }
        )
        .replace(Regex("/+"), "/")
        .ifEmpty { "/" }
}

fun pathResolve(pieces: List<String>): String {
    return pieces.reduce { right, left -> pathResolve(right, left) }
}

/**
 * A [Reporter] that generates an [Opossum Input].
 *
 * This reporter supports the following options:
 * - *output.file.formats*: The list of [FileFormat]s to generate, defaults to [FileFormat.JSON].
 */
class OpossumReporter : Reporter {
    companion object {
        const val OPTION_SCANNER_MAXDEPTH = "scanner.maxDepth"
        const val OPTION_EXCLUDED_SCOPES = "scopes.excluded"
        const val FOLLOW_UP = "FOLLOW_UP"
    }

    data class OpossumSignal(
        val source: String,
        val id: Identifier? = null,
        val url: String? = null,
        val license: SpdxExpression? = null,
        val copyright: String? = null,
        val comment: String? = null,
        val preselected: Boolean = false,
        val followUp: String? = null,
        val excludeFromNotice: Boolean = false,
        val uuid: UUID = UUID.randomUUID()
    ) {
        fun toJson(): Map<*, *> {
            return sortedMapOf(
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
                    "followUp" to followUp,
                    "excludeFromNotice" to excludeFromNotice,

                    "comment" to comment
                )
            )
        }

        fun matchesOther(other: OpossumSignal): Boolean =
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
        fun addResource(pathPieces: List<String>) {
            if (pathPieces.isEmpty()) {
                return
            }

            val head = pathPieces.first()
            val tail = pathPieces.drop(1)

            if (!tree.containsKey(head)) {
                tree[head] = OpossumResources()
            }
            tree[head]!!.addResource(tail)
        }

        fun addResource(path: String) {
            val pathPieces = path.split("/")
                .filter { it.isNotEmpty() }
            addResource(pathPieces)
        }

        fun isFile(): Boolean {
            return tree.isEmpty()
        }

        fun isPathAFile(path: String): Boolean {
            val pathPieces = path.split("/")
                .filter { it.isNotEmpty() }
            return isPathAFile(pathPieces)
        }

        private fun isPathAFile(pathPieces: List<String>): Boolean {
            if (pathPieces.isEmpty()) {
                return isFile()
            }

            val head = pathPieces.first()
            val tail = pathPieces.drop(1)

            if (!tree.containsKey(head)) {
                return true
            }
            return tree[head]!!.isPathAFile(tail)
        }

        fun toJson(): Map<*, *> {
            return tree.asSequence().associateBy(
                { it.key },
                {
                    if (it.value.isFile()) {
                        1
                    } else {
                        it.value.toJson()
                    }
                })
        }

        fun toFileList(): Set<String> {
            return tree.asSequence()
                .flatMap { e ->
                    e.value.toFileList()
                        .map { pathResolve(e.key, it, is_dir_flag = false) }
                }
                .plus("/")
                .toSet()
        }
    }

    data class OpossumFrequentLicense(
        var shortName: String,
        var fullName: String?,
        var defaultText: String?
    ) : Comparable<OpossumFrequentLicense> {
        fun toJson(): Map<*, *> {
            return sortedMapOf(
                "shortName" to shortName,
                "fullName" to fullName,
                "defaultText" to defaultText
            )
        }

        override fun compareTo(other: OpossumFrequentLicense) = compareValuesBy(this, other,
            { it.shortName },
            { it.fullName },
            { it.defaultText })
    }

    data class OpossumInput(
        var resources: OpossumResources = OpossumResources(),
        var signals: MutableList<OpossumSignal> = mutableListOf(),
        var pathToSignal: SortedMap<String, SortedSet<UUID>> = sortedMapOf(),
        var packageToRoot: SortedMap<Identifier, SortedMap<String, Int>> = sortedMapOf(),
        var attributionBreakpoints: SortedSet<String> = sortedSetOf(),
        var filesWithChildren: SortedSet<String> = sortedSetOf(),
        var frequentLicenses: SortedSet<OpossumFrequentLicense> = sortedSetOf(),
        var baseUrlsForSources: SortedMap<String, String> = sortedMapOf()
    ) {
        fun toJson(): Map<*, *> {
            return sortedMapOf(
                "metadata" to sortedMapOf(
                    "projectId" to "0",
                    "fileCreationDate" to LocalDateTime.now().toString()
                ),
                "resources" to resources.toJson(),
                "externalAttributions" to signals
                    .map { it.toJson() }
                    .asSequence()
                    .flatMap { it.asSequence() }
                    .associateBy({ it.key }, { it.value }),
                "resourcesToAttributions" to pathToSignal.mapKeys {
                    val trailingSlash = if (resources.isPathAFile(it.key)) {
                        ""
                    } else {
                        "/"
                    }
                    "${it.key}${trailingSlash}"
                },
                "attributionBreakpoints" to attributionBreakpoints,
                "filesWithChildren" to filesWithChildren,
                "frequentLicenses" to frequentLicenses.toList().map { it.toJson() },
                "baseUrlsForSources" to baseUrlsForSources
            )
        }

        fun getSignalsForFile(file: String): List<OpossumSignal> {
            return pathToSignal[file]
                ?.mapNotNull { uuid -> signals.find { it.uuid == uuid } }
                ?: emptyList()
        }

        fun addAttributionBreakpoint(breakpoint: String) {
            attributionBreakpoints.add(pathResolve(breakpoint, is_dir_flag = true))
            resources.addResource(breakpoint)
        }

        fun addFileWithChildren(fileWithChildren: String) {
            filesWithChildren.add(pathResolve(fileWithChildren, is_dir_flag = true))
        }

        fun addBaseURL(path: String, vcs: VcsInfo) {
            val idFromPath = pathResolve(path, is_dir_flag = true)

            if (baseUrlsForSources.containsKey(idFromPath)) {
                return
            }

            if (vcs.type == VcsType.GIT && (vcs.url.startsWith("https://github.com/") || vcs.url.startsWith("ssh://git@github.com/"))) {
                val revision = if (vcs.revision != VcsInfo.EMPTY.revision) {
                    vcs.revision
                } else {
                    "HEAD"
                }
                val baseUrl = vcs.url
                    .replace("ssh://git@github.com/", "https://github.com/")
                    .replace(Regex("\\.git$"), "")
                    .plus("/tree/${revision}/${vcs.path}/{path}")
                baseUrlsForSources[idFromPath] = baseUrl
            }

        }

        fun addPackageRoot(id: Identifier, path: String, level: Int = 0, vcs: VcsInfo = VcsInfo.EMPTY) {
            val mapOfId = packageToRoot.getOrPut(id) { sortedMapOf() }
            val oldLevel = mapOfId.getOrDefault(path, level)
            mapOfId[path] = min(level, oldLevel)
            resources.addResource(path)

            addBaseURL(path, vcs)
        }

        fun addSignal(signal: OpossumSignal, paths: SortedSet<String>) {
            if (paths.isEmpty()) {
                return
            }

            val matchingSignal = signals.find { it.matchesOther(signal) }
            val uuidOfSignal = if (matchingSignal == null) {
                signals.add(signal)
                signal.uuid
            } else {
                matchingSignal.uuid
            }

            paths.forEach {
                log.trace("add signal ${signal.id} of source ${signal.source} to ${it}")
                resources.addResource(it)
                val itAsID = pathResolve(it)
                if (pathToSignal.containsKey(itAsID)) {
                    pathToSignal[itAsID]!!.add(uuidOfSignal)
                } else {
                    pathToSignal[itAsID] = sortedSetOf(uuidOfSignal)
                }
            }
        }

        fun addSignal(signal: OpossumSignal, path: String) {
            addSignal(signal, sortedSetOf(path))
        }

        fun signalFromPkg(pkg: Package, id: Identifier = pkg.id): OpossumSignal {
            return OpossumSignal(
                "ORT-Package",
                id = id,
                url = pkg.homepageUrl,
                license = pkg.concludedLicense ?: pkg.declaredLicensesProcessed.spdxExpression,
                preselected = true
            )
        }

        fun addDependency(
            dependency: PackageReference,
            curatedPackages: SortedSet<CuratedPackage>,
            relRoot: String,
            level: Int = 1
        ) {
            val dependencyId = dependency.id
            log.debug("$relRoot - $dependencyId - Dependency")
            val dependencyPath =
                pathResolve(listOf(relRoot, dependencyId.namespace, "${dependencyId.name}@${dependencyId.version}"))

            val dependencyPackage = curatedPackages
                .find { curatedPackage -> curatedPackage.pkg.id == dependencyId }
                ?.pkg ?: Package.EMPTY

            addPackageRoot(dependencyId, dependencyPath, level, dependencyPackage.vcsProcessed)

            this.addSignal(signalFromPkg(dependencyPackage, dependencyId), dependencyPath)

            if (dependency.dependencies.isNotEmpty()) {
                val rootForDependencies = pathResolve(dependencyPath, "dependencies")
                addAttributionBreakpoint(rootForDependencies)
                dependency.dependencies.map { this.addDependency(it, curatedPackages, rootForDependencies, level + 1) }
            }
        }

        fun addDependencyScope(scope: Scope, curatedPackages: SortedSet<CuratedPackage>, relRoot: String = "/") {
            val name = scope.name
            log.debug("$relRoot - $name - DependencyScope")

            val rootForScope = pathResolve(relRoot, name)
            addAttributionBreakpoint(rootForScope)
            scope.dependencies.forEachIndexed { index, dependency ->
                log.debug("scope -> dependency ${index + 1} of ${scope.dependencies.size}")
                this.addDependency(dependency, curatedPackages, rootForScope)
            }
        }

        fun getRootForProject(project: Project, relRoot: String): String {
            val vcsPath = pathResolve(relRoot, project.vcs.path)
            val definitionFilePath = pathResolve(relRoot, project.definitionFilePath)
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
            val definitionFilePath = pathResolve(relRoot, project.definitionFilePath)
            log.debug("$definitionFilePath - $projectId - Project")
            val projectRoot = getRootForProject(project, relRoot)
            addPackageRoot(projectId, projectRoot, 0, project.toPackage().vcsProcessed)
            addFileWithChildren(definitionFilePath)

            val signalFromProject = OpossumSignal(
                "ORT-Project",
                id = projectId,
                url = project.homepageUrl,
                license = project.declaredLicensesProcessed.spdxExpression,
                copyright = project.authors.joinToString(separator = "\n"),
                preselected = true
            )

            addSignal(signalFromProject, definitionFilePath)

            project.scopes
                .filter { !excludedScopes.contains(it.name) }
                .forEachIndexed { index, scope ->
                    log.debug("analyzerResultProject -> scope ${index + 1} of ${project.scopes.size}")
                    this.addDependencyScope(scope, curatedPackages, definitionFilePath)
                }
        }

        private fun makeLostAndFoundPath(id: Identifier): String {
            return pathResolve("/lost+found", id.toPurl())
        }

        fun addScannerResult(id: Identifier, result: ScanResult, maxDepth: Int) {
            val scanner = "${result.scanner.name}@${result.scanner.version}"
            val roots = packageToRoot[id]
            if (roots == null) {
                log.info("No root for $id from $scanner")
                return
            }

            log.debug("add scanner results for $id from $scanner to ${roots.size} roots")

            val rootsBelowMaxDepth = roots
                .filter { it.value <= maxDepth }
                .map { it.key }
            val rootsAboveMaxDepth = roots
                .filter { it.value > maxDepth }
                .map { it.key }

            val licenseFindings = result.summary.licenseFindings
            val copyrightFindings = result.summary.copyrightFindings

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
                        .reduceRightOrNull { left, right ->
                            SpdxCompoundExpression(
                                left,
                                SpdxOperator.AND,
                                right
                            )
                        }

                    val pathSignal = OpossumSignal(
                        "ORT-Scanner-$scanner",
                        copyright = copyright,
                        license = license
                    )
                    this.addSignal(pathSignal,
                        rootsBelowMaxDepth.map { pathResolve(it, pathFromFinding) }
                            .toSortedSet())
                }
            }
            if (rootsAboveMaxDepth.isNotEmpty()) {
                val copyright = copyrightFindings
                    .distinct()
                    .joinToString(separator = "\n") { it.statement }
                val license = licenseFindings
                    .map { it.license }
                    .distinct()
                    .reduceRightOrNull { left, right ->
                        SpdxCompoundExpression(
                            left,
                            SpdxOperator.AND,
                            right
                        )
                    }
                val rootSignal = OpossumSignal(
                    "ORT-Scanner-$scanner",
                    copyright = copyright,
                    license = license
                )
                this.addSignal(rootSignal, rootsAboveMaxDepth.toSortedSet())
            }

            addIssues(
                result.summary.issues,
                "ORT-Scanner-Issue-$scanner"
            )
        }

        fun addScannerResults(id: Identifier, results: List<ScanResult>, maxDepth: Int) {
            results.forEach { this.addScannerResult(id, it, maxDepth) }
        }

        private fun addIssue(issue: OrtIssue, source: String) {
            val signal = OpossumSignal(source, comment = issue.toString(), followUp = FOLLOW_UP, excludeFromNotice = true)
            addSignal(signal, "/ortIssues/issues")
        }

        fun addIssues(issues: List<OrtIssue>, source: String) {
            resources.addResource("/ortIssues/issues")
            attributionBreakpoints.add("/ortIssues")
            issues.forEach { addIssue(it, source) }
        }

        fun addPackagesThatAreRootless(analyzerResultPackages: SortedSet<CuratedPackage>) {
            val numberOfRootlessPackages = analyzerResultPackages
                .filter { packageToRoot[it.pkg.id] == null }
                .map {
                    val path = makeLostAndFoundPath(it.pkg.id)
                    addSignal(signalFromPkg(it.pkg), path)
                    addPackageRoot(it.pkg.id, path, Int.MAX_VALUE, it.pkg.vcsProcessed)
                    it
                }.size
            if (numberOfRootlessPackages > 0) {
                log.warn("There are $numberOfRootlessPackages packages that had no root")
            }
        }

        fun addFrequentLicense(
            shortName: String,
            fullName: String? = null,
            defaultText: String?
        ) {
            frequentLicenses.add(OpossumFrequentLicense(shortName, fullName, defaultText))
        }
    }

    override val reporterName = "Opossum"

    fun writeReport(outputFile: File, opossumInput: OpossumInput) {
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
            opossumInput.addFrequentLicense(
                shortName = it.id,
                fullName = it.fullName,
                defaultText = licenseText
            )
        }

        val analyzerResult = ortResult.analyzer?.result?.withScopesResolved() ?: return opossumInput
        val analyzerResultProjects = analyzerResult.projects
        val analyzerResultPackages = analyzerResult.packages
        analyzerResultProjects.forEachIndexed { index, project ->
            log.debug("analyzerResultProject ${index + 1} of ${analyzerResultProjects.size}")
            opossumInput.addProject(project, analyzerResultPackages, excludedScopes)
        }
        if (excludedScopes.isEmpty()) {
            opossumInput.addPackagesThatAreRootless(analyzerResultPackages)
        }
        opossumInput.addIssues(analyzerResult.issues.values.flatten(), "ORT-Analyzer-Issues")

        val scannerResults = ortResult.scanner?.results?.scanResults ?: return OpossumInput()
        scannerResults.entries.forEachIndexed { index, entry ->
            log.debug("scannerResult ${index + 1} of ${scannerResults.entries.size}")
            opossumInput.addScannerResults(entry.key, entry.value, maxDepth)
        }


        // val advisorResult = ortResult.advisor?.result
        // val evaluatorResult = ortResult.evaluator?.result

        return opossumInput
    }

    override fun generateReport(
        input: ReporterInput,
        outputDir: File,
        options: Map<String, String>
    ): List<File> {

        val maxDepth = options.getOrDefault(OPTION_SCANNER_MAXDEPTH, "3").toInt()
        val excludedScopes = options.getOrDefault(OPTION_EXCLUDED_SCOPES, "devDependencies,test").split(",").toSet()
        val opossumInput = generateOpossumInput(input.ortResult, excludedScopes, maxDepth)

        val outputFile = outputDir.resolve("opossum.input.json.gz")
        writeReport(outputFile, opossumInput)
        return listOf(outputFile)
    }
}
