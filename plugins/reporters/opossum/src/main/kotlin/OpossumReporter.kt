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

package org.ossreviewtoolkit.plugins.reporters.opossum

import java.io.File
import java.util.UUID

import kotlin.math.min

import org.apache.logging.log4j.kotlin.logger

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
import org.ossreviewtoolkit.model.utils.toPurl
import org.ossreviewtoolkit.plugins.api.OrtPlugin
import org.ossreviewtoolkit.plugins.api.OrtPluginOption
import org.ossreviewtoolkit.plugins.api.PluginDescriptor
import org.ossreviewtoolkit.plugins.reporters.opossum.OpossumSignalFlat.OpossumSignal
import org.ossreviewtoolkit.reporter.Reporter
import org.ossreviewtoolkit.reporter.ReporterFactory
import org.ossreviewtoolkit.reporter.ReporterInput
import org.ossreviewtoolkit.utils.common.div
import org.ossreviewtoolkit.utils.common.packZip
import org.ossreviewtoolkit.utils.ort.createOrtTempDir
import org.ossreviewtoolkit.utils.spdx.SpdxLicense
import org.ossreviewtoolkit.utils.spdx.toExpression

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

data class OpossumReporterConfig(
    /**
     * The depth to which the full file level scanner information is added.
     */
    @OrtPluginOption(defaultValue = "3")
    val maxDepth: Int
)

/**
 * A [Reporter] that generates an [OpossumInput].
 */
