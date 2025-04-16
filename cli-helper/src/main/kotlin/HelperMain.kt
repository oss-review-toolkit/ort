/*
 * Copyright (C) 2019 The ORT Project Authors (see <https://github.com/oss-review-toolkit/ort/blob/main/NOTICE>)
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

package org.ossreviewtoolkit.helper

import ch.qos.logback.classic.Level
import ch.qos.logback.classic.Logger

import com.github.ajalt.clikt.core.CliktCommand
import com.github.ajalt.clikt.core.Context
import com.github.ajalt.clikt.core.context
import com.github.ajalt.clikt.core.main
import com.github.ajalt.clikt.core.subcommands
import com.github.ajalt.clikt.output.MordantHelpFormatter
import com.github.ajalt.clikt.parameters.options.default
import com.github.ajalt.clikt.parameters.options.flag
import com.github.ajalt.clikt.parameters.options.option
import com.github.ajalt.clikt.parameters.options.switch

import kotlin.system.exitProcess

import org.ossreviewtoolkit.helper.commands.*
import org.ossreviewtoolkit.helper.commands.classifications.LicenseClassificationsCommand
import org.ossreviewtoolkit.helper.commands.dev.DevCommand
import org.ossreviewtoolkit.helper.commands.packageconfig.PackageConfigurationCommand
import org.ossreviewtoolkit.helper.commands.packagecuration.PackageCurationsCommand
import org.ossreviewtoolkit.helper.commands.provenancestorage.ProvenanceStorageCommand
import org.ossreviewtoolkit.helper.commands.repoconfig.RepositoryConfigurationCommand
import org.ossreviewtoolkit.helper.utils.ORTH_NAME
import org.ossreviewtoolkit.utils.common.Os
import org.ossreviewtoolkit.utils.ort.printStackTrace

import org.slf4j.LoggerFactory

/**
 * The entry point for the application with [args] being the list of arguments.
 */
fun main(args: Array<String>) {
    Os.fixupUserHomeProperty()
    HelperMain().main(args)
    exitProcess(0)
}

private const val REQUIRED_OPTION_MARKER = "*"

internal class HelperMain : CliktCommand(ORTH_NAME) {
    private val logLevel by option(help = "Set the verbosity level of log output.").switch(
        "--error" to Level.ERROR,
        "--warn" to Level.WARN,
        "--info" to Level.INFO,
        "--debug" to Level.DEBUG
    ).default(Level.WARN)

    private val stacktrace by option(help = "Print out the stacktrace for all exceptions.").flag()

    init {
        context {
            helpFormatter = { MordantHelpFormatter(context = it, REQUIRED_OPTION_MARKER, showDefaultValues = true) }
        }

        subcommands(
            ConvertOrtFileCommand(),
            CreateAnalyzerResultFromPackageListCommand(),
            DevCommand(),
            ExtractRepositoryConfigurationCommand(),
            GenerateTimeoutErrorResolutionsCommand(),
            GetPackageLicensesCommand(),
            GroupScanIssuesCommand(),
            DownloadResultsFromPostgresCommand(),
            ImportCopyrightGarbageCommand(),
            ImportScanResultsCommand(),
            LicenseClassificationsCommand(),
            ListCopyrightsCommand(),
            ListLicenseCategoriesCommand(),
            ListLicensesCommand(),
            ListPackagesCommand(),
            ListStoredScanResultsCommand(),
            MapCopyrightsCommand(),
            MergeRepositoryConfigurationsCommand(),
            PackageConfigurationCommand(),
            PackageCurationsCommand(),
            ProvenanceStorageCommand(),
            RepositoryConfigurationCommand(),
            SetDependencyRepresentationCommand(),
            SetLabelsCommand(),
            TransformResultCommand(),
            VerifySourceArtifactCurationsCommand()
        )
    }

    override fun helpEpilog(context: Context) = "$REQUIRED_OPTION_MARKER denotes required options."

    override fun run() {
        // This is somewhat dirty: For logging, ORT uses the Log4j API (because of its nice Kotlin API), but Logback as
        // the implementation (for its robustness). The former API does not provide a way to set the root log level,
        // only the Log4j implementation does (via org.apache.logging.log4j.core.config.Configurator). However, the
        // SLF4J API does provide a way to get the root logger and set its level. That is why ORT's CLI additionally
        // depends on the SLF4J API, just to be able to set the root log level below.
        val rootLogger = LoggerFactory.getLogger(Logger.ROOT_LOGGER_NAME) as Logger
        rootLogger.level = logLevel

        // Make the parameter globally available.
        printStackTrace = stacktrace
    }
}
