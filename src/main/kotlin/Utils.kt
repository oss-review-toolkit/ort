package com.here.provenanceanalyzer

import com.fasterxml.jackson.databind.JsonNode
import com.fasterxml.jackson.databind.ObjectMapper
import com.fasterxml.jackson.databind.node.JsonNodeFactory

import com.here.provenanceanalyzer.managers.NPM

import com.vdurmont.semver4j.Semver

import java.io.File
import java.io.IOException
import java.net.URI

/**
 * Operating-System-specific utility functions.
 */
object OS {
    private val OS_NAME = System.getProperty("os.name").toLowerCase()

    val isWindows = OS_NAME.contains("windows")
}

/**
 * An (almost) drop-in replacement for ProcessBuilder that is able to capture huge outputs to the standard output and
 * standard error streams by redirecting output to temporary files.
 */
class ProcessCapture(workingDir: File?, vararg command: String) {
    constructor(vararg command: String) : this(null, *command)

    val commandLine = command.joinToString(" ")

    @Suppress("UnsafeCallOnNullableType")
    val stdoutFile = File.createTempFile(command.first(), ".stdout")!!

    @Suppress("UnsafeCallOnNullableType")
    val stderrFile = File.createTempFile(command.first(), ".stderr")!!

    private val builder = ProcessBuilder(*command)
            .directory(workingDir)
            .redirectOutput(stdoutFile)
            .redirectError(stderrFile)

    private val process = builder.start()

    init {
        if (log.isDebugEnabled) {
            log.debug("Keeping temporary files:")
            log.debug(stdoutFile.absolutePath)
            log.debug(stderrFile.absolutePath)
        } else {
            stdoutFile.deleteOnExit()
            stderrFile.deleteOnExit()
        }

        process.waitFor()
    }

    /**
     * Return the exit value of the terminated process.
     */
    fun exitValue() = process.exitValue()

    /**
     * Return the standard output stream of the terminated process as a string.
     */
    fun stdout() = stdoutFile.readText()

    /**
     * Return the standard errors stream of the terminated process as a string.
     */
    fun stderr() = stderrFile.readText()
}

/**
 * Parse the standard output of a process as JSON.
 */
fun parseJsonProcessOutput(workingDir: File, vararg command: String): JsonNode {
    val process = ProcessCapture(workingDir, *command)
    if (process.exitValue() != 0) {
        throw IOException(
                "'${process.commandLine}' failed with exit code ${process.exitValue()}:\n${process.stderr()}")
    }

    val mapper = ObjectMapper()

    // Wrap yarn's output, which is one JSON object per line, so the output as a whole can be parsed as a JSON array.
    if (command.first() == NPM.yarn && command.contains("--json")) {
        val array = JsonNodeFactory.instance.arrayNode()
        process.stdoutFile.readLines().forEach { array.add(mapper.readTree(it)) }
        return array
    }

    return mapper.readTree(process.stdout())
}

/**
 * Normalize a VCS URL by converting it to a common pattern. For example NPM defines some shortcuts for GitHub or GitLab
 * URLs which are converted to full URLs so that they can be used in a common way.
 *
 * @param vcsUrl The URL to normalize.
 * @param semverType Required to convert package manager specific shortcuts.
 */
fun normalizeVcsUrl(vcsUrl: String, semverType: Semver.SemverType) :String {
    // A hierarchical URI looks like
    //     [scheme:][//authority][path][?query][#fragment]
    // where a server-based "authority" has the syntax
    //     [user-info@]host[:port]
    val uri = URI(vcsUrl)

    if (semverType == Semver.SemverType.NPM) {
        // https://docs.npmjs.com/files/package.json#repository
        val path = uri.schemeSpecificPart
        if (path != null) {
            if (uri.authority == null && uri.query == null && uri.fragment == null) {
                // Handle shortcut URLs.
                when (uri.scheme) {
                    null -> return "https://github.com/$path.git"
                    "gist" -> return "https://gist.github.com/$path"
                    "bitbucket" -> return "https://bitbucket.org/$path.git"
                    "gitlab" -> return "https://gitlab.com/$path.git"
                }
            }
        }
    }

    if (uri.host.endsWith("github.com")) {
        // Ensure the path ends in ".git".
        val path = if (uri.path.endsWith(".git")) uri.path else uri.path + ".git"

        // Remove any user name and "www" prefix.
        val host = uri.authority.substringAfter("@").removePrefix("www.")

        return "https://" + host + path
    }

    // Return the URL unmodified.
    return vcsUrl
}
