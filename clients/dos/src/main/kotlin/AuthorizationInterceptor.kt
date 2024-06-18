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

package org.ossreviewtoolkit.clients.dos

import okhttp3.Interceptor

import retrofit2.Invocation

/**
 * A custom annotation for skipping the authorization interceptor for certain API calls.
 */
@Target(AnnotationTarget.FUNCTION)
@Retention(AnnotationRetention.RUNTIME)
internal annotation class SkipAuthorization

/**
 * An HTTP interceptor to conditionally skip authorization.
 */
internal class AuthorizationInterceptor(private val token: String) : Interceptor {
    override fun intercept(chain: Interceptor.Chain): okhttp3.Response {
        val original = chain.request()

        val skipAuthorization = original.tag(Invocation::class.java)
            ?.method()
            ?.isAnnotationPresent(SkipAuthorization::class.java) == true

        if (skipAuthorization) return chain.proceed(original)

        val requestBuilder = original.newBuilder()
            .header("Authorization", "Bearer $token")
            .method(original.method, original.body)

        return chain.proceed(requestBuilder.build())
    }
}
