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

package org.ossreviewtoolkit.commands

import com.github.ajalt.clikt.core.CliktCommand
import com.github.ajalt.clikt.core.ProgramResult
import com.github.ajalt.clikt.parameters.groups.mutuallyExclusiveOptions
import com.github.ajalt.clikt.parameters.groups.required
import com.github.ajalt.clikt.parameters.groups.single
import com.github.ajalt.clikt.parameters.options.convert
import com.github.ajalt.clikt.parameters.options.default
import com.github.ajalt.clikt.parameters.options.flag
import com.github.ajalt.clikt.parameters.options.option
import com.github.ajalt.clikt.parameters.options.required
import com.github.ajalt.clikt.parameters.options.split
import com.github.ajalt.clikt.parameters.types.enum
import com.github.ajalt.clikt.parameters.types.file

import org.ossreviewtoolkit.GroupTypes
import org.ossreviewtoolkit.GroupTypes.FileType
import org.ossreviewtoolkit.GroupTypes.StringType
import org.ossreviewtoolkit.downloader.DownloadException
import org.ossreviewtoolkit.downloader.Downloader
import org.ossreviewtoolkit.downloader.VersionControlSystem
import org.ossreviewtoolkit.downloader.consolidateProjectPackagesByVcs
import org.ossreviewtoolkit.model.Identifier
import org.ossreviewtoolkit.model.OrtResult
import org.ossreviewtoolkit.model.Package
import org.ossreviewtoolkit.model.RemoteArtifact
import org.ossreviewtoolkit.model.VcsInfo
import org.ossreviewtoolkit.model.VcsType
import org.ossreviewtoolkit.model.readValue
import org.ossreviewtoolkit.utils.ArchiveType
import org.ossreviewtoolkit.utils.collectMessagesAsString
import org.ossreviewtoolkit.utils.encodeOrUnknown
import org.ossreviewtoolkit.utils.expandTilde
import org.ossreviewtoolkit.utils.log
import org.ossreviewtoolkit.utils.packZip
import org.ossreviewtoolkit.utils.safeDeleteRecursively
import org.ossreviewtoolkit.utils.showStackTrace

import java.io.File

class DownloaderCommand : CliktCommand(name = "download", help = "Fetch source code from a remote location.") {
    private val input by mutuallyExclusiveOptions<GroupTypes>(
        option(
            "--ort-file", "-i",
            help = "An ORT result file with an analyzer result to use. Must not be used together with '--project-url'."
        ).convert { it.expandTilde() }
            .file(mustExist = true, canBeFile = true, canBeDir = false, mustBeWritable = false, mustBeReadable = true)
            .convert { FileType(it) },
        option(
            "--project-url",
            help = "A VCS or archive URL of a project to download. Must not be used together with '--ort-file'."
        ).convert { StringType(it) }
    ).single().required()

    private val projectNameOption by option(
        "--project-name",
        help = "The speaking name of the project to download. For use together with '--project-url'. Will be ignored " +
                "if '--ort-file' is also specified. (default: the last part of the project URL)"
    )

    private val vcsTypeOption by option(
        "--vcs-type",
        help = "The VCS type if '--project-url' points to a VCS. Will be ignored if '--ort-file' is also specified. " +
                "(default: the VCS type detected by querying the project URL)"
    )

    private val vcsRevisionOption by option(
        "--vcs-revision",
        help = "The VCS revision if '--project-url' points to a VCS. Will be ignored if '--ort-file' is also " +
                "specified. (default: the VCS's default revision)"
    )

    private val vcsPath by option(
        "--vcs-path",
        help = "The VCS path if '--project-url' points to a VCS. Will be ignored if '--ort-file' is also specified. " +
                "(default: the empty root path)"
    ).default("")

    private val outputDir by option(
        "--output-dir", "-o",
        help = "The output directory to download the source code to."
    ).convert { it.expandTilde() }
        .file(mustExist = false, canBeFile = false, canBeDir = true, mustBeWritable = false, mustBeReadable = false)
        .required()

    private val archive by option(
        "--archive",
        help = "Archive the downloaded source code as ZIP files to the output directory. Will be ignored if " +
                "'--project-url' is also specified."
    ).flag()

    private val entities by option(
        "--entities", "-e",
        help = "The data entities from the ORT file's analyzer result to limit downloads to. If not specified, all " +
                "data entities are downloaded."
    ).enum<Downloader.DataEntity>().split(",").default(enumValues<Downloader.DataEntity>().asList())

