/*
 * Copyright (C) 2026 The ORT Project Copyright Holders <https://github.com/oss-review-toolkit/ort/blob/main/NOTICE>
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
package org.ossreviewtoolkit.model.utils

import org.ossreviewtoolkit.model.config.Excludes
import org.ossreviewtoolkit.model.config.Includes

/**
 * This function checks if a [path] is included or excluded based on [excludes] and [includes] configuration.
 * If there are no includes, all files are included. If includes are defined, everything not included is excluded.
 * If the same path is affected by an include and an exclude, the latter takes precedence and the path is excluded.
 */
fun isPathIncluded(path: String, excludes: Excludes, includes: Includes): Boolean {
    val isIncluded = includes.isPathIncluded(path)
    val isExcluded = excludes.isPathExcluded(path)

    return isIncluded && !isExcluded
}
