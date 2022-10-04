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
import com.github.ajalt.clikt.core.context
import com.github.ajalt.clikt.core.subcommands
import com.github.ajalt.clikt.output.CliktHelpFormatter
import com.github.ajalt.clikt.parameters.options.default
import com.github.ajalt.clikt.parameters.options.flag
import com.github.ajalt.clikt.parameters.options.option
import com.github.ajalt.clikt.parameters.options.switch

import org.ossreviewtoolkit.helper.commands.ConvertOrtFileCommand
import org.ossreviewtoolkit.helper.commands.CreateAnalyzerResultCommand
import org.ossreviewtoolkit.helper.commands.DownloadResultsFromPostgresCommand
import org.ossreviewtoolkit.helper.commands.ExtractRepositoryConfigurationCommand
import org.ossreviewtoolkit.helper.commands.GenerateTimeoutErrorResolutionsCommand
import org.ossreviewtoolkit.helper.commands.GetPackageLicensesCommand
import org.ossreviewtoolkit.helper.commands.ImportCopyrightGarbageCommand
import org.ossreviewtoolkit.helper.commands.ImportScanResultsCommand
import org.ossreviewtoolkit.helper.commands.ListCopyrightsCommand
import org.ossreviewtoolkit.helper.commands.ListLicenseCategoriesCommand
import org.ossreviewtoolkit.helper.commands.ListLicensesCommand
import org.ossreviewtoolkit.helper.commands.ListPackagesCommand
import org.ossreviewtoolkit.helper.commands.ListStoredScanResultsCommand
import org.ossreviewtoolkit.helper.commands.MapCopyrightsCommand
import org.ossreviewtoolkit.helper.commands.MergeRepositoryConfigurationsCommand
import org.ossreviewtoolkit.helper.commands.SetDependencyRepresentationCommand
import org.ossreviewtoolkit.helper.commands.SetLabelsCommand
import org.ossreviewtoolkit.helper.commands.SubtractScanResultsCommand
import org.ossreviewtoolkit.helper.commands.TransformResultCommand
import org.ossreviewtoolkit.helper.commands.VerifySourceArtifactCurationsCommand
import org.ossreviewtoolkit.helper.commands.packageconfig.PackageConfigurationCommand
import org.ossreviewtoolkit.helper.commands.packagecuration.PackageCurationsCommand
import org.ossreviewtoolkit.helper.commands.repoconfig.RepositoryConfigurationCommand
import org.ossreviewtoolkit.helper.commands.scanstorage.ScanStorageCommand
import org.ossreviewtoolkit.helper.utils.ORTH_NAME
import org.ossreviewtoolkit.utils.ort.printStackTrace

import org.slf4j.LoggerFactory

/**
 * The entry point for the application with [args] being the list of arguments.
 */
fun main(args: Array<String>) {
    return HelperMain().main(args)
}

internal class HelperMain : CliktCommand(name = ORTH_NAME, epilog = "* denotes required options.") {
    private val logLevel by option(help = "Set the verbosity level of log output.").switch(
        "--debug" to Level.DEBUG,
        "--error" to Level.ERROR,
        "--info" to Level.INFO,
        "--warn" to Level.WARN
    ).default(Level.WARN)

    private val stacktrace by option(help = "Print out the stacktrace for all exceptions.").flag()

    init {
        context {
            expandArgumentFiles = false
            helpFormatter = CliktHelpFormatter(showDefaultValues = true)
        }

        subcommands(
            ConvertOrtFileCommand(),
            CreateAnalyzerResultCommand(),
            ExtractRepositoryConfigurationCommand(),
            GenerateTimeoutErrorResolutionsCommand(),
            GetPackageLicensesCommand(),
            DownloadResultsFromPostgresCommand(),
            ImportCopyrightGarbageCommand(),
            ImportScanResultsCommand(),
            ListCopyrightsCommand(),
            ListLicenseCategoriesCommand(),
            ListLicensesCommand(),
            ListPackagesCommand(),
            ListStoredScanResultsCommand(),
            MapCopyrightsCommand(),
            MergeRepositoryConfigurationsCommand(),
            PackageConfigurationCommand(),
            PackageCurationsCommand(),
            RepositoryConfigurationCommand(),
            ScanStorageCommand(),
            SetDependencyRepresentationCommand(),
            SetLabelsCommand(),
            SubtractScanResultsCommand(),
            TransformResultCommand(),
            VerifySourceArtifactCurationsCommand()
        )
    }

    override fun run() {
        val rootLogger = LoggerFactory.getLogger(Logger.ROOT_LOGGER_NAME) as Logger
        rootLogger.level = logLevel

        // Make the parameter globally available.
        printStackTrace = stacktrace
    }
}
