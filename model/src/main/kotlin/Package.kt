package com.here.provenanceanalyzer.model

import com.fasterxml.jackson.annotation.JsonIgnoreProperties
import com.fasterxml.jackson.annotation.JsonProperty

import com.vdurmont.semver4j.Semver

@JsonIgnoreProperties("identifier", "normalizedVcsUrl", "semverType")
data class Package(
        @JsonProperty("package_manager")
        val packageManager: String, // e.g. "Maven", "NPM", ...
        val namespace: String, // e.g. group in Maven ("org.junit"), scope in NPM ("@types")
        val name: String, // e.g. "junit"
        val description: String,
        val version: String, // concrete version, e.g. 1.2.3
        @JsonProperty("homepage_url")
        val homepageUrl: String?,
        @JsonProperty("download_url")
        val downloadUrl: String?,
        val hash: String, // e.g. sha1, sha512, ... of download
        @JsonProperty("vcs_provider")
        val vcsProvider: String?,
        @JsonProperty("vcs_url")
        val vcsUrl: String?,
        @JsonProperty("vcs_revision")
        val vcsRevision: String?
) {
    val identifier = "$packageManager:$namespace:$name:$version"

    val semverType = when (packageManager) {
        "NPM" -> Semver.SemverType.NPM
        else -> Semver.SemverType.STRICT
    }

    val normalizedVcsUrl = if (vcsUrl == null) null else normalizeVcsUrl(vcsUrl, semverType)
}
