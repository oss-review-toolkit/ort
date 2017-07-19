package com.here.provenanceanalyzer.managers

import com.here.provenanceanalyzer.PackageManager
import com.here.provenanceanalyzer.model.ScanResult

import java.io.File

object Bower : PackageManager(
        "https://bower.io/",
        "JavaScript",
        listOf("bower.json")
) {
    override fun command(workingDir: File): String {
        return "bower"
    }

    override fun resolveDependencies(definitionFiles: List<File>): Map<File, ScanResult> {
        TODO("not implemented") //To change body of created functions use File | Settings | File Templates.
    }
}
