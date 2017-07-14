package com.here.provenanceanalyzer.model

import com.vdurmont.semver4j.Semver
import com.vdurmont.semver4j.Semver.SemverType

import java.net.URI

data class Dependency(
        val group: String? = null,
        val artifact: String,
        val version: Semver,
        val scope: String,
        val dependencies: List<Dependency> = listOf(),
        private val scm: String? = null
) {
    val normalizedScm = scm?.let { normalizeRepositoryUrl(it) }

    private fun normalizeRepositoryUrl(url: String): String {
        // A hierarchical URI looks like
        //     [scheme:][//authority][path][?query][#fragment]
        // where a server-based "authority" has the syntax
        //     [user-info@]host[:port]
        val uri = URI(url)

        if (version.type == SemverType.NPM) {
            // https://docs.npmjs.com/files/package.json#repository
            val path = uri.schemeSpecificPart
            if (path != null) {
                if (uri.authority == null && uri.query == null && uri.fragment == null) {
                    // Handle shortcut URLs.
                    when (uri.scheme) {
                        null -> return "https://github.com/$path.git"
                        "gist" -> return "https://gist.github.com/$path"
                        "bitbucket" -> return "https://bitbucket.org/$path.git"
                        "gitlab" -> return "https://gitlab.com/$path.git"
                    }
                }
            }
        }

        if (uri.host.endsWith("github.com")) {
            // Ensure the path ends in ".git".
            val path = if (uri.path.endsWith(".git")) uri.path else uri.path + ".git"

            // Remove any user name and "www" prefix.
            val host = uri.authority.substringAfter("@").removePrefix("www.")

            return "https://" + host + path
        }

        // Return the URL unmodified.
        return url
    }

    override fun toString(): String {
        return toString("")
    }

    private fun toString(indent: String): String {
        return buildString {
            append("$indent$group:$artifact:${version.value}:$scope:$normalizedScm")
            append(System.lineSeparator())
            dependencies.forEach { dependency ->
                append(dependency.toString("$indent  "))
            }
        }
    }
}
