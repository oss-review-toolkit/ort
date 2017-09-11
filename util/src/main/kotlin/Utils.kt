package com.here.provenanceanalyzer.util

import com.fasterxml.jackson.databind.JsonNode
import com.fasterxml.jackson.databind.ObjectMapper
import com.fasterxml.jackson.databind.node.JsonNodeFactory
import com.fasterxml.jackson.dataformat.yaml.YAMLFactory
import com.fasterxml.jackson.module.kotlin.registerKotlinModule

import com.vdurmont.semver4j.Semver

import java.io.File
import java.io.IOException

@Suppress("UnsafeCast")
val log = org.slf4j.LoggerFactory.getLogger({}.javaClass) as ch.qos.logback.classic.Logger

val jsonMapper = ObjectMapper().registerKotlinModule()
val yamlMapper = ObjectMapper(YAMLFactory()).registerKotlinModule()

/**
 * Parse the standard output of a process as JSON.
 */
fun parseJsonProcessOutput(workingDir: File, vararg command: String, multiJson: Boolean = false): JsonNode {
    val process = ProcessCapture(workingDir, *command)
    if (process.exitValue() != 0) {
        throw IOException(
                "'${process.commandLine}' failed with exit code ${process.exitValue()}:\n${process.stderr()}")
    }

    // Support parsing multiple lines with one JSON object per line by wrapping the whole output into a JSON array.
    if (multiJson) {
        val array = JsonNodeFactory.instance.arrayNode()
        process.stdoutFile.readLines().forEach { array.add(jsonMapper.readTree(it)) }
        return array
    }

    return jsonMapper.readTree(process.stdout())
}

/**
 * Run a command to check it for specific version.
 */
fun requireCommandVersion(command: String, expectedVersion: Semver, versionArgument: String = "--version") {
    val version = ProcessCapture(command, versionArgument)
    if (version.exitValue() != 0) {
        throw IOException("Unable to determine the $command version:\n${version.stderr()}")
    }

    var versionString = version.stdout().trim()
    if (versionString.isEmpty()) {
        // Fall back to trying to read the version from stderr.
        versionString = version.stderr().trim()
    }

    val actualVersion = Semver(versionString, expectedVersion.type)
    if (actualVersion != expectedVersion) {
        throw IOException(
                "Unsupported $command version $actualVersion, version $expectedVersion is required.")
    }
}
