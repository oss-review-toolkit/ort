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

package com.here.ort.analyzer

import com.here.ort.model.Identifier
import com.here.ort.model.PackageCuration
import com.here.ort.model.readValue

import java.io.File

/**
 * A [PackageCurationProvider] that loads [PackageCuration]s from a single YAML file.
 */
class YamlFilePackageCurationProvider(
    curationFile: File
) : PackageCurationProvider {
    internal val packageCurations: List<PackageCuration> by lazy {
        curationFile.readValue<List<PackageCuration>>()
    }

    override fun getCurationsFor(pkgId: Identifier) = packageCurations.filter { it.isApplicable(pkgId) }
}
