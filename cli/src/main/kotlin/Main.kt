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

package com.here.ort

import ch.frankel.slf4k.*

import com.beust.jcommander.IStringConverter
import com.beust.jcommander.JCommander
import com.beust.jcommander.Parameter
import com.beust.jcommander.ParameterException
import com.beust.jcommander.Parameters

import com.here.ort.analyzer.Analyzer
import com.here.ort.analyzer.PackageManager
import com.here.ort.analyzer.PackageManagerFactory
import com.here.ort.downloader.DownloadException
import com.here.ort.downloader.Downloader
import com.here.ort.model.AnalyzerResult
import com.here.ort.model.HashAlgorithm
import com.here.ort.model.Identifier
import com.here.ort.model.OutputFormat
import com.here.ort.model.OrtResult
import com.here.ort.model.Package
import com.here.ort.model.RemoteArtifact
import com.here.ort.model.VcsInfo
import com.here.ort.model.config.AnalyzerConfiguration
import com.here.ort.model.config.ScannerConfiguration
import com.here.ort.model.readValue
import com.here.ort.reporter.Reporter
import com.here.ort.reporter.reporters.*
import com.here.ort.scanner.LocalScanner
import com.here.ort.scanner.ScanResultsCache
import com.here.ort.scanner.Scanner
import com.here.ort.scanner.ScannerFactory
import com.here.ort.scanner.scanners.ScanCode
import com.here.ort.utils.ARCHIVE_EXTENSIONS
import com.here.ort.utils.PARAMETER_ORDER_HELP
import com.here.ort.utils.PARAMETER_ORDER_LOGGING
import com.here.ort.utils.PARAMETER_ORDER_MANDATORY
import com.here.ort.utils.PARAMETER_ORDER_OPTIONAL
import com.here.ort.utils.encodeOrUnknown
import com.here.ort.utils.log
import com.here.ort.utils.packZip
import com.here.ort.utils.printStackTrace
import com.here.ort.utils.safeDeleteRecursively
import com.here.ort.utils.safeMkdirs
import com.here.ort.utils.showStackTrace

import java.io.File

import kotlin.system.exitProcess

const val TOOL_NAME = "ort"

/**
 * The main entry point of the application.
 */
object Main {
    @Parameter(description = "Enable info logging.",
            names = ["--info"],
            order = PARAMETER_ORDER_LOGGING)
    private var info = false

    @Parameter(description = "Enable debug logging and keep any temporary files.",
            names = ["--debug"],
            order = PARAMETER_ORDER_LOGGING)
    private var debug = false

    @Parameter(description = "Print out the stacktrace for all exceptions.",
            names = ["--stacktrace"],
            order = PARAMETER_ORDER_LOGGING)
    private var stacktrace = false

    @Parameter(description = "Display the command line help.",
            names = ["--help", "-h"],
            help = true,
            order = PARAMETER_ORDER_HELP)
    private var help = false

    @Parameters(commandNames = ["analyze"], commandDescription = "Determine dependencies of a software project.")
    private object AnalyzerCommand : Runnable {
        private class PackageManagerConverter : IStringConverter<PackageManagerFactory> {
            companion object {
                // Map upper-cased package manager names to their instances.
                val PACKAGE_MANAGERS = PackageManager.ALL.associateBy { it.toString().toUpperCase() }
            }

            override fun convert(name: String): PackageManagerFactory {
                return PACKAGE_MANAGERS[name.toUpperCase()]
                        ?: throw ParameterException("Package managers must be contained in ${PACKAGE_MANAGERS.keys}.")
            }
        }

        @Parameter(description = "The list of package managers to activate.",
                names = ["--package-managers", "-m"],
                converter = PackageManagerConverter::class,
                order = PARAMETER_ORDER_OPTIONAL)
        private var packageManagers = PackageManager.ALL

        @Parameter(description = "The project directory to analyze.",
                names = ["--input-dir", "-i"],
                required = true,
                order = PARAMETER_ORDER_MANDATORY)
        @Suppress("LateinitUsage")
        private lateinit var inputDir: File

        @Parameter(description = "The directory to write the analyzer result file to.",
                names = ["--output-dir", "-o"],
                required = true,
                order = PARAMETER_ORDER_MANDATORY)
        @Suppress("LateinitUsage")
        private lateinit var outputDir: File

