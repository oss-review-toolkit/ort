package com.here.provenanceanalyzer.model

import com.fasterxml.jackson.databind.ObjectMapper
import com.fasterxml.jackson.dataformat.yaml.YAMLFactory
import com.fasterxml.jackson.module.kotlin.registerKotlinModule

val jsonMapper = ObjectMapper().registerKotlinModule()
val yamlMapper = ObjectMapper(YAMLFactory()).registerKotlinModule()
