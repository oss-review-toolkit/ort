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

import com.fasterxml.jackson.databind.JsonNode
import com.fasterxml.jackson.databind.ObjectMapper
import com.fasterxml.jackson.databind.PropertyNamingStrategy
import com.fasterxml.jackson.databind.SerializationFeature
import com.fasterxml.jackson.databind.module.SimpleModule
import com.fasterxml.jackson.dataformat.xml.XmlFactory
import com.fasterxml.jackson.dataformat.yaml.YAMLFactory
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule
import com.fasterxml.jackson.module.kotlin.registerKotlinModule

import com.here.ort.model.config.AnalyzerConfiguration
import com.here.ort.model.config.AnalyzerConfigurationDeserializer

private val ortModelModule = SimpleModule("OrtModelModule").apply {
    addDeserializer(AnalyzerConfiguration::class.java, AnalyzerConfigurationDeserializer())
    addDeserializer(LicenseFinding::class.java, LicenseFindingDeserializer())
    addDeserializer(OrtIssue::class.java, OrtIssueDeserializer())
    addDeserializer(VcsInfo::class.java, VcsInfoDeserializer())

    addSerializer(OrtIssue::class.java, OrtIssueSerializer())
    addSerializer(Identifier::class.java, IdentifierToStringSerializer())
}

/**
 * A lambda expression that can be [applied][apply] to all [ObjectMapper]s to configure them the same way.
 */
private val mapperConfig: ObjectMapper.() -> Unit = {
    registerKotlinModule()

    registerModule(JavaTimeModule())
    disable(SerializationFeature.WRITE_DATES_AS_TIMESTAMPS)

    registerModule(ortModelModule)

    propertyNamingStrategy = PropertyNamingStrategy.SNAKE_CASE
}

val jsonMapper = ObjectMapper().apply(mapperConfig)
val xmlMapper = ObjectMapper(XmlFactory()).apply(mapperConfig)
val yamlMapper = ObjectMapper(YAMLFactory()).apply(mapperConfig)

val EMPTY_JSON_NODE: JsonNode = jsonMapper.readTree("{}")
