/*
 * Copyright (C) 2020 Bosch.IO GmbH
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

@file:Suppress("TooManyFunctions")

package org.ossreviewtoolkit.scanner.scanners

import com.fasterxml.jackson.databind.JsonNode

import org.ossreviewtoolkit.model.CopyrightResultOption
import org.ossreviewtoolkit.model.EmailResultOption
import org.ossreviewtoolkit.model.IgnoreFilterOption
import org.ossreviewtoolkit.model.IncludeFilterOption
import org.ossreviewtoolkit.model.LicenseResultOption
import org.ossreviewtoolkit.model.MetadataResultOption
import org.ossreviewtoolkit.model.OutputFormatOption
import org.ossreviewtoolkit.model.PackageResultOption
import org.ossreviewtoolkit.model.ScannerOption
import org.ossreviewtoolkit.model.ScannerOptions
import org.ossreviewtoolkit.model.SubOptionType
import org.ossreviewtoolkit.model.SubOptions
import org.ossreviewtoolkit.model.TimeoutOption
import org.ossreviewtoolkit.model.UnclassifiedOption
import org.ossreviewtoolkit.model.UrlResultOption

/**
 * A class storing information how to map a command line option to a sub option belonging to one or more option
 * classes. Using this data structure, the processing of scanner command line options can be done in a
 * declarative way.
 */
private data class SubOptionMapping(
    /** The key of the command line option this mapping is about. */
    val key: String,

    /**
     * The option classes this sub option belongs to. (There are sub options, such as _consolidate_, which affect
     * multiple options.)
     */
    val optionClasses: List<Class<*>>,

    /**
     * The data type of the sub option. This is nullable as some command line elements only trigger the creation of
     * a scanner option, but do not add a value themselves.
     */
    val subOptionType: SubOptionType? = SubOptionType.STRINGS,

    /** A name for the sub option if it differs from the command line option. */
    val subOptionName: String? = null,

    /**
     * A flag indicating whether the represented option is responsible for the creation of this scanner option.
     * Some options are used to fine-tune specific scanner features and are evaluated only if other options are
     * present. The options that must be present are marked with this flag; the option as a whole is ignored if
     * there is no such lead option.
     */
    val leadOption: Boolean = false
)

/**
 * A data class storing information about a mapped command line option together with its current value.
 */
private data class SubOptionValue(
    /** The mapping to apply for this sub option. */
    val mapping: SubOptionMapping,

    /** The value assigned to this sub option. */
    val value: String
)

/** The prefix to identify options on the ScanCode command line. */
private const val OPTION_PREFIX = "--"

/** A map assigning parameter alias names to their full option names. */
private val aliasMapping = mapOf(
    "c" to "copyright",
    "e" to "email",
    "i" to "info",
    "l" to "license",
    "n" to "processes",
    "p" to "package",
    "u" to "url"
)

/** A map listing ignore filters and their prefixes in the result. */
private val ignoreFilterMapping = mapOf(
    "ignore" to "path",
    "ignore-author" to "author",
    "ignore-copyright-holder" to "copyright"
)

/** A map defining the output options and the format they produce. */
private val outputFormatMapping = mapOf(
    "json" to "JSON",
    "json-pp" to "JSON",
    "json-lines" to "JSON",
    "csv" to "CSV",
    "html" to "HTML",
    "spdx-rdf" to "SPDX-RDF",
    "spdx-tv" to "SPDX-TV"
)

/** Key of the include filter option. */
private const val INCLUDE_FILTER_OPTION = "include"

/** The value assigned to command line switches (which do not have an explicit value). */
private const val FLAG_VALUE = "true"

/**
 * A data structure that defines how to create scanner options with sub options from elements on the command line.
 * This basically says where a specific command line option belongs to.
 */
