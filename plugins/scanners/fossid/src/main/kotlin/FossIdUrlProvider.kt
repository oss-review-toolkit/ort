/*
 * Copyright (C) 2022 The ORT Project Authors (see <https://github.com/oss-review-toolkit/ort/blob/main/NOTICE>)
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

package org.ossreviewtoolkit.plugins.scanners.fossid

import java.net.Authenticator
import java.net.PasswordAuthentication

import org.apache.logging.log4j.kotlin.logger

import org.ossreviewtoolkit.utils.common.percentEncode
import org.ossreviewtoolkit.utils.common.replaceCredentialsInUri
import org.ossreviewtoolkit.utils.common.toUri
import org.ossreviewtoolkit.utils.ort.requestPasswordAuthentication

/**
 * An internal helper class that generates the URLs used by [FossId] to check out the repositories to be scanned.
 *
 * The URLs used by [FossId] can sometimes be different from the normal package URLs. For instance, credentials may need
 * to be added, or a different protocol may be used. This class takes care of such mappings.
 */
class FossIdUrlProvider private constructor(
    /**
     * The URL mapping. URLs matched by a key [Regex] are replaced by the URL in the value. The replacement string can
     * refer to matched groups of the [Regex]. It can also contain the variables [VAR_USERNAME] and [VAR_PASSWORD] to
     * allow the injection of credentials.
     */
    private val urlMapping: Map<Regex, String>
) {
    companion object {
        /** The prefix of option keys that define a URL mapping. */
        internal const val PREFIX_URL_MAPPING = "urlMapping"

        /** Variable in the mapping replacement string that references the username. */
        private const val VAR_USERNAME = "#username"

        /** Variable in the mapping replacement string that references the password. */
        private const val VAR_PASSWORD = "#password"

        /** The separator regex to split URL mappings. */
        private val MAPPING_SEPARATOR = Regex("\\s+->\\s+")

        /** A credentials object to be used if the Authenticator does not yield results. */
        private val UNKNOWN_CREDENTIALS = PasswordAuthentication("unknown", CharArray(0))

        /**
         * Create a new instance of [FossIdUrlProvider] that applies the given [urlMapping]. The strings in
         * [urlMapping] must contain a regular expression to match URLs and a replacement string, separated by
         * [MAPPING_SEPARATOR].
         */
        fun create(urlMapping: Collection<String> = emptyList()): FossIdUrlProvider {
            val mappings = urlMapping.toRegexMapping()
            mappings.forEach {
                logger.info { "Mapping ${it.key} -> ${it.value}." }
            }

            return FossIdUrlProvider(mappings)
        }

        /**
         * Try to fetch credentials for [repoUrl] from the current [Authenticator]. Return *null* if no matching host
         * is found.
         */
        private fun queryAuthenticator(repoUrl: String): PasswordAuthentication? {
            val repoUri = repoUrl.toUri().getOrElse {
                logger.warn { "The repository URL $repoUrl is not valid." }
                return null
            }

            logger.info { "Requesting authentication for host ${repoUri.host} ..." }

            return requestPasswordAuthentication(repoUri)
        }

        /**
         * Handle the variables referencing credentials if they occur in the [replacement string][replaced].
         */
        private fun replaceVariables(replaced: String): String =
            replaced.takeUnless { VAR_USERNAME in it || VAR_PASSWORD in it }
                ?: insertCredentials(replaced)

        /**
         * Query the [Authenticator] and populate the corresponding variables in [replaced]. If no credentials are
         * known for this host, strip the credentials part from [replaced].
         */
        private fun insertCredentials(replaced: String): String {
            // In order to have a valid URL from which credentials can be stripped, the variables must be replaced by
            // some credentials first.
            val replacedUrl = replaced.insertCredentials(UNKNOWN_CREDENTIALS).replaceCredentialsInUri()

            return queryAuthenticator(replacedUrl)?.let { replaced.insertCredentials(it) }
                ?: replacedUrl
        }

        /**
         * Replace the variables related to credentials with the values in [auth].
         */
        private fun String.insertCredentials(auth: PasswordAuthentication): String =
            replace(VAR_USERNAME, auth.userName.percentEncode())
                .replace(VAR_PASSWORD, String(auth.password).percentEncode())

        /**
         * Construct a URL mapping from the elements in this [Collection]. The resulting [Map] contains regular
         * expressions as keys to match for URLs and the corresponding replacement strings as values.
         */
        private fun Collection<String>.toRegexMapping(): Map<Regex, String> {
            val (mappings, invalid) = map { it to it.split(MAPPING_SEPARATOR) }.partition { it.second.size == 2 }

            if (invalid.isNotEmpty()) {
                logger.warn { "Found the following invalid URL mappings: ${invalid.map { it.first }}." }
            }

            return mappings.associate { pair ->
                val regex = pair.second[0].toRegex()
                val replace = pair.second[1]
                regex to replace
            }
        }
    }

    /**
     * Return the URL to be passed to FossID when creating a scan from the given [repoUrl].
     */
    fun getUrl(repoUrl: String): String {
        for ((regex, replace) in urlMapping) {
            val replaced = regex.replace(repoUrl, replace)
            if (replaced != repoUrl) {
                logger.info { "URL mapping applied to $repoUrl: Mapped to $replaced." }
                return replaceVariables(replaced)
            } else {
                logger.debug { "Mapping $regex did not match." }
            }
        }

        logger.info { "No matching URL mapping could be found for $repoUrl." }

        return repoUrl
    }
}
