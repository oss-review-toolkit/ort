package com.here.ort.analyzer.managers

import com.here.ort.analyzer.PackageManager
import com.here.ort.model.ScanResult

import java.io.File

object Bower : PackageManager(
        "https://bower.io/",
        "JavaScript",
        listOf("bower.json")
) {
    override fun command(workingDir: File): String {
        return "bower"
    }

    override fun resolveDependencies(projectDir: File, definitionFiles: List<File>): Map<File, ScanResult> {
        TODO("not implemented") //To change body of created functions use File | Settings | File Templates.
    }
}