private val subOptionMappingDeclaration = listOf(
    SubOptionMapping("copyright", listOf(CopyrightResultOption::class.java), null, leadOption = true),
    SubOptionMapping("consolidate", listOf(CopyrightResultOption::class.java, PackageResultOption::class.java)),

    SubOptionMapping("email", listOf(EmailResultOption::class.java), null, leadOption = true),
    SubOptionMapping("max-email", listOf(EmailResultOption::class.java), SubOptionType.THRESHOLD),

    SubOptionMapping("license", listOf(LicenseResultOption::class.java), null, leadOption = true),
    SubOptionMapping("is-license-text", listOf(LicenseResultOption::class.java)),
    SubOptionMapping("license-score", listOf(LicenseResultOption::class.java), SubOptionType.THRESHOLD),
    SubOptionMapping("license-text", listOf(LicenseResultOption::class.java)),
    SubOptionMapping("license-text-diagnostics", listOf(LicenseResultOption::class.java)),
    SubOptionMapping("license-url-template", listOf(LicenseResultOption::class.java)),

    SubOptionMapping("info", listOf(MetadataResultOption::class.java), null, leadOption = true),
    SubOptionMapping("mark-source", listOf(MetadataResultOption::class.java)),

    SubOptionMapping("package", listOf(PackageResultOption::class.java), null, leadOption = true),

    SubOptionMapping(
        "timeout",
        listOf(TimeoutOption::class.java),
        SubOptionType.THRESHOLD,
        leadOption = true,
        subOptionName = SubOptions.DEFAULT_KEY
    ),

    SubOptionMapping("url", listOf(UrlResultOption::class.java), null, leadOption = true),
    SubOptionMapping("max-url", listOf(UrlResultOption::class.java), SubOptionType.THRESHOLD),

    notRelevantOption("verbose"),
    notRelevantOption("quiet"),
    notRelevantOption("processes"),
    notRelevantOption("reindex-licenses"),
    notRelevantOption("max-in-memory"),

    // detailed output options are considered not relevant, but the format is retained in an OutputFormatOption
    notRelevantOption("json"),
    notRelevantOption("json-pp"),
    notRelevantOption("json-lines"),
    notRelevantOption("csv"),
    notRelevantOption("html"),
    notRelevantOption("spdx-rdf"),
    notRelevantOption("spdx-tv")
)

/**
 * Stores mapping information to generate sub option values for command line options.
 */
private val subOptionMappings = subOptionMappingDeclaration.associateBy { it.key }

/**
 * A mapping for a synthetic lead option of unclassified options. The scanner options always contain a (potentially)
 * empty unclassified option. Its creation is triggered by this mapping.
 */
private val unclassifiedLeadMapping =
    SubOptionValue(SubOptionMapping("", listOf(UnclassifiedOption::class.java), null, leadOption = true), "")

/**
 * An option with the default ScanCode timeout. This option is added if no timeout is explicitly specified.
 */
private val defaultTimeoutOption = TimeoutOption(
    SubOptions.create { putThresholdOption(120.0) }
)

/**
 * Generate a [SubOptionMapping] for an option that is no relevant for the results produced by a scanner.
 */
private fun notRelevantOption(key: String): SubOptionMapping =
    SubOptionMapping(key, listOf(UnclassifiedOption::class.java), SubOptionType.STRINGS_IGNORE)

/**
 * Generate a [ScannerOptions] object from the command line options passed in. The options are already separated
 * into options that are [always applied][commandLine] and options to be used in [debug mode][debugCommandLine];
 * the latter are taken into account only if the [includeDebug] flag is *true*.
 */
internal fun parseScannerOptionsFromCommandLine(
    commandLine: List<String>,
    debugCommandLine: List<String>,
    includeDebug: Boolean
): ScannerOptions {
    val commands = commandLine.toMutableList()
    if (includeDebug) {
        commands.addAll(debugCommandLine)
    }

    val options = commands.deAlias().combineOptionValues()
    val (filterOptions, subOptions) = options.partition(::isFilterOption)

    val optionsSet = mutableSetOf<ScannerOption>()
    optionsSet.addAll(generateFilterOptions(filterOptions))
    optionsSet.addAll(generateOutputOptions(subOptions))
    optionsSet.addAll(generateSubOptions(subOptions))
    addDefaultTimeoutIfUnspecified(optionsSet)
    return ScannerOptions(optionsSet)
}

/**
 * Generate a [ScannerOptions] objects from the command line representation of a stored ScanCode result represented
 * by the [root JSON node][options] containing this data.
 */
internal fun parseScannerOptionsFromResult(options: JsonNode?): ScannerOptions {
    val commandLine = options?.let { optionsNode ->
        optionsNode.fieldNames().asSequence().fold(mutableListOf<String>()) { list, opt ->
            val node = optionsNode[opt]
            when {
                node.isBoolean ->
                    if (node.asBoolean()) {
                        list.add(opt)
                    }

                node.isEmpty -> {
                    list.add(opt)
                    list.add(node.asText())
                }

                else ->
                    node.forEach {
                        list.add(opt)
                        list.add(it.asText())
                    }
            }

            list
        }
    } ?: emptyList()

    return parseScannerOptionsFromCommandLine(commandLine, emptyList(), includeDebug = false)
}

/**
 * Traverse a list with command line options and group the options and their values together.
 */
private fun List<String>.combineOptionValues(): List<Pair<String, String>> {
    val result = mutableListOf<Pair<String, String>>()
    val remainingKey = fold<String, String?>(null) { lastKey, cmd ->
        if (cmd.startsWith(OPTION_PREFIX)) {
            if (lastKey != null) {
                result.add(lastKey to FLAG_VALUE)
            }
            cmd.substring(OPTION_PREFIX.length)
        } else {
            result.add((lastKey ?: "") to cmd)
            null
        }
    }

    if (remainingKey != null) {
        result.add(remainingKey to FLAG_VALUE)
    }

    return result
}

