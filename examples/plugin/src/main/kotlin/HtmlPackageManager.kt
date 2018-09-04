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

package com.here.ort.examples.plugin

import com.here.ort.analyzer.AbstractPackageManagerFactory
import com.here.ort.analyzer.PackageManager
import com.here.ort.downloader.VersionControlSystem
import com.here.ort.model.CuratedPackage
import com.here.ort.model.Identifier
import com.here.ort.model.Package
import com.here.ort.model.PackageReference
import com.here.ort.model.Project
import com.here.ort.model.ProjectAnalyzerResult
import com.here.ort.model.RemoteArtifact
import com.here.ort.model.Scope
import com.here.ort.model.VcsInfo
import com.here.ort.model.config.AnalyzerConfiguration
import com.here.ort.model.config.RepositoryConfiguration

import java.io.File
import java.net.URL
import java.time.Instant
import java.time.format.DateTimeParseException
import java.util.SortedSet

import org.jsoup.Jsoup
import org.jsoup.nodes.Document

class HtmlPackageManager(analyzerConfig: AnalyzerConfiguration, repoConfig: RepositoryConfiguration) :
        PackageManager(analyzerConfig, repoConfig) {
    class Factory : AbstractPackageManagerFactory<HtmlPackageManager>() {
        override val globsForDefinitionFiles = listOf("*.html")

        override fun create(analyzerConfig: AnalyzerConfiguration, repoConfig: RepositoryConfiguration) =
                HtmlPackageManager(analyzerConfig, repoConfig)
    }

    override fun command(workingDir: File) = "" // Not using a command line tool.

    override fun resolveDependencies(definitionFile: File): ProjectAnalyzerResult? {
        val projectDir = definitionFile.parentFile

        val doc = Jsoup.parse(definitionFile.readText())

        val date = findDate(doc)

        val (dependencies, packages) = findDependencies(doc, date)

        val scope = Scope(
                name = "links",
                dependencies = dependencies
        )

        return ProjectAnalyzerResult(
                project = Project(
                        id = Identifier(
                                provider = "html",
                                namespace = "",
                                name = definitionFile.name,
                                version = ""
                        ),
                        definitionFilePath = VersionControlSystem.getPathInfo(definitionFile).path,
                        declaredLicenses = sortedSetOf(),
                        vcs = VcsInfo.EMPTY,
                        vcsProcessed = processProjectVcs(projectDir),
                        homepageUrl = "",
                        scopes = sortedSetOf(scope)
                ),
                packages = packages,
                errors = listOf()
        )
    }

    private fun findDate(doc: Document): String {
        val metaDate = doc.select("meta").find { meta ->
            meta.attr("name") == "date"
        }?.attr("content")?.let { if (it.isEmpty()) null else it }

        return metaDate?.let {
            try {
                // Check if the date is in ISO-8601.
                Instant.parse(it).epochSecond.toString()
            } catch (e: DateTimeParseException) {
                null
            }
        } ?: Instant.now().epochSecond.toString()
    }

    private fun findDependencies(doc: Document, date: String)
                : Pair<SortedSet<PackageReference>, SortedSet<CuratedPackage>> {
        val links = doc.select("a")

        val dependencies = links.mapNotNull { link ->
            val href = link.attr("href")
            if (href.isEmpty()) {
                null
            } else {
                val url = URL(href)

                val id = Identifier(
                        provider = url.protocol,
                        namespace = url.host,
                        name = url.path,
                        version = date
                )

                val pkgRef = PackageReference(
                        id = id,
                        dependencies = sortedSetOf(),
                        errors = listOf()
                )

                val pkg = Package(
                        id = id,
                        declaredLicenses = sortedSetOf(),
                        description = "",
                        homepageUrl = href,
                        binaryArtifact = RemoteArtifact.EMPTY,
                        sourceArtifact = RemoteArtifact.EMPTY,
                        vcs = VcsInfo(
                                type = "archive.org",
                                url = href,
                                revision = date
                        )
                )

                Pair(pkgRef, pkg)
            }
        }

        return Pair(
                dependencies.map { it.first }.toSortedSet(),
                dependencies.map { it.second.toCuratedPackage() }.toSortedSet()
        )
    }
}
