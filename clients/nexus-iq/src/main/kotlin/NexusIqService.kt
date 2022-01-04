/*
 * Copyright (C) 2020 Bosch.IO GmbH
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

package org.ossreviewtoolkit.clients.nexusiq

import com.jakewharton.retrofit2.converter.kotlinx.serialization.asConverterFactory

import java.net.URI
import java.util.UUID

import kotlinx.serialization.KSerializer
import kotlinx.serialization.Serializable
import kotlinx.serialization.Serializer
import kotlinx.serialization.encoding.Decoder
import kotlinx.serialization.encoding.Encoder
import kotlinx.serialization.json.Json

import okhttp3.Credentials
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient

import retrofit2.Retrofit
import retrofit2.http.Body
import retrofit2.http.GET
import retrofit2.http.POST
import retrofit2.http.Path

@Serializer(URI::class)
object URISerializer : KSerializer<URI> {
    override fun serialize(encoder: Encoder, value: URI) = encoder.encodeString(value.toString())
    override fun deserialize(decoder: Decoder) = URI(decoder.decodeString())
}

/**
 * Interface for the NexusIQ REST API, based on the documentation from
 * https://help.sonatype.com/iqserver/automating/rest-apis.
 */
interface NexusIqService {
    companion object {
        /** Identifier of the scoring system _Common Vulnerability Scoring System_ version 2. */
        const val CVSS2_SCORE = "CVSS2"

        /** Identifier of the scoring system _Common Vulnerability Scoring System_ version 3. */
        const val CVSS3_SCORE = "CVSS3"

        /**
         * The JSON (de-)serialization object used by this service.
         */
        val JSON = Json { ignoreUnknownKeys = true }

        /**
         * Create a NexusIQ service instance for communicating with a server running at the given [url], optionally
         * using [user] and [password] for basic authentication, and / or a pre-built OkHttp [client].
         */
        fun create(
            url: String,
            user: String? = null,
            password: String? = null,
            client: OkHttpClient? = null
        ): NexusIqService {
            val nexusIqClient = (client ?: OkHttpClient()).newBuilder()
                .addInterceptor { chain ->
                    val request = chain.request()
                    val token = UUID.randomUUID().toString()
                    val requestBuilder = request.newBuilder()
                        .header("X-CSRF-TOKEN", token)
                        .header("Cookie", "CLM-CSRF-TOKEN=$token")

                    if (user != null && password != null) {
                        requestBuilder.header("Authorization", Credentials.basic(user, password))
                    }

                    chain.proceed(requestBuilder.build())
                }
                .build()

            val contentType = "application/json".toMediaType()
            val retrofit = Retrofit.Builder()
                .client(nexusIqClient)
                .baseUrl(url)
                .addConverterFactory(JSON.asConverterFactory(contentType))
                .build()

            return retrofit.create(NexusIqService::class.java)
        }
    }

    @Serializable
    data class ComponentDetailsWrapper(
        val componentDetails: List<ComponentDetails>
    )

    @Serializable
    data class ComponentDetails(
        val component: Component,
        val securityData: SecurityData
    )

    @Serializable
    data class SecurityData(
        val securityIssues: List<SecurityIssue>
    )

    @Serializable
    data class SecurityIssue(
        val source: String,
        val reference: String,
        val severity: Float,
        @Serializable(URISerializer::class) val url: URI?,
        val threatCategory: String
    ) {
        // See https://guides.sonatype.com/iqserver/technical-guides/sonatype-vuln-data/#how-is-a-vulnerability-score-severity-calculated.
        private val cvss3Sources = listOf("cve", "sonatype")

        /**
         * Return an identifier for the scoring system used for this issue.
         */
        fun scoringSystem(): String = if (source in cvss3Sources) CVSS3_SCORE else CVSS2_SCORE
    }

    @Serializable
    data class ComponentsWrapper(
        val components: List<Component>
    )

    @Serializable
    data class Component(
        val packageUrl: String
    )

    @Serializable
    data class Organizations(
        val organizations: List<Organization>
    )

    @Serializable
    data class Organization(
        val id: String,
        val name: String,
        val tags: List<Tag>
    )

    @Serializable
    data class Tag(
        val id: String,
        val name: String,
        val description: String,
        val color: String
    )

    @Serializable
    data class MemberMappings(
        val memberMappings: List<MemberMapping>
    )

    @Serializable
    data class MemberMapping(
        val roleId: String,
        val members: List<Member>
    )

    @Serializable
    data class Member(
        val ownerId: String,
        val ownerType: String,
        val type: String,
        val userOrGroupName: String
    )

    /**
     * Retrieve the details for multiple [components].
     * See https://help.sonatype.com/iqserver/automating/rest-apis/component-details-rest-api---v2.
     */
    @POST("api/v2/components/details")
    suspend fun getComponentDetails(@Body components: ComponentsWrapper): ComponentDetailsWrapper

    /**
     * Produce a list of all organizations and associated information that are viewable for the current user.
     * See https://help.sonatype.com/iqserver/automating/rest-apis/organization-rest-apis---v2#OrganizationRESTAPIs-v2-GetOrganizations.
     */
    @GET("api/v2/organizations")
    suspend fun getOrganizations(): Organizations

    /**
     * Get the users and groups by role for an organization.
     * See https://help.sonatype.com/iqserver/automating/rest-apis/authorization-configuration-%28aka-role-membership%29-rest-api---v2#AuthorizationConfiguration(akaRoleMembership)RESTAPI-v2-Gettheusersandgroupsbyrole.
     */
    @GET("api/v2/roleMemberships/organization/{organizationId}")
    suspend fun getRoleMembershipsForOrganization(@Path("organizationId") organizationId: String): MemberMappings
}
