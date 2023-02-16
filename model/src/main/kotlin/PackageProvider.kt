/*
 * Copyright (C) 2023 The ORT Project Authors (see <https://github.com/oss-review-toolkit/ort/blob/main/NOTICE>)
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     https://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 *
 * SPDX-License-Identifier: Apache-2.0
 * License-Filename: LICENSE
 */

package org.ossreviewtoolkit.model

/**
 * The provider / hosting platform of a remote artifact or Version Control System. These are basically human-readable
 * aliases for different URLs that may point to the same provider. Note that different providers might host different
 * artifacts under the same [Identifier]. So if in question, the provider must be given in addition to the [Identifier].
 */
enum class PackageProvider(
    /**
     * The regular expression patterns that match all URLs for a specific provider.
     */
    vararg urlPatterns: String,
) {
    COCOAPODS(
        "^https?://cocoapods\\.org/pods/"
    ),
    CRATES_IO(
        "^https?://crates\\.io/api/"
    ),
    DEBIAN(
        "^https?://(\\w+\\.)+debian.org/debian/"
    ),
    GITHUB(
        "^(https?|ssh)://(git@)?(\\w+\\.)?github\\.com/"
    ),
    GITLAB(
        "^(https?|ssh)://(git@)?(\\w+\\.)?gitlab\\.com/"
    ),
    GOLANG(
        "^https?://go\\.googlesource\\.com/",
        "^https?://proxy\\.golang\\.org/"
    ),
    GRADLE_PLUGIN(
        "^https?://plugins\\.gradle\\.org/m2/"
    ),
    MAVEN_CENTRAL(
        "^https?://repo\\.maven\\.apache\\.org/maven2/",
        "^https?://repo1\\.maven\\.org/maven2/"
    ),
    MAVEN_GOOGLE(
        "^https?://maven\\.google\\.com/"
    ),
    NPM_JS(
        "^https?://registry\\.npmjs\\.(com|org)/"
    ),
    NUGET(
        "^https?://api\\.nuget\\.org/.+\\.nupkg"
    ),
    PACKAGIST(
        "^https?://repo\\.packagist\\.org/"
    ),
    PYPI(
        "^https?://files\\.pythonhosted\\.org/packages/"
    ),
    RUBYGEMS(
        "^https?://rubygems\\.org/gems/"
    ),

    // The following have no matching ClearlyDefined provider.
    ANDROID(
        "^https?://android\\.googlesource\\.com/"
    ),
    APACHE_GIT(
        "^https?://gitbox\\.apache\\.org/repos/.+\\.git",
        "^https?://git-wip-us\\.apache\\.org/repos/.+\\.git"
    ),
    APACHE_SUBVERSION(
        "^https?://svn\\.apache\\.org/repos/"
    ),
    BITBUCKET(
        "^https?://bitbucket\\.org/"
    ),
    GOOGLE_CODE(
        "^https?://\\w+\\.googlecode\\.com/"
    ),
    HACKAGE(
        "^https?://hackage\\.haskell\\.org/package/"
    ),
    HASKELL(
        "^https?://git\\.haskell\\.org/"
    ),
    JCENTER(
        "^https?://jcenter\\.bintray\\.com/"
    ),
    JENKINS(
        "^https?://repo\\.jenkins-ci\\.org/"
    ),
    JITPACK(
        "^https?://jitpack\\.io/"
    );

    private val urlRegexes by lazy { urlPatterns.map { it.toRegex() } }

    companion object {
        /**
         * Return the [PackageProvider] as determined from the given [url], or null if there is no match.
         */
        fun get(url: String): PackageProvider? =
            enumValues<PackageProvider>().find { provider ->
                provider.urlRegexes.any { it.containsMatchIn(url) }
            }

        /**
         * Return the [PackageProvider] as determined from the given [artifact]'s URL, or null if there is no match.
         */
        fun get(artifact: RemoteArtifact): PackageProvider? = get(artifact.url)

        /**
         * Return the [PackageProvider] as determined from the given [vcs] URL, or null if there is no match.
         */
        fun get(vcs: VcsInfo): PackageProvider? = get(vcs.url)
    }
}
