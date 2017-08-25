package com.here.provenanceanalyzer.graph

import com.beust.jcommander.JCommander
import com.beust.jcommander.Parameter
import com.here.provenanceanalyzer.model.Dependency

import com.here.provenanceanalyzer.model.OutputFormat
import com.here.provenanceanalyzer.model.ScanResult
import com.here.provenanceanalyzer.model.jsonMapper
import com.here.provenanceanalyzer.model.yamlMapper
import org.graphstream.graph.Edge
import org.graphstream.graph.implementations.SingleGraph
import org.graphstream.graph.implementations.SingleNode
import org.graphstream.ui.layout.springbox.implementations.SpringBox

import java.io.File
import java.util.*

import kotlin.system.exitProcess

/**
 * The main entry point of the application.
 */
object Main {

    @Parameter(description = "provenance data file path")
    private var provenanceFilePath: String? = null

    @Parameter(names = arrayOf("--help", "-h"),
            description = "Display the command line help.",
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

        if (help || provenanceFilePath == null) {
            jc.usage()
            exitProcess(1)
        }

        var provenanceFile = File(provenanceFilePath)
        require(provenanceFile.isFile) {
            "Provided path is not a file: ${provenanceFile.absolutePath}"
        }

        val mapper = when {
            provenanceFile.name.endsWith(OutputFormat.JSON.fileEnding) -> jsonMapper
            provenanceFile.name.endsWith(OutputFormat.YAML.fileEnding) -> yamlMapper
            else -> throw IllegalArgumentException("Provided input file is neither JSON or YAML.")
        }

        val scanResult = mapper.readValue(provenanceFile, ScanResult::class.java)

        showGraph(scanResult)
    }

    private fun showGraph(scanResult: ScanResult) {
        System.setProperty("org.graphstream.ui.renderer", "org.graphstream.ui.j2dviewer.J2DGraphRenderer")

        val project = scanResult.project

        val graph = SingleGraph(project.name)

        graph.setStrict(true)
        graph.setAutoCreate(true)
        graph.addAttribute("ui.antialias")
        graph.addAttribute("ui.quality")

        val rootNode = graph.addNode<SingleNode>(project.name)
        rootNode.addAttribute("ui.label", project.name)

        project.scopes.forEach { scope ->
            println("Adding scope ${scope.name}.")
            val scopeNode = graph.addNode<SingleNode>(scope.name)
            scopeNode.addAttribute("ui.label", scope.name)
            graph.addEdge<Edge>(scope.name, rootNode, scopeNode)

            scope.dependencies.forEach { dependency ->
                addDependency(scopeNode, dependency)
            }
        }

        val viewer = graph.display(false)
        val layout = SpringBox()
        viewer.enableAutoLayout(layout)

    }

    private fun addDependency(parent: SingleNode, dependency: Dependency) {
        val identifier = "${dependency.namespace}:${dependency.name}:${dependency.version}"
        val dependencyNode = parent.graph.addNode<SingleNode>(UUID.randomUUID().toString())
        dependencyNode.addAttribute("ui.label", identifier)
        parent.graph.addEdge<Edge>(UUID.randomUUID().toString(), parent, dependencyNode)

        dependency.dependencies.forEach {
            addDependency(dependencyNode, it)
        }
    }

}