        @Parameter(description = "The list of output formats used for the result file(s).",
                names = ["--output-formats", "-f"],
                order = PARAMETER_ORDER_OPTIONAL)
        private var outputFormats = listOf(OutputFormat.YAML)

        @Parameter(description = "Ignore versions of required tools. NOTE: This may lead to erroneous results.",
                names = ["--ignore-tool-versions"],
                order = PARAMETER_ORDER_OPTIONAL)
        private var ignoreToolVersions = false

        @Parameter(description = "Allow dynamic versions of dependencies. This can result in unstable results when " +
                "dependencies use version ranges. This option only affects package managers that support lock files, " +
                "like NPM.",
                names = ["--allow-dynamic-versions"],
                order = PARAMETER_ORDER_OPTIONAL)
        private var allowDynamicVersions = false

        @Parameter(description = "By default the components configured to be excluded in the .ort.yml file of the " +
                "input directory will still be analyzed, but marked as excluded in the result. With this option " +
                "enabled they will not appear in the result.",
                names = ["--remove-excludes-from-result"],
                order = PARAMETER_ORDER_OPTIONAL)
        private var removeExcludesFromResult = false

        @Parameter(description = "A YAML file that contains package curation data.",
                names = ["--package-curations-file"],
                order = PARAMETER_ORDER_OPTIONAL)
        private var packageCurationsFile: File? = null

        override fun run() {
            val absoluteOutputPath = outputDir.absoluteFile
            if (absoluteOutputPath.exists()) {
                log.error { "The output directory '$absoluteOutputPath' must not exist yet." }
                exitProcess(1)
            }

            println("The following package managers are activated:")
            println("\t" + packageManagers.joinToString(", "))

            val absoluteProjectPath = inputDir.absoluteFile
            println("Scanning project path:\n\t$absoluteProjectPath")

            val config = AnalyzerConfiguration(ignoreToolVersions, allowDynamicVersions, removeExcludesFromResult)
            val analyzer = Analyzer(config)
            val ortResult = analyzer.analyze(absoluteProjectPath, packageManagers, packageCurationsFile)

            absoluteOutputPath.safeMkdirs()

            outputFormats.forEach { format ->
                val outputFile = File(absoluteOutputPath, "analyzer-result.${format.fileExtension}")
                println("Writing analyzer result to '$outputFile'.")
                format.mapper.writerWithDefaultPrettyPrinter().writeValue(outputFile, ortResult)
            }
        }
    }

    @Parameters(commandNames = ["download"], commandDescription = "Fetch source code from a remote location.")
    private object DownloaderCommand : Runnable {
        @Parameter(description = "An analyzer result file to use. Must not be used together with '--project-url'.",
                names = ["--analyzer-result-file", "-a"],
                order = PARAMETER_ORDER_OPTIONAL)
        private var dependenciesFile: File? = null

        @Parameter(description = "A VCS or archive URL of a project to download. Must not be used together with " +
                "'--analyzer-result-file'.",
                names = ["--project-url"],
                order = PARAMETER_ORDER_OPTIONAL)
        private var projectUrl: String? = null

        @Parameter(description = "The speaking name of the project to download. For use together with " +
                "'--project-url'. Will be ignored if '--analyzer-result-file' is also specified.",
                names = ["--project-name"],
                order = PARAMETER_ORDER_OPTIONAL)
        private var projectName: String? = null

        @Parameter(description = "The VCS type if '--project-url' points to a VCS. Will be ignored if " +
                "'--analyzer-result-file' is also specified.",
                names = ["--vcs-type"],
                order = PARAMETER_ORDER_OPTIONAL)
        private var vcsType = ""

        @Parameter(description = "The VCS revision if '--project-url' points to a VCS. Will be ignored if " +
                "'--analyzer-result-file' is also specified.",
                names = ["--vcs-revision"],
                order = PARAMETER_ORDER_OPTIONAL)
        private var vcsRevision = ""

        @Parameter(description = "The VCS path if '--project-url' points to a VCS. Will be ignored if " +
                "'--analyzer-result-file' is also specified.",
                names = ["--vcs-path"],
                order = PARAMETER_ORDER_OPTIONAL)
        private var vcsPath = ""

