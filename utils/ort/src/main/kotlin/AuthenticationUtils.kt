/*
 * Copyright (C) 2017 The ORT Project Authors (see <https://github.com/oss-review-toolkit/ort/blob/main/NOTICE>)
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

package org.ossreviewtoolkit.utils.ort

import java.net.Authenticator
import java.net.PasswordAuthentication
import java.net.URI
import java.net.URL

/**
 * Request a [PasswordAuthentication] object for the given [host], [port], [scheme], and optional [url]. Install the
 * [OrtAuthenticator] and the [OrtProxySelector] beforehand to ensure they are active.
 */
fun requestPasswordAuthentication(host: String, port: Int, scheme: String, url: URL? = null): PasswordAuthentication? {
    OrtAuthenticator.install()
    OrtProxySelector.install()

    return Authenticator.requestPasswordAuthentication(
        /* host = */ host,
        /* addr = */ null,
        /* port = */ port,
        /* protocol = */ scheme,
        /* prompt = */ null,
        /* scheme = */ null,
        /* url = */ url,
        /* reqType = */ Authenticator.RequestorType.SERVER
    )
}

/**
 * Request a [PasswordAuthentication] object for the given [uri]. Install the [OrtAuthenticator] and the
 * [OrtProxySelector] beforehand to ensure they are active.
 */
fun requestPasswordAuthentication(uri: URI): PasswordAuthentication? =
    requestPasswordAuthentication(uri.host, uri.port, uri.scheme, uri.toURL())
