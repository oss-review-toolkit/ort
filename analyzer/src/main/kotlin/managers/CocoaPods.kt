package com.here.ort.analyzer.managers

import com.here.ort.analyzer.PackageManager
import com.here.ort.model.AnalyzerResult

import java.io.File

object CocoaPods : PackageManager(
        "https://cocoapods.org/",
        "Objective-C",
        listOf("Podfile.lock", "Podfile")
) {
    override fun command(workingDir: File): String {
        return "pod"
    }

    override fun resolveDependencies(projectDir: File, definitionFiles: List<File>): Map<File, AnalyzerResult> {
        TODO("not implemented") //To change body of created functions use File | Settings | File Templates.
    }
}
