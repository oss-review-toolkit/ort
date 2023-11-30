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
 * An enum representing the different kinds of configuration the ORT tools consume.
 */
enum class OrtConfigType(val resolvable: Boolean) {
    COPYRIGHT_GARBAGE(resolvable = false),
    CUSTOM_LICENSE_TEXTS(resolvable = false),
    EVALUATOR_RULES(resolvable = false),
    HOW_TO_FIX_TEXTS(resolvable = false),
    LICENSE_CHOICES(resolvable = false),
    LICENSE_CLASSIFICATIONS(resolvable = false),
    NOTIFIER_RULES(resolvable = false),
    ORT_CONFIG(resolvable = false),
    PACKAGE_CONFIGURATIONS(resolvable = true),
    PACKAGE_CURATIONS(resolvable = true),
    RESOLUTIONS(resolvable = true);

    fun resolvableTypes() = values().filter { it.resolvable }
}
