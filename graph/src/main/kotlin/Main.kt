/*
 * Copyright (c) 2017-2018 HERE Europe B.V.
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

package com.here.ort.graph

import com.beust.jcommander.JCommander
import com.beust.jcommander.Parameter

import com.here.ort.model.OutputFormat
import com.here.ort.model.PackageReference
import com.here.ort.model.AnalyzerResult
import com.here.ort.utils.PARAMETER_ORDER_HELP
import com.here.ort.utils.PARAMETER_ORDER_LOGGING
import com.here.ort.utils.PARAMETER_ORDER_MANDATORY
import com.here.ort.utils.jsonMapper
import com.here.ort.utils.log
import com.here.ort.utils.yamlMapper

import org.graphstream.graph.Edge
import org.graphstream.graph.implementations.SingleGraph
import org.graphstream.graph.implementations.SingleNode
import org.graphstream.ui.layout.springbox.implementations.SpringBox

import java.io.File
import java.util.UUID

import kotlin.system.exitProcess

/**
 * The main entry point of the application.
 */
object Main {
    const val TOOL_NAME = "graph"

    @Parameter(description = "The dependencies analysis file to use.",
            names = ["--dependencies-file", "-d"],
            required = true,
            order = PARAMETER_ORDER_MANDATORY)
    @Suppress("LateinitUsage")
    private lateinit var dependenciesFile: File

    @Parameter(description = "Enable info logging.",
            names = ["--info"],
            order = PARAMETER_ORDER_LOGGING)
    private var info = false

    @Parameter(description = "Enable debug logging and keep any temporary files.",
            names = ["--debug"],
            order = PARAMETER_ORDER_LOGGING)
    private var debug = false

    @Parameter(description = "Print out the stacktrace for all exceptions.",
            names = ["--stacktrace"],
            order = PARAMETER_ORDER_LOGGING)
    var stacktrace = false

    @Parameter(description = "Display the command line help.",
            names = ["--help", "-h"],
            help = true,
            order = PARAMETER_ORDER_HELP)
    private var help = false

    /**
     * The entry point for the application.
     *
     * @param args The list of application arguments.
     */
    @JvmStatic
    fun main(args: Array<String>) {
        val jc = JCommander(this)
        jc.parse(*args)
        jc.programName = TOOL_NAME

        if (info) {
            log.level = ch.qos.logback.classic.Level.INFO
        }

        if (debug) {
            log.level = ch.qos.logback.classic.Level.DEBUG
        }

        if (help) {
            jc.usage()
            exitProcess(1)
        }

        require(dependenciesFile.isFile) {
            "Provided path is not a file: ${dependenciesFile.absolutePath}"
        }

        val mapper = when (dependenciesFile.extension) {
            OutputFormat.JSON.fileEnding -> jsonMapper
            OutputFormat.YAML.fileEnding -> yamlMapper
            else -> throw IllegalArgumentException("Provided input file is neither JSON or YAML.")
        }

        showGraph(mapper.readValue(dependenciesFile, AnalyzerResult::class.java))
    }

    private fun showGraph(analyzerResult: AnalyzerResult) {
        System.setProperty("org.graphstream.ui.renderer", "org.graphstream.ui.j2dviewer.J2DGraphRenderer")

        val project = analyzerResult.project
        val graph = SingleGraph(project.id.name).apply {
            isStrict = true
            setAutoCreate(true)
            addAttribute("ui.antialias")
            addAttribute("ui.quality")
        }

        val rootNode = graph.addNode<SingleNode>(project.id.name)
        rootNode.addAttribute("ui.label", project.id.name)

        project.scopes.forEach { (name, _, dependencies) ->
            println("Adding scope $name.")
            val scopeNode = graph.addNode<SingleNode>(name)
            scopeNode.addAttribute("ui.label", name)
            graph.addEdge<Edge>(name, rootNode, scopeNode)

            dependencies.forEach { dependency ->
                addDependency(scopeNode, dependency)
            }
        }

        val viewer = graph.display(false)
        val layout = SpringBox()
        viewer.enableAutoLayout(layout)
    }

    private fun addDependency(parent: SingleNode, dependency: PackageReference) {
        val identifier = "${dependency.namespace}:${dependency.name}:${dependency.version}"
        val dependencyNode = parent.graph.addNode<SingleNode>(UUID.randomUUID().toString())
        dependencyNode.addAttribute("ui.label", identifier)
        parent.graph.addEdge<Edge>(UUID.randomUUID().toString(), parent, dependencyNode)

        dependency.dependencies.forEach {
            addDependency(dependencyNode, it)
        }
    }
}
