/*
 * Copyright (C) 2022 EPAM Systems, Inc.
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

package org.ossreviewtoolkit.analyzer.managers.utils

/**
 * Return the given [moduleVersion] normalized to a Semver compliant version. The `v` prefix gets stripped and
 * also any suffix starting with '+', because build metadata is not involved in version comparison according to
 * https://go.dev/ref/mod#incompatible-versions.
 */
fun normalizeModuleVersion(moduleVersion: String): String =
    moduleVersion.removePrefix("v").substringBefore("+")
