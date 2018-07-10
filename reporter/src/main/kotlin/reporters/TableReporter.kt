/*
 * Copyright (c) 2017-2018 HERE Europe B.V.
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

package com.here.ort.reporter.reporters

import com.here.ort.model.Identifier
import com.here.ort.model.Project
import com.here.ort.model.ScanRecord
import com.here.ort.model.VcsInfo

import java.io.File
import java.util.SortedMap
import java.util.SortedSet

/**
 * An abstract [Reporter] that converts the [ScanRecord] to a table representation.
 */
abstract class TableReporter : Reporter {
    data class TabularScanRecord(
            /**
             * The [VcsInfo] for the scanned project.
             */
            val vcsInfo: VcsInfo,

            /**
             * A [Table] containing all dependencies that caused errors.
             */
            val errorSummary: Table,

            /**
             * A [Table] containing the dependencies of all [Project]s.
             */
            val summary: Table,

            /**
             * The [Table]s containing the dependencies for each [Project].
             */
            val projectDependencies: SortedMap<Project, Table>,

            /**
             * Additional metadata read from the "reporter.metadata" field in [ScanRecord.data].
             */
            val metadata: Map<String, String>,

            /**
             * Extra columns that shall be added to the results table by the implementing reporter.
             */
            val extraColumns: List<String>
    )

    data class Table(
            val entries: List<TableEntry>
    )

    data class TableEntry(
            /**
             * The identifier of the package.
             */
            val id: Identifier,

            /**
             * The scopes the package is used in.
             */
            val scopes: SortedSet<String>,

            /**
             * The licenses declared by the package.
             */
            val declaredLicenses: SortedSet<String>,

            /**
             * The detected licenses aggregated from all [ScanResult]s for this package.
             */
            val detectedLicenses: SortedSet<String>,

            /**
             * All analyzer errors related to this package.
             */
            val analyzerErrors: List<String>,

            /**
             * All scan errors related to this package.
             */
            val scanErrors: List<String>
    ) {
        fun merge(other: TableEntry) =
                TableEntry(
                        id = id,
                        scopes = (scopes + other.scopes).toSortedSet(),
                        declaredLicenses = (declaredLicenses + other.declaredLicenses).toSortedSet(),
                        detectedLicenses = (detectedLicenses + other.detectedLicenses).toSortedSet(),
                        analyzerErrors = (analyzerErrors + other.analyzerErrors).distinct(),
                        scanErrors = (scanErrors + other.scanErrors).distinct()
                )
    }

    override fun generateReport(scanRecord: ScanRecord, outputDir: File) {
        val errorSummaryEntries = mutableMapOf<Identifier, TableEntry>()
        val summaryEntries = mutableMapOf<Identifier, TableEntry>()

        val projectTables = scanRecord.analyzerResult.projects.associate { project ->
            val tableEntries = (listOf(project.id) + project.collectDependencyIds()).map { id ->
                val scanResult = scanRecord.scanResults.find { it.id == id }

                val scopes = project.scopes.filter { id in it }.map { it.name }.toSortedSet()

                val declaredLicenses = scanRecord.analyzerResult.projects.find { it.id == id }?.declaredLicenses
                        ?: scanRecord.analyzerResult.packages.find { it.pkg.id == id }?.pkg?.declaredLicenses
                        ?: sortedSetOf()

                val detectedLicenses = scanResult?.results?.flatMap {
                    it.summary.licenses
                }?.toSortedSet() ?: sortedSetOf()

                val analyzerErrors = project.collectErrors(id).toMutableList()
                scanRecord.analyzerResult.errors[id]?.let {
                    analyzerErrors += it
                }

                val scanErrors = scanResult?.results?.flatMap {
                    it.summary.errors
                }?.distinct() ?: emptyList()

                TableEntry(
                        id = id,
                        scopes = scopes,
                        declaredLicenses = declaredLicenses,
                        detectedLicenses = detectedLicenses,
                        analyzerErrors = analyzerErrors,
                        scanErrors = scanErrors
                ).also { entry ->
                    summaryEntries[entry.id] = summaryEntries[entry.id]?.merge(entry) ?: entry
                    if (entry.analyzerErrors.isNotEmpty() || entry.scanErrors.isNotEmpty()) {
                        errorSummaryEntries[entry.id] = errorSummaryEntries[entry.id]?.merge(entry) ?: entry
                    }
                }
            }

            Pair(project, Table(tableEntries))
        }.toSortedMap()

        val errorSummaryTable = Table(errorSummaryEntries.values.toList().sortedBy { it.id })
        val summaryTable = Table(summaryEntries.values.toList().sortedBy { it.id })

        val metadata = scanRecord.data["reporter.metadata"]?.let {
            if (it is Map<*, *>) {
                it.entries.associate { (key, value) -> key.toString() to value.toString() }
            } else {
                null
            }
        } ?: emptyMap()

        val extraColumns = scanRecord.data["reporter.extraColumns"]?.let {
            if (it is List<*>) {
                it.map { it.toString() }
            } else {
                null
            }
        } ?: emptyList()

        generateReport(TabularScanRecord(scanRecord.analyzerResult.vcsProcessed, errorSummaryTable, summaryTable,
                projectTables, metadata, extraColumns), outputDir)
    }

    abstract fun generateReport(tabularScanRecord: TabularScanRecord, outputDir: File)
}
