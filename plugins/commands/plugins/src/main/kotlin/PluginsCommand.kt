/*
 * Copyright (C) 2025 The ORT Project Authors (see <https://github.com/oss-review-toolkit/ort/blob/main/NOTICE>)
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

package org.ossreviewtoolkit.plugins.commands.plugins

import com.github.ajalt.clikt.parameters.options.default
import com.github.ajalt.clikt.parameters.options.option
import com.github.ajalt.clikt.parameters.options.split
import com.github.ajalt.mordant.widgets.HorizontalRule
import com.github.ajalt.mordant.widgets.UnorderedList

import org.ossreviewtoolkit.advisor.AdviceProviderFactory
import org.ossreviewtoolkit.analyzer.PackageManagerFactory
import org.ossreviewtoolkit.downloader.VersionControlSystemFactory
import org.ossreviewtoolkit.plugins.api.OrtPlugin
import org.ossreviewtoolkit.plugins.api.PluginDescriptor
import org.ossreviewtoolkit.plugins.commands.api.OrtCommand
import org.ossreviewtoolkit.plugins.commands.api.OrtCommandFactory
import org.ossreviewtoolkit.plugins.packageconfigurationproviders.api.PackageConfigurationProviderFactory
import org.ossreviewtoolkit.plugins.packagecurationproviders.api.PackageCurationProviderFactory
import org.ossreviewtoolkit.reporter.ReporterFactory
import org.ossreviewtoolkit.scanner.ScannerWrapperFactory

@OrtPlugin(
    displayName = "Plugins",
    description = "Print information about the installed ORT plugins.",
    factory = OrtCommandFactory::class
)
class PluginsCommand(descriptor: PluginDescriptor = PluginsCommandFactory.descriptor) : OrtCommand(descriptor) {
    private val types by option(
        "--types",
        help = "A comma-separated list of plugin types to show."
    ).split(",").default(PluginType.entries.map { it.optionName })

    override fun run() {
        types.forEach { type ->
            PluginType.entries.find { it.optionName == type }?.let { pluginType ->
                renderPlugins(pluginType.title, pluginType.descriptors.value)
            }
        }
    }

    private fun renderPlugins(title: String, plugins: List<PluginDescriptor>) {
        echo(HorizontalRule(title, "="))
        echo()

        plugins.forEach { plugin ->
            echo(HorizontalRule(plugin.displayName))
            echo()

            echo("ID: ${plugin.id}")
            echo()

            echo("Description: ${plugin.description}")
            echo()

            if (plugin.options.isNotEmpty()) {
                echo("Options:")
                echo(
                    UnorderedList(
                        listEntries = plugin.options.map { option ->
                            buildString {
                                append("${option.name}: ${option.type.name}")
                                option.defaultValue?.also { append(" (Default: $it)") }
                                if (option.isRequired) append(" (Required)")
                                appendLine()
                                append(option.description)
                            }
                        }.toTypedArray(),
                        bulletText = "*"
                    )
                )
                echo()
            } else {
                echo("Options: None")
                echo()
            }
        }
    }
}

private enum class PluginType(
    val optionName: String,
    val title: String,
    val descriptors: Lazy<List<PluginDescriptor>>
) {
    ADVICE_PROVIDERS(
        "advice-providers",
        "Advice Providers",
        lazy { AdviceProviderFactory.ALL.map { it.value.descriptor } }
    ),
    COMMANDS(
        "commands",
        "CLI Commands",
        lazy { OrtCommandFactory.ALL.map { it.value.descriptor } }
    ),
    PACKAGE_CONFIGURATION_PROVIDERS(
        "package-configuration-providers",
        "Package Configuration Providers",
        lazy { PackageConfigurationProviderFactory.ALL.map { it.value.descriptor } }
    ),
    PACKAGE_CURATION_PROVIDERS(
        "package-curation-providers",
        "Package Curation Providers",
        lazy { PackageCurationProviderFactory.ALL.map { it.value.descriptor } }
    ),
    PACKAGE_MANAGERS(
        "package-managers",
        "Package Managers",
        lazy { PackageManagerFactory.ALL.map { it.value.descriptor } }
    ),
    REPORTERS(
        "reporters",
        "Reporters",
        lazy { ReporterFactory.ALL.map { it.value.descriptor } }
    ),
    SCANNERS(
        "scanners",
        "Scanners",
        lazy { ScannerWrapperFactory.ALL.map { it.value.descriptor } }
    ),
    VERSION_CONTROL_SYSTEMS(
        "version-control-systems",
        "Version Control Systems",
        lazy { VersionControlSystemFactory.ALL.map { it.value.descriptor } }
    )
}
