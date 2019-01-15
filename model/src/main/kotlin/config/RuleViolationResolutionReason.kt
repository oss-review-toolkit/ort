package com.here.ort.model.config

/*
 * Copyright (C) 2017-2019 HERE Europe B.V.
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

enum class RuleViolationResolutionReason {
    /**
     * The rule violation is acceptable given the fact that the dependency it relates to is dynamically linked.
     */
    DYNAMIC_LINKAGE_EXCEPTION,

    /**
     * The implied patent grant is acceptable in this case.
     */
    PATENT_GRANT_EXCEPTION
}
