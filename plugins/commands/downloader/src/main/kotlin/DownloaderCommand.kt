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

package org.ossreviewtoolkit.plugins.commands.downloader

import com.github.ajalt.clikt.core.ProgramResult
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

import kotlin.time.measureTime

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withContext

import org.apache.logging.log4j.kotlin.Logging

import org.ossreviewtoolkit.downloader.DownloadException
import org.ossreviewtoolkit.downloader.Downloader
import org.ossreviewtoolkit.downloader.VersionControlSystem
import org.ossreviewtoolkit.downloader.consolidateProjectPackagesByVcs
import org.ossreviewtoolkit.model.Identifier
import org.ossreviewtoolkit.model.Package
import org.ossreviewtoolkit.model.PackageType
import org.ossreviewtoolkit.model.RemoteArtifact
import org.ossreviewtoolkit.model.VcsInfo
import org.ossreviewtoolkit.model.VcsType
import org.ossreviewtoolkit.model.licenses.LicenseCategorization
import org.ossreviewtoolkit.model.licenses.LicenseClassifications
import org.ossreviewtoolkit.model.licenses.LicenseInfoResolver
import org.ossreviewtoolkit.model.licenses.LicenseView
import org.ossreviewtoolkit.model.licenses.ResolvedLicenseInfo
import org.ossreviewtoolkit.model.readValue
import org.ossreviewtoolkit.model.utils.createLicenseInfoResolver
import org.ossreviewtoolkit.plugins.commands.api.OrtCommand
import org.ossreviewtoolkit.plugins.commands.api.utils.GroupTypes.FileType
import org.ossreviewtoolkit.plugins.commands.api.utils.GroupTypes.StringType
import org.ossreviewtoolkit.plugins.commands.api.utils.OPTION_GROUP_INPUT
import org.ossreviewtoolkit.plugins.commands.api.utils.configurationGroup
import org.ossreviewtoolkit.plugins.commands.api.utils.inputGroup
import org.ossreviewtoolkit.plugins.commands.api.utils.outputGroup
import org.ossreviewtoolkit.plugins.commands.api.utils.readOrtResult
import org.ossreviewtoolkit.utils.common.ArchiveType
import org.ossreviewtoolkit.utils.common.collectMessages
import org.ossreviewtoolkit.utils.common.encodeOrUnknown
import org.ossreviewtoolkit.utils.common.expandTilde
import org.ossreviewtoolkit.utils.common.packZip
import org.ossreviewtoolkit.utils.common.safeDeleteRecursively
import org.ossreviewtoolkit.utils.ort.ORT_CONFIG_FILENAME
import org.ossreviewtoolkit.utils.ort.ORT_LICENSE_CLASSIFICATIONS_FILENAME
import org.ossreviewtoolkit.utils.ort.ortConfigDirectory
import org.ossreviewtoolkit.utils.ort.showStackTrace
import org.ossreviewtoolkit.utils.spdx.model.SpdxLicenseChoice

