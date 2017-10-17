package com.here.ort.analyzer.managers

import com.here.ort.analyzer.PackageManager
import com.here.ort.model.AnalyzerResult

import java.io.File

object Godep : PackageManager(
        "https://godoc.org/github.com/tools/godep",
        "Go",
        listOf("Godeps/Godeps.json")
) {
    override fun command(workingDir: File): String {
        return "godep"
    }

    override fun resolveDependencies(projectDir: File, definitionFiles: List<File>): Map<File, AnalyzerResult> {
        TODO("not implemented") //To change body of created functions use File | Settings | File Templates.
    }
}