/**
 * Replace short alias names in a list with ScanCode command line options by their full option names to allow
 * uniform access. Note that ScanCode supports combining multiple short aliases to a single parameter; e.g. a
 * parameter like "-ipu" is equivalent to the options "--info --package --url".
 */
private fun List<String>.deAlias() =
    fold(mutableListOf<String>()) { lst, cmd ->
        if (cmd.startsWith("-") && !cmd.startsWith(OPTION_PREFIX)) {
            cmd.substring(1).forEach { alias ->
                val option = OPTION_PREFIX + (aliasMapping[alias.toString()] ?: alias)
                lst.add(option)
            }
        } else {
            lst.add(cmd)
        }
        lst
    }

/**
 * Determine whether the command line option represented by [pair] defines a filter.
 */
private fun isFilterOption(pair: Pair<String, String>): Boolean =
    pair.first == INCLUDE_FILTER_OPTION || ignoreFilterMapping.containsKey(pair.first)

/**
 * Generate scanner options for inclusion and exclusion filters defined in the given list of [options][filterOptions].
 */
private fun generateFilterOptions(filterOptions: List<Pair<String, String>>): List<ScannerOption> {
    val includeFilters = filterOptions.filter { it.first == INCLUDE_FILTER_OPTION }
        .map { it.second }
        .toSet()
    val includeFilterOption = includeFilters.takeIf { it.isNotEmpty() }?.let(::IncludeFilterOption)

    val ignoreFilters = filterOptions.mapNotNull { option ->
        ignoreFilterMapping[option.first]?.let { "$it:${option.second}" }
    }.toSet()
    val ignoreFilterOption = ignoreFilters.takeIf { it.isNotEmpty() }?.let(::IgnoreFilterOption)

    return listOfNotNull(includeFilterOption, ignoreFilterOption)
}

/**
 * Generate scanner options defining the output format of the result for the given [options]. Note: Currently only a
 * single output format is handled.
 */
private fun generateOutputOptions(options: List<Pair<String, String>>): List<ScannerOption> =
    options.mapNotNull { outputFormatMapping[it.first] }
        .map { format ->
            OutputFormatOption(
                SubOptions.create { putStringOption(format) }
            )
        }

/**
 * Generate [ScannerOption] instances for the given list of [options].
 */
private fun generateSubOptions(options: List<Pair<String, String>>): List<ScannerOption> =
    mapSubOptions(options)
        .filter { entry -> entry.value.any { it.mapping.leadOption } }
        .map { entry -> createOptionWithSubOptions(entry.key, toSubOptions(entry.value)) }

/**
 * Process the generated list of [options] and generate an intermediate map with information about sub options that
 * correspond to these options.
 */
private fun mapSubOptions(options: List<Pair<String, String>>): Map<Class<*>, List<SubOptionValue>> {
    val initialMapping: MutableMap<Class<*>, MutableList<SubOptionValue>> =
        mutableMapOf(UnclassifiedOption::class.java to mutableListOf(unclassifiedLeadMapping))

    return options.fold(initialMapping) { map, pair ->
        val subOptionValue = SubOptionValue(pair.subOptionMapping(), pair.second)
        subOptionValue.mapping.optionClasses.forEach { optionClass ->
            map.putIfAbsent(optionClass, mutableListOf())
            map[optionClass]?.add(subOptionValue)
        }
        map
    }
}

/**
 * Transform the given list with sub option [values] to a [SubOptions] object.
 */
private fun toSubOptions(values: List<SubOptionValue>): SubOptions =
    SubOptions.create {
        values.filter { it.mapping.subOptionType != null }
            .forEach { value ->
                val key = value.mapping.subOptionName ?: value.mapping.key
                when (value.mapping.subOptionType) {
                    SubOptionType.THRESHOLD ->
                        putThresholdOption(value.value.toDouble(), key)
                    SubOptionType.STRINGS, SubOptionType.STRINGS_IGNORE ->
                        putStringOption(value.value, key, value.mapping.subOptionType.relevant)
                }
            }
    }

/**
 * Create a new scanner option with sub options of the [optionClass] provided that is initialized with the given
 * [subOptions].
 */
private fun createOptionWithSubOptions(optionClass: Class<*>, subOptions: SubOptions): ScannerOption {
    val ctor = optionClass.getConstructor(SubOptions::class.java)
    return ctor.newInstance(subOptions) as ScannerOption
}

/**
 * Return a [SubOptionMapping] for the option represented by this pair.
 */
private fun Pair<String, String>.subOptionMapping() =
    subOptionMappings[first] ?: SubOptionMapping(first, listOf(UnclassifiedOption::class.java), SubOptionType.STRINGS)

/**
 * Make sure that the given [optionsSet] contains a timeout option. Add the default timeout option if necessary.
 */
private fun addDefaultTimeoutIfUnspecified(optionsSet: MutableSet<ScannerOption>) {
    if (!optionsSet.any { it is TimeoutOption }) {
        optionsSet.add(defaultTimeoutOption)
    }
}