class DownloaderCommand : OrtCommand(
    name = "download",
    help = "Fetch source code from a remote location."
) {
    private companion object : Logging

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
                "categories are specified in the '$ORT_CONFIG_FILENAME' file. If not specified, all packages are " +
                "downloaded."
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

    private val packageTypes by option(
        "--package-types",
        help = "A comma-separated list of the package types from the ORT file's analyzer result to limit downloads to."
    ).enum<PackageType>().split(",").default(enumValues<PackageType>().asList())

    private val packageIds by option(
        "--package-ids",
        help = "A comma-separated list of regular expressions for matching package ids from the ORT file's analyzer " +
                "result to limit downloads to. If not specified, all packages are downloaded."
    ).split(",")

    private val skipExcluded by option(
        "--skip-excluded",
        help = "Do not download excluded projects or packages. Works only with the '--ort-file' parameter."
    ).flag()

    private val dryRun by option(
        "--dry-run",
        help = "Do not actually download anything but just verify that all source code locations are valid."
    ).flag()

    override fun run() {
        val failureMessages = mutableListOf<String>()

        val duration = measureTime {
            when (input) {
                is FileType -> downloadFromOrtResult((input as FileType).file, failureMessages)
                is StringType -> downloadFromProjectUrl((input as StringType).string, failureMessages)
            }
        }

        val verb = if (dryRun) "verification" else "download"
        println("The $verb took $duration.")

        if (failureMessages.isNotEmpty()) {
            logger.error {
                "The following failure(s) occurred:\n" + failureMessages.joinToString("\n--\n")
            }

            throw ProgramResult(1)
        }
    }

    private fun downloadFromOrtResult(ortFile: File, failureMessages: MutableList<String>) {
        val ortResult = readOrtResult(ortFile)

        if (ortResult.analyzer?.result == null) {
            logger.warn {
                "Cannot run the downloader as the provided ORT result file '${ortFile.canonicalPath}' does " +
                        "not contain an analyzer result. Nothing will be downloaded."
            }

            throw ProgramResult(0)
        }

        println(
            "Downloading ${packageTypes.joinToString(" and ") { "${it}s" }} from ORT result file at " +
                    "'${ortFile.canonicalPath}'..."
        )

        val packages = mutableListOf<Package>().apply {
            if (PackageType.PROJECT in packageTypes) {
                val projects = consolidateProjectPackagesByVcs(ortResult.getProjects(skipExcluded)).keys
                println("Found ${projects.size} project(s) in the ORT result.")
                addAll(projects)
            }

            if (PackageType.PACKAGE in packageTypes) {
                val packages = ortResult.getPackages(skipExcluded).map { it.metadata }
                println("Found ${packages.size} packages(s) the ORT result.")
                addAll(packages)
            }
        }

        packageIds?.also {
            val originalCount = packages.size

            val pkgIdRegex = it.joinToString(".*|.*", "(.*", ".*)").toRegex()
            val isModified = packages.retainAll { pkg -> pkgIdRegex.matches(pkg.id.toCoordinates()) }

            if (isModified) {
                val diffCount = originalCount - packages.size
                println("Removed $diffCount package(s) which do not match the specified id pattern.")
            }
        }

        val includedLicenseCategories = ortConfig.downloader.includedLicenseCategories
        if (includedLicenseCategories.isNotEmpty() && licenseClassificationsFile.isFile) {
            val originalCount = packages.size

            val licenseCategorizations = licenseClassificationsFile.readValue<LicenseClassifications>().categorizations
            val licenseInfoResolver = ortResult.createLicenseInfoResolver()

            val isModified = packages.retainAll { pkg ->
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

            if (isModified) {
                val diffCount = originalCount - packages.size
                println("Removed $diffCount package(s) which do not match the specified license classification.")
            }
        }

        println("Downloading ${packages.size} project(s) / package(s) in total.")

        val packageDownloadDirs = packages.associateWith { outputDir.resolve(it.id.toPath()) }

        runBlocking { downloadAllPackages(packageDownloadDirs, failureMessages) }

        if (archiveMode == ArchiveMode.BUNDLE && !dryRun) {
            val zipFile = outputDir.resolve("archive.zip")

            logger.info { "Archiving directory '$outputDir' to '$zipFile'." }
            val result = runCatching { outputDir.packZip(zipFile) }

            result.exceptionOrNull()?.let {
                logger.error { "Could not archive '$outputDir': ${it.collectMessages()}" }
            }

            packageDownloadDirs.forEach { (_, dir) ->
                dir.safeDeleteRecursively(baseDirectory = outputDir)
            }
        }
    }

    private suspend fun downloadAllPackages(
        packageDownloadDirs: Map<Package, File>,
        failureMessages: MutableList<String>
    ) {
        withContext(Dispatchers.IO) {
            packageDownloadDirs.entries.mapIndexed { index, (pkg, dir) ->
                async {
                    val progress = "${index + 1} of ${packageDownloadDirs.size}"

                    val verb = if (dryRun) "Verifying" else "Starting"
                    println("$verb download for '${pkg.id.toCoordinates()}' ($progress).")

                    downloadPackage(pkg, dir, failureMessages).also {
                        if (!dryRun) println("Finished download for ${pkg.id.toCoordinates()} ($progress).")
                    }
                }
            }.awaitAll()
        }
    }

    private fun downloadPackage(pkg: Package, dir: File, failureMessages: MutableList<String>) {
        try {
            Downloader(ortConfig.downloader).download(pkg, dir, dryRun)

            if (archiveMode == ArchiveMode.ENTITY && !dryRun) {
                val zipFile = outputDir.resolve("${pkg.id.toPath("-")}.zip")

                logger.info { "Archiving directory '$dir' to '$zipFile'." }
                val result = runCatching {
                    dir.packZip(
                        zipFile,
                        "${pkg.id.name.encodeOrUnknown()}/${pkg.id.version.encodeOrUnknown()}/"
                    )
                }

                result.exceptionOrNull()?.let {
                    logger.error { "Could not archive '$dir': ${it.collectMessages()}" }
                }

                dir.safeDeleteRecursively(baseDirectory = outputDir)
            }
        } catch (e: DownloadException) {
            e.showStackTrace()

            failureMessages += e.collectMessages()
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
        vararg licenseChoices: List<SpdxLicenseChoice>
    ): Set<String> {
        val resolvedLicenseInfo = licenseInfoResolver.resolveLicenseInfo(pkg.id)
        val effectiveLicenses = resolvedLicenseInfo.effectiveLicense(
            LicenseView.CONCLUDED_OR_DECLARED_AND_DETECTED,
            *licenseChoices
        )?.decompose().orEmpty()

        return licenseCategorizations
            .filter { it.id in effectiveLicenses }
            .flatMap { it.categories }
            .toSet()
    }

    private fun downloadFromProjectUrl(projectUrl: String, failureMessages: MutableList<String>) {
        val archiveType = ArchiveType.getType(projectUrl)
        val projectNameFromUrl = projectUrl.substringAfterLast('/')

        val projectName = projectNameOption ?: archiveType.extensions.fold(projectNameFromUrl) { name, ext ->
            name.removeSuffix(ext)
        }

        val dummyId = Identifier("Downloader::$projectName:")

        runCatching {
            val dummyPackage = if (archiveType != ArchiveType.NONE) {
                println("Downloading $archiveType artifact from $projectUrl...")
                Package.EMPTY.copy(id = dummyId, sourceArtifact = RemoteArtifact.EMPTY.copy(url = projectUrl))
            } else {
                val vcs = VersionControlSystem.forUrl(projectUrl)
                val vcsType = vcsTypeOption?.let { VcsType.forName(it) } ?: (vcs?.type ?: VcsType.UNKNOWN)
                val vcsRevision = vcsRevisionOption ?: vcs?.getDefaultBranchName(projectUrl).orEmpty()

                val vcsInfo = VcsInfo(
                    type = vcsType,
                    url = projectUrl,
                    revision = vcsRevision,
                    path = vcsPath
                )

                println("Downloading from $vcsType VCS at $projectUrl...")
                Package.EMPTY.copy(id = dummyId, vcs = vcsInfo, vcsProcessed = vcsInfo.normalize())
            }

            // Always allow moving revisions when directly downloading a single project only. This is for
            // convenience as often the latest revision (referred to by some VCS-specific symbolic name) of a
            // project needs to be downloaded.
            val config = ortConfig.downloader.copy(allowMovingRevisions = true)

            val provenance = Downloader(config).download(dummyPackage, outputDir, dryRun)
            println("Successfully downloaded $provenance.")
        }.onFailure {
            it.showStackTrace()

            failureMessages += it.collectMessages()
        }
    }
}
