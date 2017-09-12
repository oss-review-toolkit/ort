package com.here.provenanceanalyzer.managers

import com.here.provenanceanalyzer.PackageManager
import com.here.provenanceanalyzer.model.ScanResult

import java.io.File

object Maven : PackageManager(
        "https://maven.apache.org/",
        "Java",
        listOf("pom.xml")
) {
    override fun command(workingDir: File): String {
        return "mvn"
    }

    override fun resolveDependencies(projectDir: File, definitionFiles: List<File>): Map<File, ScanResult> {
        TODO("not implemented") //To change body of created functions use File | Settings | File Templates.
    }
}
