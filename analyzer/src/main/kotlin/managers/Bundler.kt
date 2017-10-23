package com.here.ort.analyzer.managers

import com.here.ort.analyzer.PackageManager
import com.here.ort.model.AnalyzerResult

import java.io.File

object Bundler : PackageManager(
        "http://bundler.io/",
        "Ruby",
        // See http://yehudakatz.com/2010/12/16/clarifying-the-roles-of-the-gemspec-and-gemfile/.
        listOf("Gemfile.lock", "Gemfile")
) {
    override fun command(workingDir: File): String {
        return "bundle"
    }
}
