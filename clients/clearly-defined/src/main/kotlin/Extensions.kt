/*
 * Copyright (C) 2020 Bosch.IO GmbH
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

package org.ossreviewtoolkit.clients.clearlydefined

import java.util.Locale

import okhttp3.RequestBody

import okio.Buffer

/**
 * Return the request body as a string, see https://github.com/square/okhttp/issues/1891.
 */
fun RequestBody.string() = Buffer().also { writeTo(it) }.readUtf8()

/**
 * Return this string lower-cased except for the first character which is upper-cased.
 */
fun String.titlecase() =
    lowercase().replaceFirstChar { if (it.isLowerCase()) it.titlecase(Locale.ROOT) else it.toString() }
