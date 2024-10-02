/*
 * Copyright (C) 2024 The ORT Project Authors (see <https://github.com/oss-review-toolkit/ort/blob/main/NOTICE>)
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

package org.ossreviewtoolkit.clients.bazelmoduleregistry

import retrofit2.http.GET
import retrofit2.http.Path

/**
 * A Retrofit client for remote bazel module registries.
 */
interface RetrofitRegistryServiceClient {
    /**
     * Retrieve the metadata for a module.
     * E.g. https://bcr.bazel.build/modules/glog/metadata.json.
     */
    @GET("modules/{name}/metadata.json")
    suspend fun getModuleMetadata(@Path("name") name: String): ModuleMetadata

    /**
     * Retrieve information about the source code for a specific version of a module.
     * E.g. https://bcr.bazel.build/modules/glog/0.5.0/source.json.
     */
    @GET("modules/{name}/{version}/source.json")
    suspend fun getModuleSourceInfo(@Path("name") name: String, @Path("version") version: String): ModuleSourceInfo
}
