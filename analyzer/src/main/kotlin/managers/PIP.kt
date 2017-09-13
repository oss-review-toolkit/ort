package com.here.provenanceanalyzer.managers

import com.here.provenanceanalyzer.PackageManager
import com.here.provenanceanalyzer.model.ScanResult

import java.io.File

object PIP : PackageManager(
        "https://pip.pypa.io/",
        "Python",
        // See https://caremad.io/posts/2013/07/setup-vs-requirement/.
        listOf("requirements*.txt", "setup.py")
) {
    override fun command(workingDir: File): String {
        return "pip"
    }

    override fun resolveDependencies(projectDir: File, definitionFiles: List<File>): Map<File, ScanResult> {
        TODO("not implemented") //To change body of created functions use File | Settings | File Templates.
    }
}
