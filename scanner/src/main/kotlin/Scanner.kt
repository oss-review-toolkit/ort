/*
 * Copyright (C) 2017-2018 HERE Europe B.V.
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

package com.here.ort.scanner

import ch.frankel.slf4k.*

import com.here.ort.downloader.Downloader
import com.here.ort.model.Environment
import com.here.ort.model.OrtResult
import com.here.ort.model.Package
import com.here.ort.model.ProjectScanScopes
import com.here.ort.model.ScanRecord
import com.here.ort.model.ScanResult
import com.here.ort.model.ScanResultContainer
import com.here.ort.model.ScannerRun
import com.here.ort.model.config.ScannerConfiguration
import com.here.ort.model.readValue
import com.here.ort.utils.log

import java.io.File
import java.util.ServiceLoader

const val TOOL_NAME = "scanner"
const val HTTP_CACHE_PATH = "$TOOL_NAME/cache/http"

/**
 * The class to run license / copyright scanners. The signatures of public functions in this class define the library
 * API.
 */
abstract class Scanner(protected val config: ScannerConfiguration) {
    companion object {
        private val LOADER = ServiceLoader.load(ScannerFactory::class.java)!!

        /**
         * The list of all available scanners in the classpath.
         */
        val ALL by lazy { LOADER.iterator().asSequence().toList() }
    }

    /**
     * Return the Java class name as a simple way to refer to the [Scanner].
     */
    override fun toString(): String = javaClass.simpleName

    /**
     * Scan the list of [packages] using this [Scanner] and store the scan results in [outputDirectory]. If
     * [downloadDirectory] is specified, it is used instead of [outputDirectory] to download the source code to.
     * [ScanResult]s are returned associated by the [Package]. The map may contain multiple results for the same
     * [Package] if the cache contains more than one result for the specification of this scanner.
     */
    abstract fun scan(packages: List<Package>, outputDirectory: File, downloadDirectory: File? = null)
            : Map<Package, List<ScanResult>>

    /**
     * Scan the [Project]s and [Package]s specified in [dependenciesFile] using this [Scanner] and store the scan
     * results in [outputDirectory]. If [downloadDirectory] is specified, it is used instead of [outputDirectory] to
     * download the source code to. Return scan results as an [OrtResult].
     */
    fun scanDependenciesFile(dependenciesFile: File, outputDirectory: File, downloadDirectory: File? = null,
                             scopesToScan: Set<String> = emptySet()): OrtResult {
        require(dependenciesFile.isFile) {
            "Provided path for the configuration does not refer to a file: ${dependenciesFile.absolutePath}"
        }

        val ortResult = dependenciesFile.readValue(OrtResult::class.java)

        require(ortResult.analyzer != null) {
            "The provided dependencies file '${dependenciesFile.invariantSeparatorsPath}' does not contain an " +
                    "analyzer result."
        }

        val analyzerResult = ortResult.analyzer!!.result

        // Add the projects as packages to scan.
        val consolidatedProjectPackageMap = Downloader().consolidateProjectPackagesByVcs(analyzerResult.projects)
        val consolidatedReferencePackages = consolidatedProjectPackageMap.keys.map { it.toCuratedPackage() }

        val projectScanScopes = if (scopesToScan.isNotEmpty()) {
            log.info { "Limiting scan to scopes $scopesToScan." }

            analyzerResult.projects.map { project ->
                project.scopes.map { it.name }.partition { it in scopesToScan }.let {
                    ProjectScanScopes(project.id, it.first.toSortedSet(), it.second.toSortedSet())
                }
            }
        } else {
            analyzerResult.projects.map { project ->
                val scopes = project.scopes.map { it.name }
                ProjectScanScopes(project.id, scopes.toSortedSet(), sortedSetOf())
            }
        }.toSortedSet()

        val packagesToScan = if (scopesToScan.isNotEmpty()) {
            consolidatedReferencePackages + analyzerResult.packages.filter { pkg ->
                analyzerResult.projects.any { project ->
                    project.scopes.any { it.name in scopesToScan && pkg.pkg in it }
                }
            }
        } else {
            consolidatedReferencePackages + analyzerResult.packages
        }.toSortedSet()

        val results = scan(packagesToScan.map { it.pkg }, outputDirectory, downloadDirectory)
        val resultContainers = results.map { (pkg, results) ->
            ScanResultContainer(pkg.id, results)
        }.toSortedSet()

        // Add scan results from de-duplicated project packages to result.
        consolidatedProjectPackageMap.forEach { referencePackage, deduplicatedPackages ->
            resultContainers.find { it.id == referencePackage.id }?.let { resultContainer ->
                deduplicatedPackages.forEach {
                    resultContainers += resultContainer.copy(id = it.id)
                }
            }
        }

        val scanRecord = ScanRecord(projectScanScopes, resultContainers, ScanResultsCache.stats)

        val scannerRun = ScannerRun(Environment(), config, scanRecord)

        return OrtResult(ortResult.repository, ortResult.analyzer, scannerRun)
    }
}
