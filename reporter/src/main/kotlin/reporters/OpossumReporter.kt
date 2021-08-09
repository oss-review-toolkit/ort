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
import org.ossreviewtoolkit.model.*
import org.ossreviewtoolkit.model.utils.getPurlType
import org.ossreviewtoolkit.model.utils.toPurl
import org.ossreviewtoolkit.reporter.Reporter
import org.ossreviewtoolkit.reporter.ReporterInput
import org.ossreviewtoolkit.spdx.SpdxCompoundExpression
import org.ossreviewtoolkit.spdx.SpdxExpression
import org.ossreviewtoolkit.spdx.SpdxOperator
import org.ossreviewtoolkit.utils.log
import java.io.File
import java.time.LocalDateTime
import java.util.*

/**
 * A [Reporter] that generates an [Opossum Input].
 *
 * This reporter supports the following options:
 * - *output.file.formats*: The list of [FileFormat]s to generate, defaults to [FileFormat.JSON].
 */
class OpossumReporter : Reporter {

    data class OpossumSignal (
        val source: String,
        val id: Identifier? = null,
        val url: String? = null,
        val license: SpdxExpression? = null,
        val copyright: String? = null,
        val comment: String? = null,
        val preselected: Boolean = false,
        val uuid: UUID = UUID.randomUUID()
    ) {
        fun toJson(): Map<*,*> {
            return sortedMapOf(
                uuid.toString() to sortedMapOf(
                    "source" to sortedMapOf(
                        "name" to source,
                        "documentConfidence" to 80
                    ),

                    "preSelected" to false,

                    "packageType" to id?.getPurlType(),
                    "packageNamespace" to id?.namespace,
                    "packageName" to id?.name,
                    "packageVersion" to id?.version,

                    "copyright" to copyright,
                    "licenseName" to license?.toString(),

                    "preSelected" to preselected,

                    "comment" to comment
                )
            )
        }

        fun matchesOther(other: OpossumSignal): Boolean =
            source == other.source
                    && id != null && id == other.id
                    && url == other.url
                    && license == other.license
                    && copyright == other.copyright
                    && comment == other.comment
                    && preselected == other.preselected
    }

    data class OpossumResources(
        val tree: MutableMap<String,OpossumResources> = mutableMapOf()
    ) {
        fun addResource(pathPieces: List<String>) {
            if (pathPieces.isEmpty()) {
                return
            }

            val head = pathPieces.first()
            val tail = pathPieces.drop(1)

            if (! tree.containsKey(head)) {
                tree[head] = OpossumResources()
            }
            tree[head]!!.addResource(tail)
        }

        fun addResource(path: File) {
            val pathPieces = path.toString().split("/")
            addResource(pathPieces)
        }

        fun isFile(): Boolean {
            return tree.isEmpty()
        }

        fun isContainedAFile(path: File) : Boolean {
            val pathPieces = path.toString().split("/")
            return isContainedAFile(pathPieces)
        }

        private fun isContainedAFile(pathPieces: List<String>) : Boolean {
            if (pathPieces.isEmpty()) {
                return isFile()
            }

            val head = pathPieces.first()
            val tail = pathPieces.drop(1)

            if (! tree.containsKey(head)) {
                return true
            }
            return tree[head]!!.isContainedAFile(tail)
        }

        fun toJson(): Map<*,*> {
            return tree.asSequence().associateBy (
                {it.key},
                {
                    if (it.value.isFile()) {
                        1
                    } else {
                        it.value.toJson()
                    }
                })
        }
    }

    data class OpossumInput(
        var resources: OpossumResources = OpossumResources(),
        var signals: MutableList<OpossumSignal> = mutableListOf(),
        var pathToSignal: SortedMap<File, SortedSet<UUID>> = sortedMapOf(),
        var packageToRoot: SortedMap<Identifier, SortedSet<File>> = sortedMapOf()
    ) {
        fun toJson(): Map<*,*> {
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
                    .associateBy ( {it.key}, {it.value} ),
                "resourcesToAttributions" to pathToSignal.mapKeys {
                    val trailingSlash = if (resources.isContainedAFile(it.key)) { "" } else { "/" }
                    "/${it.key}${trailingSlash}"
                }
            )
        }

        fun addPackageRoot(id: Identifier, path: File) {
            if (packageToRoot.containsKey(id)) {
                packageToRoot.get(id)?.add(path)
            } else {
                packageToRoot.put(id, sortedSetOf(path))
            }
        }

        fun addSignal(signal: OpossumSignal, paths: SortedSet<File>) {
            val matchingSignal = signals.find { it.matchesOther(signal) }
            val uuidOfSignal = if (matchingSignal == null) {
                signals.add(signal)
                signal.uuid
            } else {
                matchingSignal.uuid
            }

            paths.forEach {
                log.debug("add signal ${signal.id} to ${it}")
                resources.addResource(it)
                if (pathToSignal.containsKey(it)) {
                    pathToSignal.get(it)?.add(uuidOfSignal)
                } else {
                    pathToSignal.put(it, sortedSetOf(uuidOfSignal))
                }
            }
        }

