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

package org.ossreviewtoolkit.model

import com.fasterxml.jackson.annotation.JsonIgnore
import com.fasterxml.jackson.annotation.JsonTypeInfo
import com.fasterxml.jackson.databind.JsonNode
import com.fasterxml.jackson.databind.node.ObjectNode

/**
 * A base class representing an option to configure a scanner execution.
 *
 * The subclasses of this class represent logic options that influence the result produced by a scanner - independent
 * on a concrete scanner implementation. Such implementations have to map their specific options to these logic
 * classes. This allows the results of different scanner implementations to be compared for compatibility. When new
 * scanner implementations with new features and options are added this hierarchy has to be extended accordingly.
 * The primary use case of these classes is to determine whether a result found in a scan result storage can be used
 * instead of running a scanner again with a specific set of options.
 *
 * Because scanner options have a specific semantic it is in most cases not sufficient to match them exactly.
 * Therefore, this class defines a function that does a compatibility check. Here classes for concrete options can
 * implement their specific logic.
 *
 * The matching logic supports a strict and a lenient mode to cover different use cases. The strict mode guarantees
 * that scan results are considered compatible only if they contain (at least) all the data requested by the
 * options provided in the exact form. This is suitable if a specific scanner is used and the processing of the scan
 * result is sensitive to the concrete data it contains. In lenient mode in contrast a user merely specifies in what
 * kind of data he or she is interested (such as license or copyright information), but does not care from which
 * scanner this data was produced or how it might have been fine-tuned.
 */
@JsonTypeInfo(use = JsonTypeInfo.Id.MINIMAL_CLASS)
sealed class ScannerOption {
    /**
     * Check whether this option is compatible with the given set of [options] doing either a strict or lenient
     * comparison depending on the [strict] flag.
     */
    abstract fun isCompatible(options: ScannerOptions, strict: Boolean = true): Boolean
}

/**
 * An enumeration defining supported types of sub options.
 *
 * This enumeration defines a logic grouping of sub options for scanner options, so that they can be handled correctly
 * in compatibility checks. The idea behind this class is that sub options have a complex semantic and thus cannot be
 * compared one to one to determine whether they are equivalent or compatible. Therefore, this enumeration defines a
 * (simple) model of sub options that can also be used to determine how to compare them.
 *
 * For instance, the ScanCode scanner has an option to include emails found in the source code into its result. There
 * is a sub option to define a maximum number of emails to return. When doing a compatibility check, this maximum
 * number does not need to match exactly, but it is sufficient if an existing result has at least the number of emails
 * requested.
 */
enum class SubOptionType(
    /**
     * A flag that determines whether sub options of this type are relevant for compatibility checks. If set to
     * *false*, options with this type are ignored.
     */
    val relevant: Boolean = true
) {
    /**
     * A type for sub options that affect the scanner result and need to be compared exactly when checking for
     * compatibility of options. When constructing a [SubOptions] instance such options should be brought in a
     * canonical form, so that a direct comparison is meaningful.
     */
    STRINGS,

    /**
     * A type for sub options that do not affect the scanner result. Such options can be contained for informational
     * purpose, but they are not taken into account for compatibility checks.
     */
    STRINGS_IGNORE(relevant = false),

    /**
     * A type for sub options that represent a threshold value. Typically, elements are added to the result when they
     * exceed this threshold. When comparing sub options of this type a less or equal semantic is applied.
     */
    THRESHOLD;

    companion object {
        private val mapping = values().associateBy { it.name }

        /**
         * Return the enum value with the specified [name] or default to the (most restrictive) [STRINGS] type when
         * the name cannot be resolved.
         */
        fun forName(name: String): SubOptionType = mapping[name] ?: STRINGS
    }
}