        @Parameter(description = "The output directory to download the source code to.",
                names = ["--output-dir", "-o"],
                required = true,
                order = PARAMETER_ORDER_MANDATORY)
        @Suppress("LateinitUsage")
        private lateinit var outputDir: File

        @Parameter(description = "Archive the downloaded source code as ZIP files.",
                names = ["--archive"],
                order = PARAMETER_ORDER_OPTIONAL)
        private var archive = false

        @Parameter(description = "The data entities from the analyzer result file to download.",
                names = ["--entities", "-e"],
                order = PARAMETER_ORDER_OPTIONAL)
        private var entities = enumValues<Downloader.DataEntity>().asList()

        @Parameter(description = "Allow the download of moving revisions (like e.g. HEAD or master in Git). By " +
                "default these revision are forbidden because they are not pointing to a stable revision of the " +
                "source code.",
                names = ["--allow-moving-revisions"],
                order = PARAMETER_ORDER_OPTIONAL)
        private var allowMovingRevisions = false

        override fun run() {
            if ((dependenciesFile != null) == (projectUrl != null)) {
                throw IllegalArgumentException(
                        "Either '--analyzer-result-file' or '--project-url' must be specified.")
            }

            val packages = dependenciesFile?.let {
                require(it.isFile) {
                    "Provided path is not a file: ${it.absolutePath}"
                }

                val analyzerResult = it.readValue(AnalyzerResult::class.java)

                mutableListOf<Package>().apply {
                    if (Downloader.DataEntity.PROJECT in entities) {
                        Downloader().consolidateProjectPackagesByVcs(analyzerResult.projects).let {
                            addAll(it.keys)
                        }
                    }

                    if (Downloader.DataEntity.PACKAGES in entities) {
                        addAll(analyzerResult.packages.map { it.pkg })
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

    @Parameters(commandNames = ["report"],
            commandDescription = "Present Analyzer and Scanner results in various formats.")
    private object ReporterCommand : Runnable {
        private enum class ReportFormat(private val reporter: Reporter) : Reporter {
            EXCEL(ExcelReporter()),
            NOTICE(NoticeReporter()),
            STATIC_HTML(StaticHtmlReporter()),
            WEB_APP(WebAppReporter());

            override fun generateReport(ortResult: OrtResult, outputDir: File) =
                    reporter.generateReport(ortResult, outputDir)
        }

        @Parameter(description = "The ORT result file to use. Must contain a scan result.",
                names = ["--ort-result-file", "-i"],
                required = true,
                order = PARAMETER_ORDER_MANDATORY)
        private lateinit var ortResultFile: File

        @Parameter(description = "The output directory to store the generated reports in.",
                names = ["--output-dir", "-o"],
                required = true,
                order = PARAMETER_ORDER_MANDATORY)
        @Suppress("LateinitUsage")
        private lateinit var outputDir: File

        @Parameter(description = "The list of report formats that will be generated.",
                names = ["--report-formats", "-f"],
                required = true,
                order = PARAMETER_ORDER_MANDATORY)
        private lateinit var reportFormats: List<ReportFormat>

        override fun run() {
            require(!outputDir.exists()) {
                "The output directory '${outputDir.absolutePath}' must not exist yet."
            }

            outputDir.safeMkdirs()

            val ortResult = ortResultFile.let {
                it.readValue(OrtResult::class.java)
            }

            reportFormats.forEach {
                it.generateReport(ortResult, outputDir)
            }
        }
    }

    @Parameters(commandNames = ["scan"], commandDescription = "Run existing copyright / license scanners.")
    private object ScannerCommand : Runnable {
        private class ScannerConverter : IStringConverter<ScannerFactory> {
            override fun convert(scannerName: String): ScannerFactory {
                // TODO: Consider allowing to enable multiple scanners (and potentially running them in parallel).
                return Scanner.ALL.find { it.toString().equals(scannerName, true) }
                        ?: throw ParameterException("Scanner '$scannerName' is not one of ${Scanner.ALL}.")
            }
        }

        @Parameter(description = "The analyzer result file to use. Source code will be downloaded automatically if " +
                "needed. This parameter and --input-path are mutually exclusive.",
                names = ["--analyzer-result-file", "-a"],
                order = PARAMETER_ORDER_OPTIONAL)
        private var dependenciesFile: File? = null

        @Parameter(description = "The input directory or file to scan. This parameter and --analyzer-result-file are " +
                "mutually exclusive.",
                names = ["--input-path", "-i"],
                order = PARAMETER_ORDER_OPTIONAL)
        private var inputPath: File? = null

        @Parameter(description = "The list of scopes that shall be scanned. Works only with the " +
                "--analyzer-result-file parameter. If empty, all scopes are scanned.",
                names = ["--scopes"],
                order = PARAMETER_ORDER_OPTIONAL)
        private var scopesToScan = listOf<String>()

        @Parameter(description = "The output directory to store the scan results in.",
                names = ["--output-dir", "-o"],
                required = true,
                order = PARAMETER_ORDER_MANDATORY)
        @Suppress("LateinitUsage")
        private lateinit var outputDir: File

        @Parameter(description = "The output directory for downloaded source code. Defaults to <output-dir>/downloads.",
                names = ["--download-dir"],
                order = PARAMETER_ORDER_OPTIONAL)
        private var downloadDir: File? = null

        @Parameter(description = "The scanner to use.",
                names = ["--scanner", "-s"],
                converter = ScannerConverter::class,
                order = PARAMETER_ORDER_OPTIONAL)
        private var scannerFactory: ScannerFactory? = null

        @Parameter(description = "The path to the configuration file.",
                names = ["--config", "-c"],
                order = PARAMETER_ORDER_OPTIONAL)
        private var configFile: File? = null

        @Parameter(description = "The list of output formats used for the result file(s).",
                names = ["--output-formats", "-f"],
                order = PARAMETER_ORDER_OPTIONAL)
        private var outputFormats = listOf(OutputFormat.YAML)

        override fun run() {
            require((dependenciesFile == null) != (inputPath == null)) {
                "Either --analyzer-result-file or --input-path must be specified."
            }

            require(!outputDir.exists()) {
                "The output directory '${outputDir.absolutePath}' must not exist yet."
            }

            downloadDir?.let {
                require(!it.exists()) {
                    "The download directory '${it.absolutePath}' must not exist yet."
                }
            }

            val config = configFile?.let {
                require(it.isFile) {
                    "Provided configuration file is not a file: ${it.invariantSeparatorsPath}"
                }

                it.readValue(ScannerConfiguration::class.java)
            } ?: ScannerConfiguration()

            config.artifactoryCache?.let {
                ScanResultsCache.configure(it)
            }

            val scanner = scannerFactory?.let {
                it.create(config)
            } ?: ScanCode(config)

            println("Using scanner '$scanner'.")

            val ortResult = dependenciesFile?.let {
                scanner.scanDependenciesFile(it, outputDir, downloadDir, scopesToScan)
            } ?: run {
                require(scanner is LocalScanner) {
                    "To scan local files the chosen scanner must be a local scanner."
                }

                val localScanner = scanner as LocalScanner
                localScanner.scanInputPath(inputPath!!, outputDir)
            }

            outputFormats.forEach { format ->
                val scanRecordFile = File(outputDir, "scan-result.${format.fileExtension}")
                println("Writing scan record to '${scanRecordFile.absolutePath}'.")
                format.mapper.writerWithDefaultPrettyPrinter().writeValue(scanRecordFile, ortResult)
            }
        }
    }

    /**
     * The entry point for the application.
     *
     * @param args The list of application arguments.
     */
    @JvmStatic
    fun main(args: Array<String>) {
        val jc = JCommander(this).apply {
            programName = TOOL_NAME
            addCommand(AnalyzerCommand)
            addCommand(DownloaderCommand)
            addCommand(ReporterCommand)
            addCommand(ScannerCommand)
            parse(*args)
        }

        when {
            debug -> log.level = ch.qos.logback.classic.Level.DEBUG
            info -> log.level = ch.qos.logback.classic.Level.INFO
        }

        // Make the parameter globally available.
        printStackTrace = stacktrace

        if (help || jc.parsedCommand == null) {
            jc.usage()
            exitProcess(0)
        }

        // JCommander already validates the command names.
        val command = jc.commands[jc.parsedCommand]!!
        val commandObject = command.objects.first() as Runnable

        // Delegate running actions to the specified command.
        commandObject.run()
    }
}
