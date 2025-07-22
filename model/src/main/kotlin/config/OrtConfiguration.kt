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

package org.ossreviewtoolkit.model.config

import com.sksamuel.hoplite.ConfigException
import com.sksamuel.hoplite.ConfigFailure
import com.sksamuel.hoplite.ConfigLoaderBuilder
import com.sksamuel.hoplite.Constants
import com.sksamuel.hoplite.PropertySource
import com.sksamuel.hoplite.addEnvironmentSource
import com.sksamuel.hoplite.fp.getOrElse
import com.sksamuel.hoplite.indent
import com.sksamuel.hoplite.resolver.context.ContextResolverMode

import java.io.File

import org.apache.logging.log4j.kotlin.logger

import org.ossreviewtoolkit.model.Severity
import org.ossreviewtoolkit.plugins.api.PluginConfig
import org.ossreviewtoolkit.utils.common.EnvironmentVariableFilter
import org.ossreviewtoolkit.utils.ort.ORT_CUSTOM_LICENSE_TEXTS_DIRNAME
import org.ossreviewtoolkit.utils.ort.ORT_FAILURE_STATUS_CODE
import org.ossreviewtoolkit.utils.ort.ORT_PACKAGE_CONFIGURATIONS_DIRNAME
import org.ossreviewtoolkit.utils.ort.ORT_PACKAGE_CURATIONS_DIRNAME
import org.ossreviewtoolkit.utils.ort.ORT_PACKAGE_CURATIONS_FILENAME

/**
 * The configuration model for all ORT components.
 */