/**
 * A data class representing additional sub options that are used to fine-tune the behaviour of a scanner in a
 * specific area.
 *
 * Some scanner implementations use options to enable or disable certain features and sub options to further control
 * the results produced by this feature. For instance, ScanCode has an option that determines whether license
 * findings should be generated. There are then a number of related options to specify the exact amount of license
 * information to retrieve.
 *
 * Depending on the mode for compatibility checks - strict or lenient -, sub options may or may not be relevant.
 * Often their values are not strings, but need to be interpreted in a specific way.
 */
data class SubOptions(val values: ObjectNode) {
    companion object {
        /**
         * Constant for the key representing the most important value of an option with multiple sub options.
         */
        const val DEFAULT_KEY = "value"

        class Builder {
            /** The node to populate by this builder. */
            val node: ObjectNode = jsonMapper.createObjectNode()

            /**
             * Add a plain string option with the given [key] and [value] to this object. It can be specified
             * whether this option is [relevant] for compatibility checks.
             */
            fun putStringOption(value: String, key: String = DEFAULT_KEY, relevant: Boolean = true): Builder {
                val optionType = if (relevant) SubOptionType.STRINGS else SubOptionType.STRINGS_IGNORE
                return putOption(optionType, key, node.textNode(value))
            }

            /**
             * Add an option of type [SubOptionType.THRESHOLD] with the given [key] and [value] to this object.
             */
            fun putThresholdOption(value: Double, key: String = DEFAULT_KEY): Builder =
                putOption(SubOptionType.THRESHOLD, key, node.numberNode(value))

            /**
             * Add the [key] with the given [value] of the [type] specified to this object. There is an object
             * node for each type supported. The actual values are stored as fields of this node.
             */
            private fun putOption(type: SubOptionType, key: String, value: JsonNode): Builder {
                val objNode = (node[type.name] ?: node.putObject(type.name)) as ObjectNode
                objNode.set<JsonNode>(key, value)
                return this
            }
        }

        /**
         * Create a new instance of [SubOptions] supporting the convenient creation of the JSON node with the
         * properties. The passed in [creator] lambda can just invoke _put()_ to populate the node.
         */
        fun create(creator: Builder.() -> Unit): SubOptions {
            val builder = Builder()
            builder.creator()
            return SubOptions(builder.node)
        }

        /**
         * Return a comparator function to check the compatibility of two corresponding sub options. Depending on the
         * [type] different comparator functions are applied.
         */
        private fun comparatorFor(type: SubOptionType): (SubOptionType, String, SubOptions, SubOptions) -> Boolean =
            when (type) {
                SubOptionType.STRINGS_IGNORE -> { _, _, _, _ -> true }
                SubOptionType.THRESHOLD -> ::compareThresholds
                else -> ::compareStrings
            }

        /**
         * Compare the sub option values with the given [key] from [subOptions1] and [subOptions2], which must match
         * exactly.
         */
        private fun compareStrings(
            type: SubOptionType,
            key: String,
            subOptions1: SubOptions,
            subOptions2: SubOptions
        ): Boolean =
            subOptions1[type, key] == subOptions2[type, key]

        /**
         * Compare the sub option values with the given [key] from [subOptions1] and [subOptions2] using a numeric
         * less or equal comparison.
         */
        private fun compareThresholds(
            type: SubOptionType,
            key: String,
            subOptions1: SubOptions,
            subOptions2: SubOptions
        ): Boolean {
            val value1 = subOptions1[type, key]?.asDouble(Double.MAX_VALUE) ?: Double.MAX_VALUE
            val value2 = subOptions2[type, key]?.asDouble(Double.MIN_VALUE) ?: Double.MIN_VALUE
            return value1 <= value2
        }
    }

    /**
     * Return a [JsonNode] with the value of the sub option with the given [key] and [type].
     */
    operator fun get(type: SubOptionType, key: String): JsonNode? =
        values[type.name]?.get(key)

