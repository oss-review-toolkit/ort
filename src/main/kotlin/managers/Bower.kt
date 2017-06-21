package com.here.provenanceanalyzer.managers

import com.here.provenanceanalyzer.model.Dependency
import com.here.provenanceanalyzer.PackageManager

import java.nio.file.Path

object Bower : PackageManager(
        "https://bower.io/",
        "JavaScript",
        listOf("bower.json")
) {
    override fun resolveDependencies(definitionFiles: List<Path>): Map<Path, Dependency> {
        TODO("not implemented") //To change body of created functions use File | Settings | File Templates.
    }
}
