package com.here.ort.model

/**
 * An enumeration of supported output file formats.
 */
enum class OutputFormat(val fileEnding: String) {
    /**
     * Specifies the [JSON](http://www.json.org/) format.
     */
    JSON("json"),

    /**
     * Specifies the [YAML](http://yaml.org/) format.
     */
    YAML("yml")
}

val ALL_OUTPUT_FORMATS = OutputFormat.values().asList()
