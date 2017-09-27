package com.here.provenanceanalyzer.model

import com.fasterxml.jackson.annotation.JsonProperty

data class Project(
        @JsonProperty("package_manager")
        val packageManager: String,
        val namespace: String,
        val name: String,
        val aliases: List<String>,
        val version: String,
        @JsonProperty("vcs_path")
        val vcsPath: String?,
        @JsonProperty("vcs_provider")
        val vcsProvider: String,
        @JsonProperty("vcs_url")
        val vcsUrl: String,
        val revision: String, // tag, branch, sha1, ...
        @JsonProperty("homepage_url")
        val homepageUrl: String,
        val scopes: List<Scope>
) {

    /**
     * Return a [Package] representation of this [Project].
     */
    fun asPackage() = Package(packageManager, namespace, name, "", version, homepageUrl, "", "", "", vcsPath,
            vcsProvider, vcsUrl, revision)

}
