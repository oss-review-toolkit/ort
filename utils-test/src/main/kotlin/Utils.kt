/*
 * Copyright (C) 2017-2018 HERE Europe B.V.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
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

package com.here.ort.utils.test

import java.io.File

val USER_DIR = File(System.getProperty("user.dir"))

fun patchExpectedResult(result: File, custom: Pair<String, String>? = null, definitionFilePath: String? = null,
                        url: String? = null, revision: String? = null, path: String? = null,
                        urlProcessed: String? = null): String {
    fun String.replaceIfNotNull(strings: Pair<String, String>?) =
            if (strings != null) replace(strings.first, strings.second) else this
    fun String.replaceIfNotNull(oldValue: String, newValue: String?) =
            if (newValue != null) replace(oldValue, newValue) else this

    return result.readText()
            .replaceIfNotNull("<REPLACE_OS>", System.getProperty("os.name"))
            .replaceIfNotNull(custom)
            .replaceIfNotNull("<REPLACE_DEFINITION_FILE_PATH>", definitionFilePath)
            .replaceIfNotNull("<REPLACE_URL>", url)
            .replaceIfNotNull("<REPLACE_REVISION>", revision)
            .replaceIfNotNull("<REPLACE_PATH>", path)
            .replaceIfNotNull("<REPLACE_URL_PROCESSED>", urlProcessed)
}