    /**
     * Test the compatibility of the options contained in this object with the ones in [other]. All the keys in
     * this object must be present in [other] with compatible values. In addition, [other] must not contain any
     * other relevant options.
     */
    fun isCompatible(other: SubOptions): Boolean =
        values.fieldNames().asSequence().all { key ->
            val optionType = SubOptionType.forName(key)
            val comparator = comparatorFor(optionType)
            val optionsNode = values[key]
            optionsNode.fieldNames().asSequence().all { comparator(optionType, it, this, other) }
        } && relevantOptions() == other.relevantOptions()

    /**
     * Return a set with sub option keys that are relevant for compatibility checks.
     */
    private fun relevantOptions(): Set<String> =
        values.fieldNames().asSequence()
            .filter { SubOptionType.forName(it).relevant }
            .flatMap { values[it].fieldNames().asSequence() }
            .toSet()
}

/**
 * A data class representing the (logic) options of a scanner.
 *
 * This class holds a set of [ScannerOption] objects and offers some query methods on it.
 */
data class ScannerOptions(val options: Set<ScannerOption>) {
    /**
     * A mapping for fast access to options by their option class.
     */
    @get:JsonIgnore
    val optionsByClass by lazy { options.associateBy { it.javaClass } }

    /**
     * Return the [ScannerOption] of a specific type from this object or *null* if it is not present.
     */
    inline fun <reified T : ScannerOption> getOption(): T? = getOption(T::class.java)

    /**
     * Return the [ScannerOption] with the given [optionClass] from this object or *null* if it is not present.
     */
    @Suppress("TYPE_INFERENCE_ONLY_INPUT_TYPES_WARNING")
    fun <T : ScannerOption> getOption(optionClass: Class<T>): T? =
        optionClass.cast(optionsByClass[optionClass])

    /**
     * Return the [ScannerOption] of a specific type from this object or the given [default] option if it is not
     * present.
     */
    inline fun <reified T : ScannerOption> getOption(default: T): T =
        getOption(T::class.java, default)

    /**
     * Return the [ScannerOption] with the given [optionClass] from this object or the given [default] option if it
     * is not present.
     */
    fun <T : ScannerOption> getOption(optionClass: Class<T>, default: T): T =
        getOption(optionClass) ?: default

    /**
     * Return a flag whether this object contains an option of a specific type.
     */
    inline fun <reified T : ScannerOption> contains(): Boolean = contains(T::class.java)

    /**
     * Return a flag whether this object contains an option of the specific [optionClass].
     */
    fun <T : ScannerOption> contains(optionClass: Class<T>): Boolean = getOption(optionClass) != null

    /**
     * Execute a [predicate][test] on the option with the given type. Return the result of the predicate or
     * *false* if there is no option with this type.
     */
    inline fun <reified T : ScannerOption> checkOption(test: (T) -> Boolean): Boolean =
        checkOption(T::class.java, test)

    /**
     * Execute a [predicate][test] on the option with the given [optionClass]. Return the result of the predicate or
     * *false* if there is no option with this class.
     */
    inline fun <T : ScannerOption> checkOption(optionClass: Class<T>, test: (T) -> Boolean): Boolean =
        getOption(optionClass)?.let { test(it) } ?: false

    /**
     * Execute a compatibility check on the option with the given type that depends on the [strict] mode flag.
     * If the flag is *true*, an option of this type must be present and satisfy the [predicate][test]
     * provided. Otherwise, an option with this type only needs to be present.
     */
    inline fun <reified T : ScannerOption> checkPresenceOrStrict(strict: Boolean, test: (T) -> Boolean): Boolean =
        checkPresenceOrStrict(T::class.java, strict, test)

    /**
     * Execute a compatibility check on the option with the given [optionClass] that depends on the [strict] mode
     * flag. If the flat is *true*, an option of this class must be present and satisfy the [predicate][test]
     * provided. Otherwise, an option of this class only needs to be present.
     */
    inline fun <T : ScannerOption> checkPresenceOrStrict(
        optionClass: Class<T>,
        strict: Boolean,
        test: (T) -> Boolean
    ): Boolean =
        (strict && checkOption(optionClass, test)) || (!strict && contains(optionClass))

