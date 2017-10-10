package com.here.ort.analyzer.managers

import com.here.ort.analyzer.PackageManager
import com.here.provenanceanalyzer.model.ScanResult

import java.io.File

object SBT : PackageManager(
        "http://www.scala-sbt.org/",
        "Scala",
        listOf("build.sbt", "build.scala")
) {
    override fun command(workingDir: File): String {
        return "sbt"
    }

    override fun resolveDependencies(projectDir: File, definitionFiles: List<File>): Map<File, ScanResult> {
        TODO("not implemented") //To change body of created functions use File | Settings | File Templates.
    }
}
