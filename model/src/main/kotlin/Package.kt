package com.here.provenanceanalyzer.model

import com.fasterxml.jackson.annotation.JsonIgnoreProperties
import com.fasterxml.jackson.annotation.JsonProperty

import com.vdurmont.semver4j.Semver

/**
 * A descriptor for a software package. It contains information about naming, version, and how to retrieve the package
 * and its source code.
 */
@JsonIgnoreProperties("identifier", "normalizedVcsUrl", "semverType")
data class Package(
        /**
         * Name of the package manager that was used to discover this dependency, for example Maven or NPM.
         */
        @JsonProperty("package_manager")
        val packageManager: String,

        /**
         * The namespace of the package, for example the group id in Maven or the scope in NPM.
         */
        val namespace: String,

        /**
         * The name of the package.
         */
        val name: String,

        /**
         * The description of the package, as provided by the package manager.
         */
        val description: String,

        /**
         * The version of the package.
         */
        val version: String,

        /**
         * The homepage of the package.
         */
        @JsonProperty("homepage_url")
        val homepageUrl: String?,

        /**
         * The optional URL to the binary artifact of the package.
         */
        @JsonProperty("download_url")
        val downloadUrl: String?,

        /**
         * The optional hash value of the binary artifact of the package.
         */
        val hash: String,

        /**
         * The optional name of the VCS provider, for example Git or SVN.
         */
        @JsonProperty("vcs_provider")
        val vcsProvider: String?,

        /**
         * The optional URL to the VCS repository.
         */
        @JsonProperty("vcs_url")
        val vcsUrl: String?,

        /**
         * The optional revision of the VCS that this [version] of the package was built from.
         */
        @JsonProperty("vcs_revision")
        val vcsRevision: String?
) {
    /**
     * The unique identifier for this package, created from [packageManager], [namespace], [name], and [version].
     */
    val identifier = "$packageManager:$namespace:$name:$version"

    /**
     * The [Semver.SemverType] used for the [version] of this package.
     */
    val semverType = when (packageManager) {
        "NPM" -> Semver.SemverType.NPM
        else -> Semver.SemverType.STRICT
    }

    /**
     * The normalized VCS URL.
     *
     * @see normalizeVcsUrl
     */
    val normalizedVcsUrl = if (vcsUrl == null) null else normalizeVcsUrl(vcsUrl, semverType)
}