    private val allowMovingRevisions by option(
        "--allow-moving-revisions",
        help = "Allow the download of moving revisions (like e.g. HEAD or master in Git). By default these revisions " +
                "are forbidden because they are not pointing to a fixed revision of the source code."
    ).flag()

    override fun run() {
        val failureMessages = mutableListOf<String>()

        when (input) {
            is FileType -> {
                val absoluteOrtFile = (input as FileType).file.normalize()
                val analyzerResult = absoluteOrtFile.readValue<OrtResult>().analyzer?.result

                requireNotNull(analyzerResult) {
                    "The provided ORT result file '$absoluteOrtFile' does not contain an analyzer result."
                }

                val packages = mutableListOf<Package>().apply {
                    if (Downloader.DataEntity.PROJECTS in entities) {
                        addAll(consolidateProjectPackagesByVcs(analyzerResult.projects).keys)
                    }

                    if (Downloader.DataEntity.PACKAGES in entities) {
                        addAll(analyzerResult.packages.map { curatedPackage -> curatedPackage.pkg })
                    }
                }

                packages.forEach { pkg ->
                    try {
                        val downloadDir = File(outputDir, pkg.id.toPath())
                        Downloader.download(pkg, downloadDir, allowMovingRevisions)
                        if (archive) archive(pkg, downloadDir, outputDir)
                    } catch (e: DownloadException) {
                        e.showStackTrace()

                        val failureMessage = "Could not download '${pkg.id.toCoordinates()}': " +
                                e.collectMessagesAsString()
                        failureMessages += failureMessage

                        log.error { failureMessage }
                    }
                }
            }

            is StringType -> {
                val projectUrl = (input as StringType).string

                val vcs = VersionControlSystem.forUrl(projectUrl)
                val vcsType = vcsTypeOption?.let { VcsType(it) } ?: (vcs?.type ?: VcsType.NONE)
                val vcsRevision = vcsRevisionOption ?: vcs?.defaultBranchName.orEmpty()

                val projectFile = File(projectUrl)
                val projectName = projectNameOption ?: projectFile.nameWithoutExtension

                val dummyId = Identifier("Downloader::$projectName:")
                val dummyPackage = if (ArchiveType.getType(projectFile.name) != ArchiveType.NONE) {
                    Package.EMPTY.copy(id = dummyId, sourceArtifact = RemoteArtifact.EMPTY.copy(url = projectUrl))
                } else {
                    val vcsInfo = VcsInfo(
                        type = vcsType,
                        url = projectUrl,
                        revision = vcsRevision,
                        path = vcsPath
                    )
                    Package.EMPTY.copy(id = dummyId, vcs = vcsInfo, vcsProcessed = vcsInfo.normalize())
                }

                try {
                    // Always allow moving revisions when directly downloading a single project only. This is for
                    // convenience as often the latest revision (referred to by some VCS-specific symbolic name) of a
                    // project needs to be downloaded.
                    Downloader.download(dummyPackage, outputDir, allowMovingRevisions = true)
                } catch (e: DownloadException) {
                    e.showStackTrace()

                    val failureMessage = "Could not download '${dummyPackage.id.toCoordinates()}': " +
                            e.collectMessagesAsString()
                    failureMessages += failureMessage

                    log.error { failureMessage }
                }
            }
        }

        if (failureMessages.isNotEmpty()) {
            log.error { "Failure summary:\n\n${failureMessages.joinToString("\n\n")}" }
            throw ProgramResult(2)
        }
    }

    private fun archive(pkg: Package, inputDir: File, outputDir: File) {
        val zipFile = File(
            outputDir,
            "${pkg.id.type.encodeOrUnknown()}-${pkg.id.namespace.encodeOrUnknown()}-" +
                    "${pkg.id.name.encodeOrUnknown()}-${pkg.id.version.encodeOrUnknown()}.zip"
        )

        log.info {
            "Archiving directory '${inputDir.absolutePath}' to '${zipFile.absolutePath}'."
        }

        try {
            inputDir.packZip(
                zipFile,
                "${pkg.id.name.encodeOrUnknown()}/${pkg.id.version.encodeOrUnknown()}/"
            )
        } catch (e: IllegalArgumentException) {
            e.showStackTrace()

            log.error { "Could not archive '${pkg.id.toCoordinates()}': ${e.collectMessagesAsString()}" }
        } finally {
            val relativePath = outputDir.toPath().relativize(inputDir.toPath()).first()
            File(outputDir, relativePath.toString()).safeDeleteRecursively()
        }
    }
}