    /**
     * Check whether scan results produced by a scanner run with the options stored in this instance are a subset of
     * the results produced by the options in [other], taking the given [strict] flag into account. This function
     * can be used to check whether a stored scan result with specific options can be used instead of running the
     * scanner again. This basically means that the result contains at least the information the new scanner run
     * would produce.
     */
    fun isSubsetOf(other: ScannerOptions, strict: Boolean): Boolean {
        return options.all { it.isCompatible(other, strict) }
    }
}

/**
 * A base class to represent complex scanner options that can have themselves sub options. This class manages the
 * sub options and implement the compatibility check based on them.
 */
sealed class ScannerOptionWithSubOptions<T : ScannerOptionWithSubOptions<T>>(
    /**
     * Stores the class of this option. This is required to access the correct counterpart from a [ScannerOptions]
     * object during a compatibility check.
     */
    val optionClass: Class<T>
) : ScannerOption() {

    /** The sub options defined for this option. */
    abstract val subOptions: SubOptions

    /**
     * Check whether this option is compatible with the given set of [options] doing either a strict or lenient
     * comparison depending on the [strict] flag. This implementation checks whether the [options] contain an
     * instance of this [optionClass] with equivalent [subOptions].
     */
    override fun isCompatible(options: ScannerOptions, strict: Boolean): Boolean =
        options.checkPresenceOrStrict(optionClass, strict) {
            subOptions.isCompatible(it.subOptions)
        }
}

/**
 * A scanner option determining the format in which the output is generated. Some scanners support different
 * output formats; therefore, there are [SubOptions] allowing the exact format to be specified.
 */
data class OutputFormatOption(override val subOptions: SubOptions) :
    ScannerOptionWithSubOptions<OutputFormatOption>(OutputFormatOption::class.java)

/**
 * A scanner option determining if the scanner result contains copyright information. The exact amount of copyright
 * information is controlled by further sub options.
 */
data class CopyrightResultOption(override val subOptions: SubOptions) :
    ScannerOptionWithSubOptions<CopyrightResultOption>(CopyrightResultOption::class.java)

/**
 * A scanner option determining if the scanner should scan sources for emails and return them in the results.
 * Further configuration can be done via sub options.
 */
data class EmailResultOption(override val subOptions: SubOptions) :
    ScannerOptionWithSubOptions<EmailResultOption>(EmailResultOption::class.java)

/**
 * A scanner option determining if the scanner should include license findings in the results. Further configuration
 * can be done via sub options.
 */
data class LicenseResultOption(override val subOptions: SubOptions) :
    ScannerOptionWithSubOptions<LicenseResultOption>(LicenseResultOption::class.java)

/**
 * A scanner option determining if the scanner should include metadata about source files (such as size, programming
 * language, etc.) in the results. Further configuration can be done via sub options.
 */
data class MetadataResultOption(override val subOptions: SubOptions) :
    ScannerOptionWithSubOptions<MetadataResultOption>(MetadataResultOption::class.java)

/**
 * A scanner option determining if the scanner result contains package information. The exact amount of package
 * information is controlled by further sub options.
 */
data class PackageResultOption(override val subOptions: SubOptions) :
    ScannerOptionWithSubOptions<PackageResultOption>(PackageResultOption::class.java)

/**
 * A scanner option determining if the scanner should extract URLs from sources and return them in the results.
 * Further configuration can be done via sub options.
 */
data class UrlResultOption(override val subOptions: SubOptions) :
    ScannerOptionWithSubOptions<UrlResultOption>(UrlResultOption::class.java)

