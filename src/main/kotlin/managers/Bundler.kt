package com.here.provenanceanalyzer.managers

import com.here.provenanceanalyzer.model.Dependency
import com.here.provenanceanalyzer.PackageManager

import java.nio.file.Path

object Bundler : PackageManager(
        "http://bundler.io/",
        "Ruby",
        // See http://yehudakatz.com/2010/12/16/clarifying-the-roles-of-the-gemspec-and-gemfile/.
        listOf("Gemfile.lock", "Gemfile")
) {
    override fun resolveDependencies(definitionFiles: List<Path>): Map<Path, Dependency> {
        TODO("not implemented") //To change body of created functions use File | Settings | File Templates.
    }
}
