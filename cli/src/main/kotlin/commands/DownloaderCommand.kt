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

package com.here.ort.commands

import ch.frankel.slf4k.*

import com.beust.jcommander.JCommander
import com.beust.jcommander.Parameter
import com.beust.jcommander.Parameters

import com.here.ort.CommandWithHelp
import com.here.ort.downloader.DownloadException
import com.here.ort.downloader.Downloader
import com.here.ort.model.AnalyzerResult
import com.here.ort.model.HashAlgorithm
import com.here.ort.model.Identifier
import com.here.ort.model.Package
import com.here.ort.model.RemoteArtifact
import com.here.ort.model.VcsInfo
import com.here.ort.model.readValue
import com.here.ort.utils.ARCHIVE_EXTENSIONS
import com.here.ort.utils.PARAMETER_ORDER_MANDATORY
import com.here.ort.utils.PARAMETER_ORDER_OPTIONAL
import com.here.ort.utils.encodeOrUnknown
import com.here.ort.utils.log
import com.here.ort.utils.packZip
import com.here.ort.utils.safeDeleteRecursively
import com.here.ort.utils.showStackTrace

import java.io.File

import kotlin.system.exitProcess

@Parameters(commandNames = ["download"], commandDescription = "Fetch source code from a remote location.")
object DownloaderCommand : CommandWithHelp() {
    @Parameter(description = "An ORT result file with an analyzer result to use. Must not be used together with " +
            "'--project-url'.",
            names = ["--ort-file", "-a"],
            order = PARAMETER_ORDER_OPTIONAL)
    private var dependenciesFile: File? = null

    @Parameter(description = "A VCS or archive URL of a project to download. Must not be used together with " +
            "'--ort-file'.",
            names = ["--project-url"],
            order = PARAMETER_ORDER_OPTIONAL)
    private var projectUrl: String? = null

    @Parameter(description = "The speaking name of the project to download. For use together with '--project-url'. " +
            "Will be ignored if '--ort-file' is also specified.",
            names = ["--project-name"],
            order = PARAMETER_ORDER_OPTIONAL)
    private var projectName: String? = null

    @Parameter(description = "The VCS type if '--project-url' points to a VCS. Will be ignored if '--ort-file' is " +
            "also specified.",
            names = ["--vcs-type"],
            order = PARAMETER_ORDER_OPTIONAL)
    private var vcsType = ""

    @Parameter(description = "The VCS revision if '--project-url' points to a VCS. Will be ignored if '--ort-file' " +
            "is also specified.",
            names = ["--vcs-revision"],
            order = PARAMETER_ORDER_OPTIONAL)
    private var vcsRevision = ""

    @Parameter(description = "The VCS path if '--project-url' points to a VCS. Will be ignored if '--ort-file' is " +
            "also specified.",
            names = ["--vcs-path"],
            order = PARAMETER_ORDER_OPTIONAL)
    private var vcsPath = ""

    @Parameter(description = "The output directory to download the source code to.",
            names = ["--output-dir", "-o"],
            required = true,
            order = PARAMETER_ORDER_MANDATORY)
    @Suppress("LateinitUsage")
    private lateinit var outputDir: File

    @Parameter(description = "Archive the downloaded source code as ZIP files to the output directory.",
            names = ["--archive"],
            order = PARAMETER_ORDER_OPTIONAL)
    private var archive = false

    @Parameter(description = "The type of data entities from the ORT file's analyzer result to download.",
            names = ["--entities", "-e"],
            order = PARAMETER_ORDER_OPTIONAL)
    private var entities = enumValues<Downloader.DataEntity>().asList()

    @Parameter(description = "Allow the download of moving revisions (like e.g. HEAD or master in Git). By default " +
            "these revision are forbidden because they are not pointing to a stable revision of the source code.",
            names = ["--allow-moving-revisions"],
            order = PARAMETER_ORDER_OPTIONAL)
    private var allowMovingRevisions = false

    override fun runCommand(jc: JCommander) {
        if ((dependenciesFile != null) == (projectUrl != null)) {
            throw IllegalArgumentException(
                    "Either '--ort-file' or '--project-url' must be specified.")
        }

        val packages = dependenciesFile?.let {
            require(it.isFile) {
                "Provided path is not a file: ${it.absolutePath}"
            }

            val analyzerResult = it.readValue<AnalyzerResult>()

            mutableListOf<Package>().apply {
                entities = entities.distinct()

                if (Downloader.DataEntity.PROJECT in entities) {
                    addAll(Downloader.consolidateProjectPackagesByVcs(analyzerResult.projects).keys)
                }

                if (Downloader.DataEntity.PACKAGES in entities) {
                    addAll(analyzerResult.packages.map { curatedPackage -> curatedPackage.pkg })
                }
            }
        } ?: run {
            allowMovingRevisions = true

            val projectFile = File(projectUrl)
            if (projectName == null) {
                projectName = projectFile.nameWithoutExtension
            }

            val dummyId = Identifier.EMPTY.copy(name = projectName!!)
            val dummyPackage = if (ARCHIVE_EXTENSIONS.any { projectFile.name.endsWith(it) }) {
                Package.EMPTY.copy(
                        id = dummyId,
                        sourceArtifact = RemoteArtifact(
                                url = projectUrl!!,
                                hash = "",
                                hashAlgorithm = HashAlgorithm.UNKNOWN
                        )
                )
            } else {
                val vcs = VcsInfo(type = vcsType, url = projectUrl!!, revision = vcsRevision, path = vcsPath)
                Package.EMPTY.copy(id = dummyId, vcs = vcs, vcsProcessed = vcs.normalize())
            }

            listOf(dummyPackage)
        }

        var error = false

        packages.forEach { pkg ->
            try {
                val result = Downloader().download(pkg, outputDir)

                if (archive) {
                    val zipFile = File(outputDir,
                            "${pkg.id.provider.encodeOrUnknown()}-${pkg.id.namespace.encodeOrUnknown()}-" +
                                    "${pkg.id.name.encodeOrUnknown()}-${pkg.id.version.encodeOrUnknown()}.zip")

                    log.info {
                        "Archiving directory '${result.downloadDirectory.absolutePath}' to " +
                                "'${zipFile.absolutePath}'."
                    }

                    try {
                        result.downloadDirectory.packZip(zipFile,
                                "${pkg.id.name.encodeOrUnknown()}/${pkg.id.version.encodeOrUnknown()}/")
                    } catch (e: IllegalArgumentException) {
                        e.showStackTrace()

                        log.error { "Could not archive '${pkg.id}': ${e.message}" }
                    } finally {
                        val relativePath = outputDir.toPath().relativize(result.downloadDirectory.toPath()).first()
                        File(outputDir, relativePath.toString()).safeDeleteRecursively()
                    }
                }
            } catch (e: DownloadException) {
                e.showStackTrace()

                log.error { "Could not download '${pkg.id}': ${e.message}" }

                error = true
            }
        }

        if (error) exitProcess(1)
    }
}
