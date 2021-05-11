/*
 * Copyright (C) 2017-2019 HERE Europe B.V.
 * Copyright (C) 2021 Bosch.IO GmbH
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
import com.github.ajalt.clikt.core.requireObject
import com.github.ajalt.clikt.parameters.groups.default
import com.github.ajalt.clikt.parameters.groups.mutuallyExclusiveOptions
import com.github.ajalt.clikt.parameters.groups.required
import com.github.ajalt.clikt.parameters.groups.single
import com.github.ajalt.clikt.parameters.options.convert
import com.github.ajalt.clikt.parameters.options.default
import com.github.ajalt.clikt.parameters.options.flag
import com.github.ajalt.clikt.parameters.options.option
import com.github.ajalt.clikt.parameters.options.required
import com.github.ajalt.clikt.parameters.options.split
import com.github.ajalt.clikt.parameters.options.switch
import com.github.ajalt.clikt.parameters.types.enum
import com.github.ajalt.clikt.parameters.types.file

import java.io.File

import org.ossreviewtoolkit.GlobalOptions
import org.ossreviewtoolkit.GroupTypes.FileType
import org.ossreviewtoolkit.GroupTypes.StringType
import org.ossreviewtoolkit.downloader.DownloadException
import org.ossreviewtoolkit.downloader.Downloader
import org.ossreviewtoolkit.downloader.VersionControlSystem
import org.ossreviewtoolkit.downloader.consolidateProjectPackagesByVcs
import org.ossreviewtoolkit.model.Identifier
import org.ossreviewtoolkit.model.Package
import org.ossreviewtoolkit.model.RemoteArtifact
import org.ossreviewtoolkit.model.VcsInfo
import org.ossreviewtoolkit.model.VcsType
import org.ossreviewtoolkit.model.licenses.LicenseCategorization
import org.ossreviewtoolkit.model.licenses.LicenseClassifications
import org.ossreviewtoolkit.model.licenses.LicenseInfoResolver
import org.ossreviewtoolkit.model.licenses.LicenseView
import org.ossreviewtoolkit.model.licenses.ResolvedLicenseInfo
import org.ossreviewtoolkit.model.licenses.orEmpty
import org.ossreviewtoolkit.model.readValue
import org.ossreviewtoolkit.model.utils.createLicenseInfoResolver
import org.ossreviewtoolkit.readOrtResult
import org.ossreviewtoolkit.spdx.VCS_DIRECTORIES
import org.ossreviewtoolkit.spdx.model.LicenseChoice
import org.ossreviewtoolkit.utils.ArchiveType
import org.ossreviewtoolkit.utils.ORT_LICENSE_CLASSIFICATIONS_FILENAME
import org.ossreviewtoolkit.utils.collectMessagesAsString
import org.ossreviewtoolkit.utils.encodeOrUnknown
import org.ossreviewtoolkit.utils.expandTilde
import org.ossreviewtoolkit.utils.log
import org.ossreviewtoolkit.utils.ortConfigDirectory
import org.ossreviewtoolkit.utils.packZip
import org.ossreviewtoolkit.utils.safeDeleteRecursively
import org.ossreviewtoolkit.utils.showStackTrace

class DownloaderCommand : CliktCommand(name = "download", help = "Fetch source code from a remote location.") {
    private val input by mutuallyExclusiveOptions(
        option(
            "--ort-file", "-i",
            help = "An ORT result file with an analyzer result to use. Must not be used together with '--project-url'."
        ).convert { it.expandTilde() }
            .file(mustExist = true, canBeFile = true, canBeDir = false, mustBeWritable = false, mustBeReadable = true)
            .convert { FileType(it.absoluteFile.normalize()) },
        option(
            "--project-url",
            help = "A VCS or archive URL of a project to download. Must not be used together with '--ort-file'."
        ).convert { StringType(it) },
        name = OPTION_GROUP_INPUT
    ).single().required()

    private val projectNameOption by option(
        "--project-name",
        help = "The speaking name of the project to download. For use together with '--project-url'. Ignored if " +
                "'--ort-file' is also specified. (default: the last part of the project URL)"
    ).inputGroup()

    private val vcsTypeOption by option(
        "--vcs-type",
        help = "The VCS type if '--project-url' points to a VCS. Ignored if '--ort-file' is also specified. " +
                "(default: the VCS type detected by querying the project URL)"
    ).inputGroup()

    private val vcsRevisionOption by option(
        "--vcs-revision",
        help = "The VCS revision if '--project-url' points to a VCS. Ignored if '--ort-file' is also specified. " +
                "(default: the VCS's default revision)"
    ).inputGroup()

    private val vcsPath by option(
        "--vcs-path",
        help = "The VCS path if '--project-url' points to a VCS. Ignored if '--ort-file' is also specified. " +
                "(default: the empty root path)"
    ).default("").inputGroup()

    private val licenseClassificationsFile by option(
        "--license-classifications-file",
        help = "A file containing the license classifications that are used to limit downloads if the included " +
                "categories are specified in the 'ort.conf' file. If not specified, all packages are downloaded."
    ).convert { it.expandTilde() }
        .file(mustExist = true, canBeFile = true, canBeDir = false, mustBeWritable = false, mustBeReadable = true)
        .convert { it.absoluteFile.normalize() }
        .default(ortConfigDirectory.resolve(ORT_LICENSE_CLASSIFICATIONS_FILENAME))
        .configurationGroup()

    private val outputDir by option(
        "--output-dir", "-o",
        help = "The output directory to download the source code to."
    ).convert { it.expandTilde() }
        .file(mustExist = false, canBeFile = false, canBeDir = true, mustBeWritable = false, mustBeReadable = false)
        .convert { it.absoluteFile.normalize() }
        .required()
        .outputGroup()

    private val allowMovingRevisions by option(
        "--allow-moving-revisions",
        help = "Allow the download of moving revisions (like e.g. HEAD or master in Git). By default these revisions " +
                "are forbidden because they are not pointing to a fixed revision of the source code."
    ).flag()

    private val globalOptionsForSubcommands by requireObject<GlobalOptions>()

    /**
     * The mode to use for archiving downloaded source code.
     */
    private enum class ArchiveMode {
        /**
         * Do not archive source code at all.
         */
        NONE,

        /**
         * Create one archive per project or package entity.
         */
        ENTITY,

        /**
         * Create a single archive containing all project or package entities.
         */
        BUNDLE
    }

    private val archiveMode by mutuallyExclusiveOptions(
        option(
            help = "Archive the downloaded source code as ZIP files to the output directory. Is ignored if " +
                    "'--project-url' is also specified."
        ).switch("--archive" to ArchiveMode.ENTITY),
        option(
            help = "Archive all the downloaded source code as a single ZIP file to the output directory. Is ignored " +
                    "if '--project-url' is also specified."
        ).switch("--archive-all" to ArchiveMode.BUNDLE)
    ).single().default(ArchiveMode.NONE)

    /**
     * The choice of data entities to download.
     */
    enum class DataEntity {
        /**
         * Identifier for package entities.
         */
        PACKAGES,

        /**
         * Identifier for project entities.
         */
        PROJECTS
    }

    private val entities by option(
        "--entities", "-e",
        help = "The data entities from the ORT file's analyzer result to limit downloads to. If not specified, all " +
                "data entities are downloaded."
    ).enum<DataEntity>().split(",").default(enumValues<DataEntity>().asList())

    override fun run() {
        val failureMessages = mutableListOf<String>()

        when (input) {
            is FileType -> {
                val ortFile = (input as FileType).file
                val ortResult = readOrtResult(ortFile)

                val analyzerResult = ortResult.analyzer?.result

                if (analyzerResult == null) {
                    log.warn {
                        "Cannot run the downloader as the provided ORT result file '${ortFile.canonicalPath}' does " +
                                "not contain an analyzer result. Nothing will be downloaded."
                    }

                    throw ProgramResult(0)
                }

                val packages = mutableListOf<Package>().apply {
                    if (DataEntity.PROJECTS in entities) {
                        addAll(consolidateProjectPackagesByVcs(analyzerResult.projects).keys)
                    }

                    if (DataEntity.PACKAGES in entities) {
                        addAll(analyzerResult.packages.map { curatedPackage -> curatedPackage.pkg })
                    }
                }

                val includedLicenseCategories = globalOptionsForSubcommands.config.downloader.includedLicenseCategories

                val packageDownloadDirs =
                    if (includedLicenseCategories.isNotEmpty() && licenseClassificationsFile.isFile) {
                        val licenseCategorizations =
                            licenseClassificationsFile.readValue<LicenseClassifications>().orEmpty().categorizations
                        val licenseInfoResolver = ortResult.createLicenseInfoResolver()

                        packages.filter { pkg ->
                            // A package is only downloaded if its license is part of a category that is part of the
                            // DownloaderConfiguration's includedLicenseCategories.
                            getLicenseCategoriesForPackage(
                                pkg,
                                licenseCategorizations,
                                licenseInfoResolver,
                                ortResult.getRepositoryLicenseChoices(),
                                ortResult.getPackageLicenseChoices(pkg.id)
                            ).any { it in includedLicenseCategories }
                        }
                    } else {
                        packages
                    }.associateWith { outputDir.resolve(it.id.toPath()) }

                packageDownloadDirs.forEach { (pkg, dir) ->
                    try {
                        Downloader(globalOptionsForSubcommands.config.downloader).download(
                            pkg,
                            dir,
                            allowMovingRevisions
                        )

                        if (archiveMode == ArchiveMode.ENTITY && archive(pkg, dir)) {
                            dir.safeDeleteRecursively(baseDirectory = outputDir)
                        }
                    } catch (e: DownloadException) {
                        e.showStackTrace()

                        val failureMessage = "Could not download '${pkg.id.toCoordinates()}': " +
                                e.collectMessagesAsString()
                        failureMessages += failureMessage

                        log.error { failureMessage }
                    }
                }

                if (archiveMode == ArchiveMode.BUNDLE && archiveAll()) {
                    packageDownloadDirs.forEach { (_, dir) ->
                        dir.safeDeleteRecursively(baseDirectory = outputDir)
                    }
                }
            }

            is StringType -> {
                val projectUrl = (input as StringType).string

                val archiveType = ArchiveType.getType(projectUrl)
                val projectNameFromUrl = projectUrl.substringAfterLast('/')

                val projectName = projectNameOption ?: archiveType.extensions.fold(projectNameFromUrl) { name, ext ->
                    name.removeSuffix(ext)
                }

                val dummyId = Identifier("Downloader::$projectName:")
                val dummyPackage = if (archiveType != ArchiveType.NONE) {
                    Package.EMPTY.copy(id = dummyId, sourceArtifact = RemoteArtifact.EMPTY.copy(url = projectUrl))
                } else {
                    val vcs = VersionControlSystem.forUrl(projectUrl)
                    val vcsType = vcsTypeOption?.let { VcsType(it) } ?: (vcs?.type ?: VcsType.UNKNOWN)
                    val vcsRevision = vcsRevisionOption ?: vcs?.getDefaultBranchName(projectUrl).orEmpty()

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
                    Downloader(globalOptionsForSubcommands.config.downloader).download(
                        dummyPackage,
                        outputDir,
                        allowMovingRevisions = true
                    )
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
            throw ProgramResult(1)
        }
    }

    /**
     * Retrieve the license categories for the [package][pkg] based on its [effective license]
     * [ResolvedLicenseInfo.effectiveLicense].
     */
    private fun getLicenseCategoriesForPackage(
        pkg: Package,
        licenseCategorizations: List<LicenseCategorization>,
        licenseInfoResolver: LicenseInfoResolver,
        vararg licenseChoices: List<LicenseChoice>
    ): Set<String> {
        val resolvedLicenseInfo = licenseInfoResolver.resolveLicenseInfo(pkg.id)
        val effectiveLicenses = resolvedLicenseInfo.effectiveLicense(
            LicenseView.ALL,
            *licenseChoices
        )?.decompose().orEmpty()

        return licenseCategorizations
            .filter { it.id in effectiveLicenses }
            .flatMap { it.categories }
            .toSet()
    }

    private fun archive(pkg: Package, inputDir: File): Boolean {
        val zipFile = File(outputDir, "${pkg.id.toPath("-")}.zip")

        log.info { "Archiving directory '$inputDir' to '$zipFile'." }

        return runCatching {
            inputDir.packZip(
                zipFile,
                "${pkg.id.name.encodeOrUnknown()}/${pkg.id.version.encodeOrUnknown()}/",
                directoryFilter = { it.name !in VCS_DIRECTORIES }
            )
        }.onFailure {
            it.showStackTrace()

            log.error { "Could not archive '$inputDir': ${it.collectMessagesAsString()}" }
        }.isSuccess
    }

    private fun archiveAll(): Boolean {
        val zipFile = outputDir.resolve("archive.zip")

        log.info { "Archiving directory '$outputDir' to '$zipFile'." }

        return runCatching {
            outputDir.packZip(
                zipFile,
                directoryFilter = { it.name !in VCS_DIRECTORIES },
                fileFilter = { it != zipFile }
            )
        }.onFailure {
            it.showStackTrace()

            log.error { "Could not archive '$outputDir': ${it.collectMessagesAsString()}" }
        }.isSuccess
    }
}