@OrtPlugin(
    displayName = "Opossum",
    description = "Generates a report in the Opossum format.",
    factory = ReporterFactory::class
)
class OpossumReporter(
    override val descriptor: PluginDescriptor = OpossumReporterFactory.descriptor,
    private val config: OpossumReporterConfig
) : Reporter {
    override fun generateReport(input: ReporterInput, outputDir: File): List<Result<File>> {
        val reportFileResult = runCatching {
            val opossumInput = createOpossumInput(input, config.maxDepth)

            outputDir.resolve("report.opossum").also {
                writeReport(it, opossumInput)
            }
        }

        return listOf(reportFileResult)
    }

    internal fun createOpossumInput(input: ReporterInput, maxDepth: Int = Int.MAX_VALUE): OpossumInput =
        OpossumInputCreator().create(input, maxDepth)

    private fun writeReport(outputFile: File, opossumInput: OpossumInput) {
        val inputJson = createOrtTempDir() / "input.json"

        inputJson.writeReport(opossumInput)

        inputJson.packZip(outputFile)
        inputJson.delete()
    }

    class OpossumInputCreator {
        private val resources: OpossumResources = OpossumResources()
        private val uuidToSignals: MutableMap<UUID, OpossumSignal> = mutableMapOf()
        private val pathToSignals: MutableMap<String, MutableSet<UUID>> = mutableMapOf()
        private val packageToRoot: MutableMap<Identifier, MutableMap<String, Int>> = mutableMapOf()
        private val attributionBreakpoints: MutableSet<String> = mutableSetOf()
        private val filesWithChildren: MutableSet<String> = mutableSetOf()
        private val frequentLicenses: MutableSet<OpossumFrequentLicense> = mutableSetOf()
        private val baseUrlsForSources: MutableMap<String, String> = mutableMapOf()
        private val externalAttributionSources: MutableMap<String, OpossumExternalAttributionSource> = mutableMapOf()

        internal fun create(input: ReporterInput, maxDepth: Int = Int.MAX_VALUE): OpossumInput {
            addBaseUrl("/", input.ortResult.repository.vcs)

            SpdxLicense.entries.forEach {
                val licenseText = input.licenseFactProvider.getLicenseText(it.id)
                frequentLicenses += OpossumFrequentLicense(it.id, it.fullName, licenseText)
            }

            input.ortResult.getProjects().forEach { project ->
                addProject(project, input.ortResult)
            }

            if (input.ortResult.getExcludes().scopes.isEmpty()) {
                addPackagesThatAreRootless(input.ortResult.getPackages())
            }

            input.ortResult.analyzer?.result?.issues.orEmpty().forEach { (id, issues) ->
                issues.forEach { issue ->
                    val source = addExternalAttributionSource(
                        key = "ORT-Analyzer-Issues",
                        name = "ORT-Analyzer Issues",
                        priority = ISSUE_PRIORITY
                    )

                    addIssue(issue, id, source)
                }
            }

            input.ortResult.getScanResults().forEach { (id, results) ->
                addScannerResults(id, results, maxDepth)
            }

            val externalAttributions = uuidToSignals.mapValues { OpossumSignalFlat.create(it.value) }
            val resourcesToAttributions = pathToSignals.mapKeys {
                val trailingSlash = if (resources.isPathAFile(it.key)) "" else "/"
                resolvePath("${it.key}$trailingSlash")
            }

            return OpossumInput(
                resources = resources,
                externalAttributions = externalAttributions,
                resourcesToAttributions = resourcesToAttributions,
                attributionBreakpoints = attributionBreakpoints,
                filesWithChildren = filesWithChildren,
                baseUrlsForSources = baseUrlsForSources,
                externalAttributionSources = externalAttributionSources,
                frequentLicenses = frequentLicenses.toSortedSet(compareBy { it.shortName })
            )
        }

        private fun addAttributionBreakpoint(breakpoint: String) {
            attributionBreakpoints += resolvePath(breakpoint, isDirectory = true)
        }

        private fun addFileWithChildren(fileWithChildren: String) {
            filesWithChildren += resolvePath(fileWithChildren, isDirectory = true)
        }

        private fun addBaseUrl(path: String, vcs: VcsInfo) {
            val idFromPath = resolvePath(path, isDirectory = true)

            if (idFromPath in baseUrlsForSources) return

            if (VcsHost.GITHUB.isApplicable(vcs)) {
                val revision = vcs.revision.ifBlank { "HEAD" }
                val baseUrl = vcs.url
                    .replace("ssh://git@github.com/", "https://github.com/")
                    .replace(Regex("\\.git$"), "")
                    .plus("/tree/$revision/${vcs.path}/{path}")

                baseUrlsForSources[idFromPath] = baseUrl
            }
        }

        private fun addExternalAttributionSource(key: String, name: String, priority: Int): String {
            if (key !in externalAttributionSources) {
                externalAttributionSources[key] = OpossumExternalAttributionSource(name, priority)
            }

            return key
        }

        private fun addPackageRoot(id: Identifier, path: String, level: Int, vcs: VcsInfo) {
            val mapOfId = packageToRoot.getOrPut(id) { mutableMapOf() }
            val oldLevel = mapOfId.getOrDefault(path, level)
            mapOfId[path] = min(level, oldLevel)

            resources.addResource(path)
            addBaseUrl(path, vcs)
        }

        private fun addSignal(signal: OpossumSignal, paths: Set<String>) {
            if (paths.isEmpty()) return

            val signalEntry = uuidToSignals.entries.find { it.value == signal }

            val matchingUuid = signalEntry?.key
            val uuidOfSignal = if (matchingUuid == null) {
                val uuid = UUID.randomUUID()
                uuidToSignals[uuid] = signal
                uuid
            } else {
                matchingUuid
            }

            paths.forEach { path ->
                logger.debug {
                    "Add signal ${signal.base.packageName} ${signal.base.packageVersion} with namespace " +
                        "${signal.base.packageNamespace} of of source ${signal.base.source} to $path."
                }

                resources.addResource(path)
                pathToSignals.getOrPut(resolvePath(path)) { mutableSetOf() } += uuidOfSignal
            }
        }

        private fun signalFromPkg(pkg: Package): OpossumSignal {
            val source = addExternalAttributionSource("ORT-Package", "ORT-Package", 180)
            return OpossumSignal.create(
                source,
                id = pkg.id,
                url = pkg.homepageUrl,
                license = pkg.concludedLicense ?: pkg.declaredLicensesProcessed.spdxExpression,
                preSelected = true
            )
        }

        private fun addDependency(dependency: DependencyNode, ortResult: OrtResult, relRoot: String, level: Int = 1) {
            val dependencyId = dependency.id

            logger.debug { "$relRoot - ${dependencyId.toCoordinates()} - Dependency" }

            val dependencyPath =
                resolvePath(listOf(relRoot, dependencyId.namespace, "${dependencyId.name}@${dependencyId.version}"))
            val dependencyPackage = ortResult.getPackage(dependencyId)?.metadata
                ?: Package.EMPTY.copy(id = dependencyId)

            addPackageRoot(dependencyId, dependencyPath, level, dependencyPackage.vcsProcessed)
            addSignal(signalFromPkg(dependencyPackage), setOf(dependencyPath))

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

        private fun addProject(project: Project, ortResult: OrtResult, relRoot: String = "/") {
            val projectId = project.id
            val definitionFilePath = resolvePath(relRoot, project.definitionFilePath)

            logger.debug { "$definitionFilePath - $projectId - Project" }

            val projectRoot = getRootForProject(project, relRoot)

            addPackageRoot(projectId, projectRoot, 0, project.toPackage().vcsProcessed)
            addFileWithChildren(definitionFilePath)

            val source = addExternalAttributionSource("ORT-Project", "ORT-Project", 200)
            val signalFromProject = OpossumSignal.create(
                source,
                id = projectId,
                url = project.homepageUrl,
                license = project.declaredLicensesProcessed.spdxExpression,
                copyright = project.authors.joinToString(separator = "\n"),
                preSelected = true
            )

            addSignal(signalFromProject, setOf(definitionFilePath))

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
                        .toExpression()

                    val pathSignal = OpossumSignal.create(
                        source,
                        copyright = copyright,
                        license = license
                    )

                    addSignal(
                        pathSignal,
                        rootsBelowMaxDepth.map { resolvePath(it, pathFromFinding) }.toSet()
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

                val rootSignal = OpossumSignal.create(
                    source,
                    copyright = copyright,
                    license = license
                )

                addSignal(rootSignal, rootsAboveMaxDepth.toSet())
            }

            val issueSource = addExternalAttributionSource(
                key = "ORT-Scanner-Issue-$scanner",
                name = "ORT-Scanner Issue $scanner",
                priority = ISSUE_PRIORITY
            )

            result.summary.issues.forEach { addIssue(it, id, issueSource) }
        }

        private fun addScannerResults(id: Identifier, results: List<ScanResult>, maxDepth: Int) =
            results.forEach { addScannerResult(id, it, maxDepth) }

        private fun addIssue(issue: Issue, id: Identifier, source: String) {
            val roots = packageToRoot[id]

            val paths = if (roots.isNullOrEmpty()) {
                logger.info { "No root for $id" }
                mutableSetOf("/")
            } else {
                roots.keys
            }

            val signal = OpossumSignal.create(
                source,
                comment = issue.toString(),
                followUp = true,
                excludeFromNotice = true
            )

            addSignal(signal, paths.map { resolvePath(it) }.toSet())
        }

        private fun addPackagesThatAreRootless(analyzerResultPackages: Set<CuratedPackage>) {
            val rootlessPackages = analyzerResultPackages.filter { packageToRoot[it.metadata.id] == null }

            rootlessPackages.forEach {
                val path = resolvePath("/lost+found", it.metadata.id.toPurl())
                addSignal(signalFromPkg(it.metadata), setOf(path))
                addPackageRoot(it.metadata.id, path, Int.MAX_VALUE, it.metadata.vcsProcessed)
            }

            if (rootlessPackages.isNotEmpty()) {
                logger.warn { "There are ${rootlessPackages.size} packages that had no root." }
            }
        }
    }
}

private fun DependencyNode.getDependencies(): List<DependencyNode> =
    buildList {
        visitDependencies { dependencyNodes ->
            addAll(dependencyNodes.map { it.getStableReference() })
        }
    }
