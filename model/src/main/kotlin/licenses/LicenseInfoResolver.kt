/*
 * Copyright (C) 2017-2020 HERE Europe B.V.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
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

package org.ossreviewtoolkit.model.licenses

import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.ConcurrentMap

import kotlin.io.path.createTempDirectory

import org.ossreviewtoolkit.model.CopyrightFinding
import org.ossreviewtoolkit.model.Identifier
import org.ossreviewtoolkit.model.LicenseSource
import org.ossreviewtoolkit.model.Provenance
import org.ossreviewtoolkit.model.config.CopyrightGarbage
import org.ossreviewtoolkit.model.config.LicenseFilenamePatterns
import org.ossreviewtoolkit.model.config.PathExclude
import org.ossreviewtoolkit.model.utils.FindingCurationMatcher
import org.ossreviewtoolkit.model.utils.FindingsMatcher
import org.ossreviewtoolkit.model.utils.RootLicenseMatcher
import org.ossreviewtoolkit.model.utils.prependPath
import org.ossreviewtoolkit.spdx.SpdxSingleLicenseExpression
import org.ossreviewtoolkit.utils.CopyrightStatementsProcessor
import org.ossreviewtoolkit.utils.ORT_NAME
import org.ossreviewtoolkit.utils.storage.FileArchiver

class LicenseInfoResolver(
    val provider: LicenseInfoProvider,
    val copyrightGarbage: CopyrightGarbage,
    val archiver: FileArchiver?,
    val licenseFilenamePatterns: LicenseFilenamePatterns = LicenseFilenamePatterns.DEFAULT
) {
    private val resolvedLicenseInfo: ConcurrentMap<Identifier, ResolvedLicenseInfo> = ConcurrentHashMap()
    private val resolvedLicenseFiles: ConcurrentMap<Identifier, ResolvedLicenseFileInfo> = ConcurrentHashMap()
    private val rootLicenseMatcher = RootLicenseMatcher(
        licenseFilenamePatterns = licenseFilenamePatterns.copy(rootLicenseFilenames = emptyList())
    )
    private val findingsMatcher = FindingsMatcher(RootLicenseMatcher(licenseFilenamePatterns))

    /**
     * Get the [ResolvedLicenseInfo] for the project or package identified by [id].
     */
    fun resolveLicenseInfo(id: Identifier) = resolvedLicenseInfo.getOrPut(id) { createLicenseInfo(id) }

    /**
     * Get the [ResolvedLicenseFileInfo] for the project or package identified by [id]. Requires an [archiver] to be
     * configured, otherwise always returns empty results.
     */
    fun resolveLicenseFiles(id: Identifier) = resolvedLicenseFiles.getOrPut(id) { createLicenseFileInfo(id) }

    private fun createLicenseInfo(id: Identifier): ResolvedLicenseInfo {
        val licenseInfo = provider.get(id)

        val concludedLicenses = licenseInfo.concludedLicenseInfo.concludedLicense?.decompose().orEmpty()
        val declaredLicenses = licenseInfo.declaredLicenseInfo.processed.spdxExpression?.decompose().orEmpty()

        val resolvedLicenses = mutableMapOf<SpdxSingleLicenseExpression, ResolvedLicenseBuilder>()

        fun SpdxSingleLicenseExpression.builder() =
            resolvedLicenses.getOrPut(this) { ResolvedLicenseBuilder(this) }

        // Handle concluded licenses.
        concludedLicenses.forEach { license ->
            license.builder().sources += LicenseSource.CONCLUDED
        }

        // Handle declared licenses.
        declaredLicenses.forEach { license ->
            license.builder().apply {
                sources += LicenseSource.DECLARED

                originalDeclaredLicenses.addAll(
                    licenseInfo.declaredLicenseInfo.processed.mapped.filterValues { it == license }.keys
                )
            }
        }

        // Handle detected licenses.
        val copyrightGarbageFindings = mutableMapOf<Provenance, Set<CopyrightFinding>>()
        val filteredDetectedLicenseInfo =
            licenseInfo.detectedLicenseInfo.filterCopyrightGarbage(copyrightGarbageFindings)

        val unmatchedCopyrights = mutableMapOf<Provenance, MutableSet<CopyrightFinding>>()
        val resolvedLocations = resolveLocations(filteredDetectedLicenseInfo, unmatchedCopyrights)

        resolvedLocations.keys.forEach { license ->
            license.builder().apply {
                sources += LicenseSource.DETECTED
                resolvedLocations[license]?.let { locations.addAll(it) }
            }
        }

        return ResolvedLicenseInfo(
            id,
            licenseInfo,
            resolvedLicenses.values.map { it.build() },
            copyrightGarbageFindings,
            unmatchedCopyrights
        )
    }

    private fun DetectedLicenseInfo.filterCopyrightGarbage(
        copyrightGarbageFindings: MutableMap<Provenance, Set<CopyrightFinding>>
    ): DetectedLicenseInfo {
        val filteredFindings = findings.map {
            val partitionedFindings = it.copyrights.partition { copyrightFinding ->
                copyrightFinding.statement in copyrightGarbage.items
            }
            copyrightGarbageFindings[it.provenance] = partitionedFindings.first.toSet()
            it.copy(copyrights = partitionedFindings.second.toSet())
        }
        return DetectedLicenseInfo(filteredFindings)
    }

    private fun resolveLocations(
        detectedLicenseInfo: DetectedLicenseInfo,
        unmatchedCopyrights: MutableMap<Provenance, MutableSet<CopyrightFinding>>
    ): Map<SpdxSingleLicenseExpression, Set<ResolvedLicenseLocation>> {
        val resolvedLocations = mutableMapOf<SpdxSingleLicenseExpression, MutableSet<ResolvedLicenseLocation>>()
        val curationMatcher = FindingCurationMatcher()

        detectedLicenseInfo.findings.forEach { findings ->
            val licenseCurationResults =
                curationMatcher
                    .applyAll(findings.licenses, findings.licenseFindingCurations, findings.relativeFindingsPath)
                    .associateBy { it.curatedFinding }

            // TODO: Currently license findings that are mapped to null are ignored, but they should be included in the
            //       resolved license for completeness, e.g. to show in a report that a license finding was marked as
            //       false positive.
            val curatedLicenseFindings = licenseCurationResults.keys.filterNotNull().toSet()
            val matchResult = findingsMatcher.match(curatedLicenseFindings, findings.copyrights)

            matchResult.matchedFindings.forEach { (licenseFinding, copyrightFindings) ->
                val resolvedCopyrightFindings = resolveCopyrights(
                    copyrightFindings,
                    findings.pathExcludes,
                    findings.relativeFindingsPath
                )

                // TODO: Currently only the first curation for the license finding is recorded here and the original
                //       findings are ignored, but for completeness all curations and original findings should be
                //       included in the resolved license, e.g. to show in a report which original license findings were
                //       curated.
                val appliedCuration =
                    licenseCurationResults.getValue(licenseFinding).originalFindings.firstOrNull()?.second

                val matchingPathExcludes = findings.pathExcludes.filter {
                    it.matches(licenseFinding.location.prependPath(findings.relativeFindingsPath))
                }

                licenseFinding.license.decompose().forEach { singleLicense ->
                    resolvedLocations.getOrPut(singleLicense) { mutableSetOf() } += ResolvedLicenseLocation(
                        findings.provenance,
                        licenseFinding.location,
                        appliedCuration = appliedCuration,
                        matchingPathExcludes = matchingPathExcludes,
                        copyrights = resolvedCopyrightFindings
                    )
                }
            }

            unmatchedCopyrights.getOrPut(findings.provenance) { mutableSetOf() } += matchResult.unmatchedCopyrights
        }

        return resolvedLocations
    }

    private fun resolveCopyrights(
        copyrightFindings: Set<CopyrightFinding>,
        pathExcludes: List<PathExclude>,
        relativeFindingsPath: String
    ): Set<ResolvedCopyright> {
        val resolvedCopyrightFindings = copyrightFindings.map { finding ->
            val matchingPathExcludes = pathExcludes.filter {
                it.matches(finding.location.prependPath(relativeFindingsPath))
            }

            ResolvedCopyrightFinding(finding.statement, finding.location, matchingPathExcludes)
        }

        return processCopyrights(resolvedCopyrightFindings)
    }

    private fun createLicenseFileInfo(id: Identifier): ResolvedLicenseFileInfo {
        if (archiver == null) {
            return ResolvedLicenseFileInfo(id, emptyList())
        }

        val licenseInfo = resolveLicenseInfo(id)
        val licenseFiles = mutableListOf<ResolvedLicenseFile>()

        licenseInfo.flatMapTo(mutableSetOf()) { resolvedLicense ->
            resolvedLicense.locations.map { it.provenance }
        }.forEach { provenance ->
            val archiveDir = createTempDirectory("$ORT_NAME-archive").toFile().apply { deleteOnExit() }
            val path = "${id.toPath()}/${provenance.hash()}"

            if (!archiver.unarchive(archiveDir, path)) return@forEach

            val directory = provenance.vcsInfo?.path.orEmpty()
            val rootLicenseFiles = rootLicenseMatcher.getApplicableLicenseFilesForDirectories(
                relativeFilePaths = archiveDir.walk().filter { it.isFile }.mapTo(mutableSetOf()) {
                    it.toRelativeString(archiveDir)
                },
                directories = listOf(directory)
            ).getValue(directory)

            licenseFiles += rootLicenseFiles.map { relativePath ->
                ResolvedLicenseFile(
                    provenance = provenance,
                    licenseInfo.filter(provenance, relativePath),
                    relativePath,
                    archiveDir.resolve(relativePath)
                )
            }
        }

        return ResolvedLicenseFileInfo(id, licenseFiles)
    }
}

private class ResolvedLicenseBuilder(val license: SpdxSingleLicenseExpression) {
    val sources = mutableSetOf<LicenseSource>()
    var originalDeclaredLicenses = mutableSetOf<String>()
    var locations = mutableSetOf<ResolvedLicenseLocation>()

    fun build() = ResolvedLicense(license, sources, originalDeclaredLicenses, locations)
}

internal fun processCopyrights(
    resolvedCopyrightFindings: Collection<ResolvedCopyrightFinding>
): Set<ResolvedCopyright> {
    val allStatements = resolvedCopyrightFindings.map { it.statement }
    val processedStatements = CopyrightStatementsProcessor().process(allStatements).toMap()

    return processedStatements.mapValues { (_, originalStatements) ->
        resolvedCopyrightFindings.filter { it.statement in originalStatements }
    }.filterValues { it.isNotEmpty() }.entries.mapTo(mutableSetOf()) { (statement, findings) ->
        ResolvedCopyright(statement, findings.toSet())
    }
}

private fun CopyrightStatementsProcessor.Result.toMap(): Map<String, Set<String>> =
    processedStatements + unprocessedStatements.associateWith { setOf(it) }
