/*
 * Copyright (C) 2017-2019 HERE Europe B.V.
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

import com.github.ajalt.clikt.core.CliktCommand
import com.github.ajalt.clikt.core.UsageError
import com.github.ajalt.clikt.parameters.groups.mutuallyExclusiveOptions
import com.github.ajalt.clikt.parameters.groups.required
import com.github.ajalt.clikt.parameters.groups.single
import com.github.ajalt.clikt.parameters.options.convert
import com.github.ajalt.clikt.parameters.options.default
import com.github.ajalt.clikt.parameters.options.flag
import com.github.ajalt.clikt.parameters.options.option
import com.github.ajalt.clikt.parameters.options.required
import com.github.ajalt.clikt.parameters.options.split
import com.github.ajalt.clikt.parameters.options.wrapValue
import com.github.ajalt.clikt.parameters.types.enum
import com.github.ajalt.clikt.parameters.types.file

import com.here.ort.GroupTypes
import com.here.ort.GroupTypes.FileType
import com.here.ort.GroupTypes.StringType
import com.here.ort.downloader.DownloadException
import com.here.ort.downloader.Downloader
import com.here.ort.model.Identifier
import com.here.ort.model.OrtResult
import com.here.ort.model.Package
import com.here.ort.model.RemoteArtifact
import com.here.ort.model.VcsInfo
import com.here.ort.model.VcsType
import com.here.ort.model.readValue
import com.here.ort.utils.ArchiveType
import com.here.ort.utils.collectMessagesAsString
import com.here.ort.utils.encodeOrUnknown
import com.here.ort.utils.expandTilde
import com.here.ort.utils.log
import com.here.ort.utils.packZip
import com.here.ort.utils.safeDeleteRecursively
import com.here.ort.utils.showStackTrace

import java.io.File

class DownloaderCommand : CliktCommand(name = "download", help = "Fetch source code from a remote location.") {
    private val input by mutuallyExclusiveOptions<GroupTypes>(
        option(
            "--ort-file", "-i",
            help = "An ORT result file with an analyzer result to use. Must not be used together with '--project-url'."
        ).file(mustExist = true, canBeFile = true, canBeDir = false, mustBeWritable = false, mustBeReadable = true)
            .wrapValue(::FileType),
        option(
            "--project-url",
            help = "A VCS or archive URL of a project to download. Must not be used together with '--ort-file'."
        ).convert { StringType(it) }
    ).single().required()

    private val projectNameOption by option(
        "--project-name",
        help = "The speaking name of the project to download. For use together with '--project-url'. Will be ignored " +
                "if '--ort-file' is also specified."
    )

    private val vcsType by option(
        "--vcs-type",
        help = "The VCS type if '--project-url' points to a VCS. Will be ignored if '--ort-file' is also specified."
    ).default("")

    private val vcsRevision by option(
        "--vcs-revision",
        help = "The VCS revision if '--project-url' points to a VCS. Will be ignored if '--ort-file' is also specified."
    ).default("")

    private val vcsPath by option(
        "--vcs-path",
        help = "The VCS path if '--project-url' points to a VCS. Will be ignored if '--ort-file' is also specified."
    ).default("")

    private val outputDir by option(
        "--output-dir", "-o",
        help = "The output directory to download the source code to."
    ).file(mustExist = false, canBeFile = false, canBeDir = true, mustBeWritable = false, mustBeReadable = false)
        .required()

    private val archive by option(
        "--archive",
        help = "Archive the downloaded source code as ZIP files to the output directory."
    ).flag()

    private val entities by option(
        "--entities", "-e",
        help = "The data entities from the ORT file's analyzer result to limit downloads to. If not specified, all " +
                "data entities are downloaded."
    ).enum<Downloader.DataEntity>().split(",").default(enumValues<Downloader.DataEntity>().asList())

    private val allowMovingRevisionsOption by option(
        "--allow-moving-revisions",
        help = "Allow the download of moving revisions (like e.g. HEAD or master in Git). By default these revision " +
                "are forbidden because they are not pointing to a stable revision of the source code."
    ).flag()

    override fun run() {
        var allowMovingRevisions = allowMovingRevisionsOption

        val packages = when (input) {
            is FileType -> {
                val absoluteOrtFile = (input as FileType).file.expandTilde().normalize()
                val analyzerResult = absoluteOrtFile.readValue<OrtResult>().analyzer?.result

                requireNotNull(analyzerResult) {
                    "The provided ORT result file '$absoluteOrtFile' does not contain an analyzer result."
                }

                mutableListOf<Package>().apply {
                    if (Downloader.DataEntity.PROJECT in entities) {
                        addAll(Downloader.consolidateProjectPackagesByVcs(analyzerResult.projects).keys)
                    }

                    if (Downloader.DataEntity.PACKAGES in entities) {
                        addAll(analyzerResult.packages.map { curatedPackage -> curatedPackage.pkg })
                    }
                }
            }

            is StringType -> {
                val projectUrl = (input as StringType).string

                val projectFile = File(projectUrl)
                val projectName = projectNameOption ?: projectFile.nameWithoutExtension

                val dummyId = Identifier("Downloader::$projectName:")
                val dummyPackage = if (ArchiveType.getType(projectFile.name) != ArchiveType.NONE) {
                    Package.EMPTY.copy(id = dummyId, sourceArtifact = RemoteArtifact.EMPTY.copy(url = projectUrl))
                } else {
                    val vcs = VcsInfo(
                        type = VcsType(vcsType),
                        url = projectUrl,
                        revision = vcsRevision,
                        path = vcsPath
                    )
                    Package.EMPTY.copy(id = dummyId, vcs = vcs, vcsProcessed = vcs.normalize())
                }

                allowMovingRevisions = true

                listOf(dummyPackage)
            }
        }

        val errorMessages = mutableListOf<String>()
        packages.forEach { pkg ->
            try {
                val absoluteOutputDir = outputDir.expandTilde()
                val result = Downloader().download(pkg, absoluteOutputDir, allowMovingRevisions)

                if (archive) {
                    val zipFile = File(
                        absoluteOutputDir,
                        "${pkg.id.type.encodeOrUnknown()}-${pkg.id.namespace.encodeOrUnknown()}-" +
                                "${pkg.id.name.encodeOrUnknown()}-${pkg.id.version.encodeOrUnknown()}.zip"
                    )

                    log.info {
                        "Archiving directory '${result.downloadDirectory.absolutePath}' to " +
                                "'${zipFile.absolutePath}'."
                    }

                    try {
                        result.downloadDirectory.packZip(
                            zipFile,
                            "${pkg.id.name.encodeOrUnknown()}/${pkg.id.version.encodeOrUnknown()}/"
                        )
                    } catch (e: IllegalArgumentException) {
                        e.showStackTrace()

                        log.error { "Could not archive '${pkg.id.toCoordinates()}': ${e.collectMessagesAsString()}" }
                    } finally {
                        val relativePath =
                            absoluteOutputDir.toPath().relativize(result.downloadDirectory.toPath()).first()
                        File(absoluteOutputDir, relativePath.toString()).safeDeleteRecursively()
                    }
                }
            } catch (e: DownloadException) {
                e.showStackTrace()

                val errorMessage = "Could not download '${pkg.id.toCoordinates()}': ${e.collectMessagesAsString()}"
                errorMessages += errorMessage

                log.error { errorMessage }
            }
        }

        if (errorMessages.isNotEmpty()) {
            log.error { "Error Summary:\n\n${errorMessages.joinToString("\n\n")}" }
            throw UsageError("${errorMessages.size} error(s) occurred.", statusCode = 2)
        }
    }
}
