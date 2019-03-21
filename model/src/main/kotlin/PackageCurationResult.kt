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

package com.here.ort.model

import com.fasterxml.jackson.annotation.JsonInclude

/**
 * This class contains information about which values were changed when applying a curation.
 */
data class PackageCurationResult(
    /**
     * Contains the values from before applying the [curation]. Values which were not changed are null.
     */
    val base: PackageCurationData,

    /**
     * The curation that was applied.
     */
    val curation: PackageCurationData,

    /**
     * A map that holds arbitrary data. Can be used by third-party tools to add custom data to the model.
     */
    @JsonInclude(JsonInclude.Include.NON_EMPTY)
    val data: CustomData = emptyMap()
)
