/*
 * Copyright (C) 2026 The ORT Project Copyright Holders <https://github.com/oss-review-toolkit/ort/blob/main/NOTICE>
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

package org.ossreviewtoolkit.plugins.packagemanagers.node

import java.io.File

import kotlinx.serialization.json.Json

import org.apache.logging.log4j.kotlin.logger

import org.ossreviewtoolkit.utils.common.CommandLineTool
import org.ossreviewtoolkit.utils.common.Os
import org.ossreviewtoolkit.utils.common.ProcessCapture
import org.ossreviewtoolkit.utils.common.splitOnWhitespace

/**
 * A [CommandLineTool] implementing for the FNM node version manager to be used by [NodeVersionManagerCommand].
 */
internal object FnmCommand : CommandLineTool {
    override fun command(workingDir: File?): String = "fnm"
}

/**
 * An abstract [CommandLineTool] base class that enables subclasses to be run under a specific version of Node.js.
 *
 * Commands derived from this base class can run on a specific version of Node.js that is bootstrapped dynamically if
 * necessary. For this purpose, the class makes use of the node version manager implementation `fnm`. The version
 * specification can have the following form:
 * - An empty version string means that the default Node.js version installed locally is used.
 * - The version string "*" means that the version of Node.js to be used should be discovered based on the current
 *   working directory. This uses the functionality of FNV to look at files like `.node-version` that can define a
 *   concrete version.
 * - Any other value is interpreted as a version number, and FNM is triggered to install this concrete version.
 *
 * Subclasses provide the bare executable name via [baseCommand] and optionally enrich the process environment
 * via [enrichEnvironment]. To produce a version-managed instance, call [useVersion] with an existing
 * instance as a prototype; the prototype's [withNodePath] factory is then used to create the new instance.
 * When [nodePath] is `null`, [command] returns the value of [baseCommand] and relies on the system PATH for
 * executable resolution. When [nodePath] is set, [command] returns an absolute path constructed from [nodePath]
 * and [baseCommand].
 *
 * See https://github.com/Schniz/fnm
 */