        fun addSignal(signal: OpossumSignal, path: File) {
            addSignal(signal, sortedSetOf(path))
        }

        fun addDependencyScope(scope: Scope, curatedPackages: SortedSet<CuratedPackage>, relRoot: File = File("")) {
            val name = scope.name
            log.debug("add dependency scope ${name}")
            val dependencies = scope.dependencies

            val rootForScope = relRoot.resolve(name)

            dependencies.map {
                val dependencyId = it.id
                log.debug("add dependency ${dependencyId}")
                val dependencyPath = rootForScope
                    .resolve(dependencyId.namespace)
                    .resolve("${dependencyId.name}@${dependencyId.version}")
                addPackageRoot(dependencyId, dependencyPath)

                val dependencyPackage = curatedPackages
                    .find { curatedPackage -> curatedPackage.pkg.id == dependencyId }
                    ?.pkg ?: Package.EMPTY

                val signalFromDependency = OpossumSignal(
                    "ORT-Dependency",
                    id = dependencyId,
                    url = dependencyPackage.homepageUrl,
                    preselected = true
                )
                this.addSignal(signalFromDependency, dependencyPath)
            }
        }

        fun addProject(project: Project, curatedPackages: SortedSet<CuratedPackage>, relRoot: File = File("")) {
            log.debug("add project ${project.id}")
            val definitionFilePath = relRoot.resolve(project.definitionFilePath)
            val projectId = project.id
            // addPackageRoot(projectId, definitionFilePath)
            addPackageRoot(projectId, File(""))

            val authors = project.authors
            val copyright = authors.joinToString(separator = "\n")
            val declaredLicensesProcessed = project.declaredLicensesProcessed
            val homepageUrl = project.homepageUrl

            val signalFromProject = OpossumSignal(
                "ORT-Project",
                id = projectId,
                url = homepageUrl,
                license = declaredLicensesProcessed.spdxExpression,
                copyright = copyright,
                preselected = true
            )

            addSignal(signalFromProject, definitionFilePath)

            project.scopes.forEach { this.addDependencyScope(it, curatedPackages, definitionFilePath) }
        }

        fun addScannerResult(id: Identifier, result: ScanResult) {
            val roots = packageToRoot.get(id) ?: sortedSetOf(File("lost+found/${id.toPurl()}"))
            val scanner = "${result.scanner.name}@${result.scanner.version}"
            log.debug("add scanner results from ${scanner}")

            val licenseFindings = result.summary.licenseFindings
            val copyrightFindings = result.summary.copyrightFindings

            val pathsFromFindings = licenseFindings
                .map { it.location.path }
                .union( copyrightFindings.map { it.location.path } )

            pathsFromFindings.forEach {pathFromFinding ->
                val licenseFindingsForPath = licenseFindings.filter { it.location.path == pathFromFinding }
                val copyrightFindingsForPath = copyrightFindings.filter { it.location.path == pathFromFinding }

                val copyright = copyrightFindingsForPath.map { it.statement }
                    .joinToString(separator = "\n")
                val license = licenseFindingsForPath
                    .map { it.license }
                    .reduceRightOrNull { left, right ->
                        SpdxCompoundExpression(
                            left,
                            SpdxOperator.AND,
                            right
                        )
                    }

                val pathSignal = OpossumSignal(
                    "ORT-Scanner-${scanner}",
                    copyright = copyright,
                    license = license
                )
                roots.forEach { root ->
                    this.addSignal(pathSignal, sortedSetOf(root.resolve(pathFromFinding)))
                }
            }
        }

        fun addScannerResults(id: Identifier, results: List<ScanResult>) {
            results.forEach{ this.addScannerResult(id, it) }
        }
    }

    override val reporterName = "Opossum"

    override fun generateReport(
        input: ReporterInput,
        outputDir: File,
        options: Map<String, String>
    ): List<File> {
        val ortResult = input.ortResult

        val analyzerResult = ortResult.analyzer?.result
        val scannerResults = ortResult.scanner?.results?.scanResults
        // val advisorResult = ortResult.advisor?.result
        // val evaluatorResult = ortResult.evaluator?.result

        val analyzerResultProjects = analyzerResult?.projects ?: sortedSetOf()
        val analyzerResultPackages = analyzerResult?.packages ?: sortedSetOf()
        // val analyzerResultDependencyGraphs = analyzerResult?.dependencyGraphs ?: sortedMapOf()

        val collector = OpossumInput()

        analyzerResultProjects.forEach { collector.addProject(it, analyzerResultPackages) }
        scannerResults?.forEach { entry -> collector.addScannerResults(entry.key, entry.value) }

        val outputFile = outputDir.resolve("opossum.input.json")
        outputFile.bufferedWriter().use {
            JsonMapper()
                .setSerializationInclusion(Include.NON_NULL)
                .writeValue(it,collector.toJson())
        }
        return listOf(outputFile)
    }
}