data class OrtConfiguration(
    /**
     * A flag to indicate whether authors should be considered as copyright holders.
     */
    val addAuthorsToCopyrights: Boolean = false,

    /**
     * A set with the names of environment variables that are explicitly allowed to be passed to child processes,
     * even if they are matched by one of the [deniedProcessEnvironmentVariablesSubstrings].
     * See [EnvironmentVariableFilter] for further details.
     */
    val allowedProcessEnvironmentVariableNames: Set<String> = EnvironmentVariableFilter.DEFAULT_ALLOW_NAMES,

    /**
     * A set with substrings to filter out environment variables before creating child processes to prevent that those
     * processes can access sensitive information. See [EnvironmentVariableFilter] for further details.
     */
    val deniedProcessEnvironmentVariablesSubstrings: Set<String> = EnvironmentVariableFilter.DEFAULT_DENY_SUBSTRINGS,

    /**
     * Enable the usage of project-local package configurations from the [RepositoryConfiguration]. If set to true,
     * apply package configurations from a local .ort.yml file before applying those specified via the command line i.e.
     * configurations from the .ort.yml take precedence.
     */
    val enableRepositoryPackageConfigurations: Boolean = false,

    /**
     * Enable the usage of project-local package curations from the [RepositoryConfiguration]. If set to true, apply
     * package curations from a local .ort.yml file before applying those specified via the command line i.e. curations
     * from the .ort.yml take precedence.
     */
    val enableRepositoryPackageCurations: Boolean = false,

    /**
     * Force overwriting of any existing output files.
     */
    val forceOverwrite: Boolean = false,

    /**
     * The license fact providers, ordered from highest to lowest priority. Defaults to the providers for bundled SPDX
     * license facts, license facts from a local ScanCode installation, and license facts from the default
     * [ORT_CUSTOM_LICENSE_TEXTS_DIRNAME].
     */
    val licenseFactProviders: LinkedHashMap<String, PluginConfig> = linkedMapOf(
        "SPDX" to PluginConfig.EMPTY,
        "ScanCode" to PluginConfig.EMPTY,
        "DefaultDir" to PluginConfig.EMPTY
    ),

    /**
     * The license file patterns.
     */
    val licenseFilePatterns: LicenseFilePatterns = LicenseFilePatterns.DEFAULT,

    /**
     * The package configuration providers to use. Defaults to the provider [ORT_PACKAGE_CONFIGURATIONS_DIRNAME]
     * configuration location.
     */
    val packageConfigurationProviders: List<ProviderPluginConfiguration> = listOf(
        ProviderPluginConfiguration(type = "DefaultDir")
    ),

    /**
     * The package curation providers to use. Defaults to providers for the default [ORT_PACKAGE_CURATIONS_FILENAME] and
     * [ORT_PACKAGE_CURATIONS_DIRNAME] configuration locations. The order of this list defines the priority of the
     * providers: Providers that appear earlier in the list can overwrite curations for the same package from providers
     * that appear later in the list.
     */
    val packageCurationProviders: List<ProviderPluginConfiguration> = listOf(
        ProviderPluginConfiguration(type = "DefaultDir"),
        ProviderPluginConfiguration(type = "DefaultFile")
    ),

    /**
     * The threshold from which on issues count as severe. Severe issues cause the status code on exit of the CLI
     * commands to be at least [ORT_FAILURE_STATUS_CODE].
     */
    val severeIssueThreshold: Severity = Severity.WARNING,

    /**
     * The threshold from which on rule violations count as severe. Severe rule violations cause the status code on exit
     * of the CLI commands to be at least [ORT_FAILURE_STATUS_CODE].
     */
    val severeRuleViolationThreshold: Severity = Severity.WARNING,

    /**
     * The configuration of the analyzer.
     */
    val analyzer: AnalyzerConfiguration = AnalyzerConfiguration(),

    /**
     * The configuration of the advisors, using the advisor's name as the key.
     */
    val advisor: AdvisorConfiguration = AdvisorConfiguration(),

    /**
     * The configuration of the downloader.
     */
    val downloader: DownloaderConfiguration = DownloaderConfiguration(),

    /**
     * The configuration of the scanner.
     */
    val scanner: ScannerConfiguration = ScannerConfiguration(),

    /**
     * The configuration of the reporter.
     */
    val reporter: ReporterConfiguration = ReporterConfiguration(),

    /**
     * The configuration of the notifier.
     */
    val notifier: NotifierConfiguration = NotifierConfiguration()
) {
    companion object {
        /**
         * Load the [OrtConfiguration]. The different sources are used with this priority:
         *
         * 1. [Command line arguments][args]
         * 2. [Configuration file][file]
         *
         * The configuration file is optional and does not have to exist. However, if it exists, but does not
         * contain a valid configuration, an [IllegalArgumentException] is thrown.
         */
        fun load(args: Map<String, String>? = null, file: File? = null): OrtConfiguration {
            val sources = listOfNotNull(
                args?.filterKeys { it.startsWith("ort.") }?.takeUnless { it.isEmpty() }?.let {
                    logger.info {
                        val argsList = it.map { (k, v) -> "\t$k=$v" }
                        "Using ORT configuration arguments:\n" + argsList.joinToString("\n")
                    }

                    PropertySource.map(it)
                },
                file?.takeIf { it.isFile }?.let {
                    logger.info { "Using ORT configuration file '${it.absolutePath}'." }

                    PropertySource.file(it)
                }
            )

            val loader = ConfigLoaderBuilder.default()
                .addEnvironmentSource()
                .addPropertySources(sources)
                .withContextResolverMode(ContextResolverMode.SkipUnresolved)
                .build()

            val configResult = loader.loadConfig<OrtConfiguration>(prefix = "ort")
            val config = configResult.getOrElse { failure ->
                val isFailureDueToEmptyConfig = failure is ConfigFailure.UndefinedTree
                    || (failure is ConfigFailure.MissingConfigValue && sources.isEmpty())

                if (!isFailureDueToEmptyConfig) {
                    val message = "Failed to load ORT configuration:\n${failure.description().indent(Constants.indent)}"
                    throw ConfigException(message)
                }

                logger.info { "All property sources were empty, falling back to the default configuration." }

                OrtConfiguration()
            }

            return config
        }
    }
}
