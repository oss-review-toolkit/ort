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

package org.ossreviewtoolkit.utils.common

import java.net.URI

/**
 * True if the string is a valid [URI], false otherwise.
 */
fun String.isValidUri() = runCatching { URI(this) }.isSuccess

/**
 * Return the [percent-encoded](https://en.wikipedia.org/wiki/Percent-encoding) string.
 */
fun String.percentEncode(): String =
    java.net.URLEncoder.encode(this, Charsets.UTF_8)
        // As "encode" above actually performs encoding for forms, not for query strings, spaces are encoded as
        // "+" instead of "%20", so apply the proper mapping here afterwards ("+" in the original string is
        // encoded as "%2B").
        .replace("+", "%20")
        // "*" is a reserved character in RFC 3986.
        .replace("*", "%2A")
        // "~" is an unreserved character in RFC 3986.
        .replace("%7E", "~")

/**
 * Replace any username / password in the URI represented by this [String] with [userInfo]. If [userInfo] is null, the
 * username / password are stripped. Return the unmodified [String] if it does not represent a URI.
 */
fun String.replaceCredentialsInUri(userInfo: String? = null) =
    toUri {
        URI(it.scheme, userInfo, it.host, it.port, it.path, it.query, it.fragment).toString()
    }.getOrDefault(this)

/**
 * Return a [Result] that indicates whether the conversion of this [String] to a [URI] was successful.
 */
fun String.toUri() = runCatching { URI(this) }

/**
 * Return a [Result] that indicates whether the conversion of this [String] to a [URI] was successful, and [transform]
 * the [URI] if so.
 */
fun <R> String.toUri(transform: (URI) -> R) = toUri().mapCatching(transform)

/**
 * Retrieve query parameters of this [URI]. Multiple values of a single key are supported if they are split by a comma,
 * or if keys are repeated as defined in RFC6570 section 3.2.9, see https://datatracker.ietf.org/doc/rfc6570.
 */
fun URI.getQueryParameters(): Map<String, List<String>> {
    if (query == null) return emptyMap()

    return query.split('&')
        .groupBy({ it.substringBefore('=') }, { it.substringAfter('=').split(',') })
        .mapValues { (_, v) -> v.flatten() }
}
