package com.here.ort.model

import com.vdurmont.semver4j.Semver

import java.net.URI

/**
 * Normalize a VCS URL by converting it to a common pattern. For example NPM defines some shortcuts for GitHub or GitLab
 * URLs which are converted to full URLs so that they can be used in a common way.
 *
 * @param vcsUrl The URL to normalize.
 * @param semverType Required to convert package manager specific shortcuts.
 */
fun normalizeVcsUrl(vcsUrl: String, semverType: Semver.SemverType): String {
    var url = vcsUrl.trimEnd('/')

    if (url.startsWith("git@github.com:")) {
        url = url.replace("git@github.com:", "https://github.com/")
    }

    // A hierarchical URI looks like
    //     [scheme:][//authority][path][?query][#fragment]
    // where a server-based "authority" has the syntax
    //     [user-info@]host[:port]
    val uri = URI(url)

    if (semverType == Semver.SemverType.NPM) {
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

    if (uri.host != null && uri.host.endsWith("github.com")) {
        // Ensure the path ends in ".git".
        val path = if (uri.path.endsWith(".git")) uri.path else uri.path + ".git"

        // Remove any user name and "www" prefix.
        val host = uri.authority.substringAfter("@").removePrefix("www.")

        return "https://" + host + path
    }

    // Return the URL unmodified.
    return url
}