abstract class NodeVersionManagerCommand(
    /**
     * The path to the directory containing the Node.js binaries. A value of *null* means that the command is invoked
     * without a path prefix and relies on the system PATH for executable resolution.
     */
    private val nodePath: String? = null,

    /**
     * The resolved Node.js version to be used. A value of *null* means that the default Node.js version installed
     * locally is used.
     */
    val nodeVersion: String? = null
) : CommandLineTool {
    companion object {
        /**
         * Constant for a version string that causes an auto-discovery of the Node.js version to be used based on files
         * like `.node-version` contained in the project.
         */
        const val NODE_VERSION_AUTO_DISCOVER = "*"

        /**
         * An allowlist regex for Node.js version strings accepted by FNM. The rules are:
         * - Must start with an alphanumeric character (prevents leading `-` which would be interpreted as a CLI
         *   option flag by fnm's argument parser, e.g. `--help` or `-n`).
         * - Dots and slashes are allowed anywhere after the first character (e.g. `18.0.0`, `lts/iron`).
         * - A hyphen is only allowed when immediately followed by an alphanumeric character, which prevents `--`
         *   (double-hyphen option prefix), a trailing hyphen, and consecutive hyphens (e.g. `lts-iron` is allowed,
         *   `--flag` or `foo--bar` are not).
         *
         * Examples of accepted strings: `18.0.0`, `v18.0.0`, `18`, `latest`, `lts/iron`, `lts-iron`.
         */
        private val VALID_NODE_VERSION_REGEX = Regex("""^[a-zA-Z0-9][a-zA-Z0-9./]*(?:-[a-zA-Z0-9][a-zA-Z0-9./]*)*$""")

        /**
         * Return a [NodeVersionManagerCommand] that is guaranteed to run under the given [nodeVersion] if specified.
         * Use [projectDir] to dynamically discover the version if required. If no [nodeVersion] is requested, the
         * original command is returned unchanged.
         */
        fun useVersion(
            cmd: NodeVersionManagerCommand,
            nodeVersion: String,
            projectDir: File
        ): NodeVersionManagerCommand = if (nodeVersion.isBlank()) cmd else createVersioned(cmd, nodeVersion, projectDir)

        /**
         * Return a new instance of [cmd]'s concrete type that runs under the Node.js version determined by
         * [nodeVersion], installing it via FNM if necessary. The [projectDir] is used for auto-discovery.
         */
        private fun createVersioned(
            cmd: NodeVersionManagerCommand,
            nodeVersion: String,
            projectDir: File
        ): NodeVersionManagerCommand {
            logger.info { "Providing Node.js in requested version $nodeVersion." }

            val resolvedNodeVersion = installNodeAndGetVersion(nodeVersion, projectDir)
            val nodePath = resolvePath(resolvedNodeVersion)
            return cmd.withNodePath(nodePath, resolvedNodeVersion)
        }

        /**
         * Install the given [nodeVersion] of Node.js and return the resolved version string. If the version is to be
         * auto-discovered, look for files that define it in the given [projectDir]. Return the resolved Node.js
         * version.
         * Note that the different calls to fnm have to be executed in a single shell, since they require a properly
         * set up environment created by the initial `fnm env` call.
         */
        private fun installNodeAndGetVersion(nodeVersion: String, projectDir: File): String {
            require(nodeVersion == NODE_VERSION_AUTO_DISCOVER || VALID_NODE_VERSION_REGEX.matches(nodeVersion)) {
                "Invalid Node.js version string: '$nodeVersion'. Only alphanumeric characters, dots, hyphens, and " +
                    "slashes are allowed."
            }

            // Wrap the version in single quotes to prevent the shell from interpreting any characters it may contain.
            // Single-quote syntax is identical in both POSIX sh and PowerShell. Because `VALID_NODE_VERSION_REGEX`
            // rejects single quotes, no further escaping of embedded quotes is needed.
            val quotedVersion = nodeVersion.takeUnless { it == NODE_VERSION_AUTO_DISCOVER }?.let { "'$it'" }

            val fnmUseCommand = listOfNotNull(
                "fnm", "use",
                quotedVersion,
                "--install-if-missing",
                "--version-file-strategy=recursive"
            ).joinToString(" ")

            val process = if (Os.isWindows) {
                // Make sure that failures in command executions are detected and cause the whole command to fail.
                val checkExitCode = $$"if ($LASTEXITCODE -ne 0) { exit $LASTEXITCODE }"
                ProcessCapture(
                    "powershell.exe", "-Command",
                    "fnm env --shell powershell | Invoke-Expression; $checkExitCode; " +
                        "$fnmUseCommand; $checkExitCode; fnm current",
                    workingDir = projectDir
                )
            } else {
                ProcessCapture(
                    "sh", "-c",
                    """eval "$(fnm env)" && $fnmUseCommand && fnm current""",
                    workingDir = projectDir
                )
            }

            process.requireSuccess()
            return process.stdout.lines().lastOrNull { it.isNotBlank() }.orEmpty().trim()
        }

        /**
         * Resolve the path of the binaries for the given [nodeVersion]. This is used to construct an absolute path
         * to the command to be executed.
         */
        private fun resolvePath(nodeVersion: String): String {
            val fnmEnv: Map<String, String> =
                Json.decodeFromString(FnmCommand.run("env", "--json").requireSuccess().stdout)
            logger.debug { "Environment variables from FNM: $fnmEnv" }

            val fnmDir = fnmEnv["FNM_DIR"] ?: error("FNM_DIR not found in FNM environment variables.")
            val nodeDir = if (Os.isWindows) {
                "$fnmDir/node-versions/$nodeVersion/installation"
            } else {
                "$fnmDir/node-versions/$nodeVersion/installation/bin"
            }

            logger.info { "Using $nodeDir as path for Node.js version $nodeVersion." }

            return nodeDir
        }
    }

    /**
     * Return a flag whether the current Node.js version supports the `--use-system-ca` option. The current version is
     * determined by [nodeVersion] if set; otherwise, the default Node.js version installed locally is used.
     */
    val hasUseSystemCaOption by lazy {
        nodeVersion?.let { NodeCommand.hasUseSystemCaOption(it) } ?: NodeCommand.hasUseSystemCaOption
    }

    /**
     * Return the bare executable name (without any path prefix), e.g. `npm` or `npm.cmd`.
     * This is combined with [nodePath] by [command] to form the final executable path.
     */
    abstract fun baseCommand(workingDir: File? = null): String

    /**
     * Return a new instance of this command's concrete type that uses the given [nodePath] as the directory
     * containing the Node.js binaries of the given [nodeVersion].
     */
    abstract fun withNodePath(nodePath: String, nodeVersion: String): NodeVersionManagerCommand

    /**
     * Enrich the [environment] map before it is passed to the child process. The default implementation returns
     * the map unchanged. Subclasses can override this to add tool-specific variables (e.g. `NODE_OPTIONS`).
     * The `PATH` variable is handled separately by [run] and must not be set here.
     */
    protected open fun enrichEnvironment(environment: Map<String, String>): Map<String, String> = environment

    /**
     * Return the executable to run. If [nodePath] is set, an absolute path is returned so that Java's
     * [ProcessBuilder] – which resolves executables against the *parent* process's PATH, not the child's –
     * picks up the correct Node.js version. Otherwise, the bare [baseCommand] is returned.
     */
    override fun command(workingDir: File?): String {
        val base = baseCommand(workingDir)
        return if (nodePath != null) File(nodePath, base).absolutePath else base
    }

    override fun run(vararg args: CharSequence, workingDir: File?, environment: Map<String, String>) =
        ProcessCapture(
            *command(workingDir).splitOnWhitespace().toTypedArray(),
            *args,
            workingDir = workingDir,
            environment = prependNodePath(enrichEnvironment(environment))
        )

    /**
     * Prepend [nodePath] to the `PATH` entry of [environment] to make sure that the child process can resolve the
     * correct binaries under all circumstances. When [nodePath] is `null` the map is returned unchanged.
     */
    private fun prependNodePath(environment: Map<String, String>): Map<String, String> {
        if (nodePath == null) return environment

        val existingPath = environment["PATH"] ?: System.getenv("PATH").orEmpty()
        return environment.toMutableMap().apply {
            put("PATH", "$nodePath${File.pathSeparator}$existingPath")
        }
    }
}
