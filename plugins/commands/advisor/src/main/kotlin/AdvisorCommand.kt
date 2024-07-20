/*
 * Copyright (C) 2020 The ORT Project Authors (see <https://github.com/oss-review-toolkit/ort/blob/main/NOTICE>)
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

package org.ossreviewtoolkit.plugins.commands.advisor

import com.github.ajalt.clikt.core.BadParameterValue
import com.github.ajalt.clikt.core.terminal
import com.github.ajalt.clikt.parameters.options.associate
import com.github.ajalt.clikt.parameters.options.convert
import com.github.ajalt.clikt.parameters.options.default
import com.github.ajalt.clikt.parameters.options.deprecated
import com.github.ajalt.clikt.parameters.options.flag
import com.github.ajalt.clikt.parameters.options.option
import com.github.ajalt.clikt.parameters.options.required
import com.github.ajalt.clikt.parameters.options.split
import com.github.ajalt.clikt.parameters.types.enum
import com.github.ajalt.clikt.parameters.types.file
import com.github.ajalt.mordant.animation.coroutines.animateInCoroutine
import com.github.ajalt.mordant.animation.progress.update
import com.github.ajalt.mordant.table.verticalLayout
import com.github.ajalt.mordant.terminal.PrintRequest
import com.github.ajalt.mordant.terminal.Terminal
import com.github.ajalt.mordant.terminal.TerminalInfo
import com.github.ajalt.mordant.terminal.TerminalInterface
import com.github.ajalt.mordant.widgets.progress.completed
import com.github.ajalt.mordant.widgets.progress.percentage
import com.github.ajalt.mordant.widgets.progress.progressBar
import com.github.ajalt.mordant.widgets.progress.progressBarLayout
import com.github.ajalt.mordant.widgets.progress.text

import io.klogging.Level.INFO
import io.klogging.config.STDOUT_SIMPLE
import io.klogging.config.loggingConfiguration
import io.klogging.context.Context
import kotlinx.coroutines.cancelChildren
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.transformWhile
import kotlinx.coroutines.job
import kotlinx.coroutines.launch

import java.time.Duration

import kotlin.time.toKotlinDuration

import kotlinx.coroutines.runBlocking

import org.ossreviewtoolkit.advisor.AdviceProviderFactory
import org.ossreviewtoolkit.advisor.Advisor2
import org.ossreviewtoolkit.advisor.OrtContext
import org.ossreviewtoolkit.advisor.PluginContext
import org.ossreviewtoolkit.advisor.logger
import org.ossreviewtoolkit.model.FileFormat
import org.ossreviewtoolkit.model.utils.DefaultResolutionProvider
import org.ossreviewtoolkit.plugins.commands.api.OrtCommand
import org.ossreviewtoolkit.plugins.commands.api.utils.SeverityStatsPrinter
import org.ossreviewtoolkit.plugins.commands.api.utils.configurationGroup
import org.ossreviewtoolkit.plugins.commands.api.utils.outputGroup
import org.ossreviewtoolkit.plugins.commands.api.utils.readOrtResult
import org.ossreviewtoolkit.plugins.commands.api.utils.writeOrtResult
import org.ossreviewtoolkit.utils.common.expandTilde
import org.ossreviewtoolkit.utils.common.safeMkdirs
import org.ossreviewtoolkit.utils.ort.Environment
import org.ossreviewtoolkit.utils.ort.ORT_FAILURE_STATUS_CODE
import org.ossreviewtoolkit.utils.ort.ORT_RESOLUTIONS_FILENAME
import org.ossreviewtoolkit.utils.ort.ortConfigDirectory
import java.io.ByteArrayOutputStream
import java.io.OutputStream
import java.io.PrintStream

class AdvisorCommand : OrtCommand(
    name = "advise",
    help = "Check dependencies for security vulnerabilities."
) {
    private val ortFile by option(
        "--ort-file", "-i",
        help = "An ORT result file with an analyzer result to use."
    ).convert { it.expandTilde() }
        .file(mustExist = true, canBeFile = true, canBeDir = false, mustBeWritable = false, mustBeReadable = true)
        .convert { it.absoluteFile.normalize() }
        .required()

    private val outputDir by option(
        "--output-dir", "-o",
        help = "The directory to write the ORT result file with advisor results to."
    ).convert { it.expandTilde() }
        .file(mustExist = false, canBeFile = false, canBeDir = true, mustBeWritable = false, mustBeReadable = false)
        .convert { it.absoluteFile.normalize() }
        .required()
        .outputGroup()

    private val outputFormats by option(
        "--output-formats", "-f",
        help = "The list of output formats to be used for the ORT result file(s)."
    ).enum<FileFormat>().split(",").default(listOf(FileFormat.YAML)).outputGroup()

    private val labels by option(
        "--label", "-l",
        help = "Set a label in the ORT result, overwriting any existing label of the same name. Can be used multiple " +
            "times. For example: --label distribution=external"
    ).associate()

    private val resolutionsFile by option(
        "--resolutions-file",
        help = "A file containing issue and rule violation resolutions."
    ).convert { it.expandTilde() }
        .file(mustExist = true, canBeFile = true, canBeDir = false, mustBeWritable = false, mustBeReadable = true)
        .convert { it.absoluteFile.normalize() }
        .default(ortConfigDirectory.resolve(ORT_RESOLUTIONS_FILENAME))
        .configurationGroup()

    private val providerFactories by option(
        "--advisors", "-a",
        help = "The comma-separated advisors to use, any of ${AdviceProviderFactory.ALL.keys}."
    ).convert { name ->
        AdviceProviderFactory.ALL[name]
            ?: throw BadParameterValue("Advisor '$name' is not one of ${AdviceProviderFactory.ALL.keys}.")
    }.split(",").required()

    private val skipExcluded by option(
        "--skip-excluded",
        help = "Do not check excluded projects or packages."
    ).flag().deprecated("Use the global option 'ort -P ort.advisor.skipExcluded=... advise' instead.")

    override fun run() {
        loggingConfiguration {
            sink("console", STDOUT_SIMPLE)
            logging {
                fromMinLevel(INFO) { toSink("console") }
            }
        }

        Context.addContextItemExtractor(OrtContext) { ortContext ->
            mapOf("ortVersion" to ortContext.environment.ortVersion)
        }

        Context.addContextItemExtractor(PluginContext) { pluginContext ->
            mapOf("plugin" to pluginContext.name)
        }

        val outputFiles = outputFormats.mapTo(mutableSetOf()) { format ->
            outputDir.resolve("advisor-result.${format.fileExtension}")
        }

        validateOutputFiles(outputFiles)

        val distinctProviders = providerFactories.distinct()
        echo("The following advisors are activated:")
        echo("\t" + distinctProviders.joinToString().ifEmpty { "<None>" })

        val ortResultInput = readOrtResult(ortFile)

        val packages = ortResultInput.getPackages(omitExcluded = skipExcluded || ortConfig.advisor.skipExcluded)
            .mapTo(mutableSetOf()) { it.metadata }

        val adviceProviders = providerFactories.map {
            val providerConfig = ortConfig.advisor.config?.get(it.type)
            it.create(providerConfig?.options.orEmpty(), providerConfig?.secrets.orEmpty())
        }

        val advisor = Advisor2(adviceProviders, packages, ortConfig.advisor)

        // Redirect the terminal to stdout/stderr.
        val redirectInterface = RedirectingTerminalInterface(System.out, System.err, Terminal().info)
        val term = Terminal(terminalInterface = redirectInterface)

        // Redirect System.out to the terminal.
        System.setOut(PrintStreamWrapper(term, System.out))

        val progressByProvider = adviceProviders.map { it.providerName }.associateWith {
            progressBarLayout {
                text("${it.padEnd(15).take(15)}:")
                completed()
                text(" packages")
                progressBar()
                percentage()
                text(" done  ")
            }
        }

        runBlocking(OrtContext(ortConfig, Environment())) {
            val animatorByProvider = progressByProvider.mapValues { (_, progress) ->
                progress.animateInCoroutine(term).also {
                    it.update { total = packages.size.toLong() }
                    launch { it.execute() }
                }
            }

            launch {
                advisor.getUpdates().collect { update ->
                    logger.info("Received update: ${update.provider} - ${update.pkg.toCoordinates()} - ${update.result.vulnerabilities.size} vulnerabilities - ${update.result.summary.issues.size} issues")
                }
            }

            launch {
                advisor.getState().transformWhile {
                    emit(it)
                    !it.finished
                }.collect { state ->
                    animatorByProvider.forEach { (providerName, animator) ->
                        animator.update(state.providerProgress[providerName]?.completedPackages ?: 0)
                    }
                }
            }

            advisor.execute()

            delay(100) // Delay a bit to ensure the progress bars are fully updated.
            coroutineContext.job.cancelChildren()
        }

        val advisorRun = advisor.getRun()

        val ortResultOutput = ortResultInput.copy(advisor = advisorRun)

        outputDir.safeMkdirs()
        writeOrtResult(ortResultOutput, outputFiles, terminal)

        val duration = with(advisorRun) { Duration.between(startTime, endTime).toKotlinDuration() }
        echo("The advice took $duration.")

        with(advisorRun.getVulnerabilities()) {
            val includedPackages = ortResultOutput.getPackages(omitExcluded = true).map { it.metadata.id }
            val totalPackageCount = includedPackages.size
            val vulnerablePackageCount = count { (id, vulnerabilities) ->
                id in includedPackages && vulnerabilities.isNotEmpty()
            }

            val vulnerabilityCount = filterKeys { it in includedPackages }.values.sumOf { it.size }

            echo(
                "$vulnerablePackageCount of $totalPackageCount package(s) (not counting excluded ones) are " +
                    "vulnerable, with $vulnerabilityCount vulnerabilities in total."
            )
        }

        val resolutionProvider = DefaultResolutionProvider.create(ortResultOutput, resolutionsFile)
        val issues = advisorRun.getIssues().flatMap { it.value }
        SeverityStatsPrinter(terminal, resolutionProvider).stats(issues)
            .print().conclude(ortConfig.severeIssueThreshold, ORT_FAILURE_STATUS_CODE)
    }
}

class RedirectingTerminalInterface(
    private val out: PrintStream,
    private val err: PrintStream,
    override val info: TerminalInfo
) : TerminalInterface {
    override fun completePrintRequest(request: PrintRequest) {
        when {
            request.stderr -> {
                if (request.trailingLinebreak) {
                    err.println(request.text)
                } else {
                    err.print(request.text)
                }
            }

            request.trailingLinebreak -> {
                if (request.text.isEmpty()) {
                    out.println()
                } else {
                    out.println(request.text)
                }
            }

            else -> out.print(request.text)
        }
    }

    override fun readLineOrNull(hideInput: Boolean): String? = readlnOrNull()
}

class PrintStreamWrapper(val terminal: Terminal, out: PrintStream) : PrintStream(ByteArrayOutputStream()) {
    private val contentBuilder = StringBuilder()

    override fun write(buf: ByteArray, off: Int, len: Int) {
        terminal.print(String(buf, off, len))
//        contentBuilder.append(String(buf, off, len))
    }

    override fun write(b: Int) {
        terminal.print(b.toChar().toString())
//        contentBuilder.append(b.toChar())
    }

//    override fun flush() {
//        val content = contentBuilder.toString()
//        contentBuilder.clear()
//        terminal.print(content)
//    }
}