/**
 * A scanner option allowing the exclusion of certain results based on a list of patterns.
 *
 * Concrete scanner implementations may actually support multiple options of this kind to configure exclusions for
 * different element types. For instance, ScanCode has the options _ignore-author_, _ignore-copyright-holder_, and
 * _ignore_ (for file patterns). For this generic model, the different kinds of filters can be represented by
 * adding specific prefixes to the list of patterns.
 *
 * Note that patterns cannot actually be interpreted; they are compared using a string comparison, and it is not
 * understood that one pattern may be an equivalent or more generic form of another.
 */
data class IgnoreFilterOption(val patterns: Set<String>) : ScannerOption() {
    /**
     * Check whether the ignore filter patterns defined by the given set of [options] are compatible with the patterns
     * contained in this option. In [strict] mode, the patterns must match exactly (ignoring order). If [strict] is
     * *false*, the check succeeds if the patterns defined within [options] are a subset of the patterns defined here.
     * (This means that the other option ignores less elements, and thus the result is more comprehensive.)
     */
    override fun isCompatible(options: ScannerOptions, strict: Boolean): Boolean {
        val otherIgnores = options.getOption<IgnoreFilterOption>()
        return if (strict) {
            patterns == otherIgnores?.patterns
        } else {
            if (otherIgnores == null) {
                !options.contains<IncludeFilterOption>()
            } else {
                patterns.containsAll(otherIgnores.patterns)
            }
        }
    }
}

/**
 * A scanner option specifying inclusion criteria of certain results based on a list of patterns.
 *
 * This option is similar to [IgnoreFilterOption], but instead of defining exclusion criteria, it defines the
 * scanner's result based on inclusions.
 */
data class IncludeFilterOption(val patterns: Set<String>) : ScannerOption() {
    /**
     * Check whether the include filter patterns defined by the given set of [options] are compatible with the patterns
     * contained in this option. In [strict] mode, the patterns must match exactly (ignoring order). If [strict] is
     * *false*, the check succeeds if the patterns defined within [options] are a super set of the patterns defined
     * here. (This means that the result produced by the other scanner options would contain at least all the data
     * generated by this inclusions.)
     */
    override fun isCompatible(options: ScannerOptions, strict: Boolean): Boolean {
        val otherIncludes = options.getOption<IncludeFilterOption>()
        return if (strict) {
            patterns == otherIncludes?.patterns
        } else {
            otherIncludes?.patterns?.containsAll(patterns) ?: !options.contains<IgnoreFilterOption>()
        }
    }
}

/**
 * A special scanner option defining a timeout for scanner runs.
 *
 * Some scanner implementations cancel the current scan operation if it takes too long. Then only limited results
 * may be available. This implementation handles this case using the comparison logic provided by the
 * [SubOptionType.THRESHOLD] type. The basic idea is that a scanner configured with a higher timeout is expected to
 * produce more results than a scanner with a lower timeout.
 *
 * To make sure that this logic actually work as expected, scanner implementations supporting such a timeout should
 * always include this option, even if it is not explicitly specified on the command line (with a default value then).
 */
data class TimeoutOption(override val subOptions: SubOptions) :
    ScannerOptionWithSubOptions<TimeoutOption>(TimeoutOption::class.java)

/**
 * A scanner option acting as a container for other options that do not fall into a category which is handled by
 * other classes.
 *
 * The semantic of this option is that it contains values and flags that impact scanner results and must match
 * exactly for the results to be compatible. The [SubOptionType.STRINGS_IGNORE] can include other options not
 * relevant for the results produced by the scanner; they are recorded for informational purposes.
 */
data class UnclassifiedOption(override val subOptions: SubOptions) :
    ScannerOptionWithSubOptions<UnclassifiedOption>(UnclassifiedOption::class.java) {
    /**
     * Check whether the sub options contained in this option are compatible with the ones stored in [options].
     * This implementation does always a [strict] comparison because the sub options managed by this class are
     * relevant for the results produced by a scanner.
     */
    override fun isCompatible(options: ScannerOptions, strict: Boolean): Boolean {
        return super.isCompatible(options, strict = true)
    }
}
