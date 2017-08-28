package com.here.provenanceanalyzer.managers

import com.here.provenanceanalyzer.PackageManager
import com.here.provenanceanalyzer.model.ScanResult

import java.io.File

object Godep : PackageManager(
        "https://godoc.org/github.com/tools/godep",
        "Go",
        listOf("Godeps/Godeps.json")
) {
    override fun command(workingDir: File): String {
        return "godep"
    }

    override fun resolveDependencies(definitionFiles: List<File>): Map<File, ScanResult> {
        TODO("not implemented") //To change body of created functions use File | Settings | File Templates.
    }
}
