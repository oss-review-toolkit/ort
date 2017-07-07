package com.here.provenanceanalyzer.managers

import com.here.provenanceanalyzer.model.Dependency
import com.here.provenanceanalyzer.PackageManager

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

    override fun resolveDependencies(definitionFiles: List<File>): Map<File, Dependency> {
        TODO("not implemented") //To change body of created functions use File | Settings | File Templates.
    }
}
