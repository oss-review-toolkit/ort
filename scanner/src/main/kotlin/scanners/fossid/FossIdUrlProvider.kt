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

package org.ossreviewtoolkit.scanner.scanners.fossid

import java.net.Authenticator

import org.apache.logging.log4j.kotlin.Logging

import org.ossreviewtoolkit.utils.common.replaceCredentialsInUri
import org.ossreviewtoolkit.utils.common.toUri
import org.ossreviewtoolkit.utils.ort.requestPasswordAuthentication

/**
 * An internal helper class that generates the URLs used by [FossId] to check out the repositories to be scanned.
 *
 * The URLs used by FossId can sometimes be different from the normal package URLs. For instance, credentials may need
 * to be added. This class takes care of such mappings.
 */
internal class FossIdUrlProvider private constructor(
    /** Flag whether credentials should be passed to FossID in URLs. */
    val addAuthenticationToUrl: Boolean
) {
    companion object : Logging {
        fun create(addAuthenticationToUrl: Boolean): FossIdUrlProvider = FossIdUrlProvider(addAuthenticationToUrl)

        /**
         * This function fetches credentials for [repoUrl] and insert them between the URL scheme and the host. If no
         * matching host is found by [Authenticator], the [repoUrl] is returned untouched.
         */
        private fun queryAuthenticator(repoUrl: String): String {
            val repoUri = repoUrl.toUri().getOrElse {
                logger.warn { "The repository URL '$repoUrl' is not valid." }
                return repoUrl
            }

            logger.info { "Requesting authentication for host ${repoUri.host} ..." }

            val creds = requestPasswordAuthentication(repoUri)
            return creds?.let {
                repoUrl.replaceCredentialsInUri("${creds.userName}:${String(creds.password)}")
            } ?: repoUrl
        }
    }

    /**
     * Return the URL to be passed to FossID when creating a scan from the given [repoUrl].
     */
    fun getUrl(repoUrl: String): String {
        if (!addAuthenticationToUrl) return repoUrl

        return queryAuthenticator(repoUrl)
    }
}
