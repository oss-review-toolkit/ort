package com.here.ort.graph

import com.beust.jcommander.JCommander
import com.beust.jcommander.Parameter

import com.here.ort.model.OutputFormat
import com.here.ort.model.PackageReference
import com.here.ort.model.AnalyzerResult
import com.here.ort.util.jsonMapper
import com.here.ort.util.log
import com.here.ort.util.yamlMapper

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

    @Parameter(description = "The dependencies analysis file to use.",
            names = arrayOf("--dependencies-file", "-d"),
            required = true,
            order = 0)
    @Suppress("LateinitUsage")
    private lateinit var dependenciesFile: File

    @Parameter(description = "Enable info logging.",
            names = arrayOf("--info"),
            order = 0)
    private var info = false

    @Parameter(description = "Enable debug logging and keep any temporary files.",
            names = arrayOf("--debug"),
            order = 0)
    private var debug = false

    @Parameter(description = "Print out the stacktrace for all exceptions.",
            names = arrayOf("--stacktrace"),
            order = 0)
    var stacktrace = false

    @Parameter(description = "Display the command line help.",
            names = arrayOf("--help", "-h"),
            help = true,
            order = 100)
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
        jc.programName = "graph"

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
        val graph = SingleGraph(project.name)

        graph.setStrict(true)
        graph.setAutoCreate(true)
        graph.addAttribute("ui.antialias")
        graph.addAttribute("ui.quality")

        val rootNode = graph.addNode<SingleNode>(project.name)
        rootNode.addAttribute("ui.label", project.name)

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
