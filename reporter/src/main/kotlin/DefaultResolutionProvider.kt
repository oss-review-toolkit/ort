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

package com.here.ort.reporter

import com.here.ort.model.Error
import com.here.ort.model.config.RepositoryConfiguration
import com.here.ort.model.config.Resolutions
import com.here.ort.model.readValue

import java.io.File

/**
 * This [ResolutionProvider] provides error resolutions from a JSON or YAML encoded resolutions file, and from the
 * [Resolutions] object as contained in the [RepositoryConfiguration].
 */
class DefaultResolutionProvider(
    /**
     * A [Resolutions] object, typically taken from a [RepositoryConfiguration].
     */
    private val repositoryResolutions: Resolutions,

    /**
     * A JSON or YAML encoded file containing resolutions.
     */
    private val resolutionsFile: File? = null
) : ResolutionProvider {
    private val fileResolutions: Resolutions  by lazy { resolutionsFile?.readValue() ?: Resolutions() }

    private val resolutions: Resolutions by lazy { fileResolutions.merge(repositoryResolutions) }

    override fun getResolutionsFor(error: Error) = resolutions.errors.filter { it.matches(error) }
}
