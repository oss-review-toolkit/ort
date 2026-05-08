/*
 * Copyright (C) 2021 The ORT Project Copyright Holders <https://github.com/oss-review-toolkit/ort/blob/main/NOTICE>
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

import com.networknt.schema.InputFormat
import com.networknt.schema.SchemaRegistry
import com.networknt.schema.dialect.Dialects

import io.kotest.core.spec.style.StringSpec
import io.kotest.inspectors.forAll
import io.kotest.matchers.collections.beEmpty
import io.kotest.matchers.should
import io.kotest.matchers.shouldNot

import java.io.File

import org.ossreviewtoolkit.model.config.RepositoryConfiguration
import org.ossreviewtoolkit.utils.ort.ORT_LICENSE_CLASSIFICATIONS_FILENAME
import org.ossreviewtoolkit.utils.ort.ORT_PACKAGE_CONFIGURATION_FILENAME
import org.ossreviewtoolkit.utils.ort.ORT_PACKAGE_CURATIONS_FILENAME
import org.ossreviewtoolkit.utils.ort.ORT_REFERENCE_CONFIG_FILENAME
import org.ossreviewtoolkit.utils.ort.ORT_REPO_CONFIG_FILENAME
import org.ossreviewtoolkit.utils.ort.ORT_RESOLUTIONS_FILENAME
import org.ossreviewtoolkit.utils.test.readResource
import org.ossreviewtoolkit.utils.test.readResourceValue

class JsonSchemaTest : StringSpec({
    val registry = SchemaRegistry.withDialect(Dialects.getDraft7()) { builder ->
        builder.schemaLoader { loader ->
            loader.fetchRemoteResources()
        }
    }

    val repositoryConfigurationSchema =
        File("../integrations/schemas/repository-configuration-schema.json").readText()

    fun validate(input: Any?, schema: String) =
        when (input) {
            is File -> registry.getSchema(schema).validate(
                input.readText(),
                if (input.extension == "json") InputFormat.JSON else InputFormat.YAML
            )

            is String -> registry.getSchema(schema).validate(
                input,
                InputFormat.YAML
            )

            else -> registry.getSchema(schema).validate(
                input.toYaml(),
                InputFormat.YAML
            )
        }

    fun validate(input: Any?, schema: File) = validate(input, schema.readText())

    "ORT's own repository configuration file validates successfully" {
        val repositoryConfiguration = File("../$ORT_REPO_CONFIG_FILENAME")

        val errors = validate(repositoryConfiguration, repositoryConfigurationSchema)

        errors should beEmpty()
    }

    "The example ORT repository configuration file validates successfully" {
        val examplesDir = File("../examples")
        val repositoryConfiguration =
            examplesDir.walk().filterTo(mutableListOf()) { it.isFile && it.name.endsWith(ORT_REPO_CONFIG_FILENAME) }

        repositoryConfiguration.forAll {
            val errors = validate(it, repositoryConfigurationSchema)

            errors should beEmpty()
        }
    }

    "Analyzer configuration within a repository configuration validates successfully" {
        val analyzerConfiguration = readResourceValue<RepositoryConfiguration>(
            "/analyzer-repository-configuration.ort.yml"
        ).analyzer
        val analyzerConfigurationSchema = File(
            "../integrations/schemas/repository-configurations/analyzer-configuration-schema.json"
        )

        val errors = validate(analyzerConfiguration, analyzerConfigurationSchema)

        errors should beEmpty()
    }

    "Package manager configuration within a repository configuration validates successfully" {
        val packageManagerConfiguration = readResourceValue<RepositoryConfiguration>(
            "/package-manager-repository-configuration.ort.yml"
        ).analyzer?.packageManagers
        val packageManagerConfigurationSchema = File(
            "../integrations/schemas/repository-configurations/package-manager-configuration-schema.json"
        )

        val errors = validate(packageManagerConfiguration, packageManagerConfigurationSchema)

        errors should beEmpty()
    }

    "The example package curations file validates successfully" {
        val curationsSchema = File("../integrations/schemas/curations-schema.json")
        val curationsExample = File("../examples/$ORT_PACKAGE_CURATIONS_FILENAME")

        val errors = validate(curationsExample, curationsSchema)

        errors should beEmpty()
    }

    "The example package configuration file validates successfully" {
        val packageConfigurationSchema = File("../integrations/schemas/package-configuration-schema.json")
        val packageConfiguration = File("../examples/$ORT_PACKAGE_CONFIGURATION_FILENAME")

        val errors = validate(packageConfiguration, packageConfigurationSchema)

        errors should beEmpty()
    }

    "The example resolutions file validates successfully" {
        val resolutionsSchema = File("../integrations/schemas/resolutions-schema.json")
        val resolutionsExample = File("../examples/$ORT_RESOLUTIONS_FILENAME")

        val errors = validate(resolutionsExample, resolutionsSchema)

        errors should beEmpty()
    }

    "The embedded reference configuration validates successfully" {
        val ortConfigurationSchema = File("../integrations/schemas/ort-configuration-schema.json")
        val referenceConfigFile = File("src/main/resources/$ORT_REFERENCE_CONFIG_FILENAME")

        val errors = validate(referenceConfigFile, ortConfigurationSchema)

        errors should beEmpty()
    }

    "The example license classifications file validates successfully" {
        val licenseClassificationsSchema = File("../integrations/schemas/license-classifications-schema.json")
        val licenseClassificationsExample = File("../examples/$ORT_LICENSE_CLASSIFICATIONS_FILENAME")

        val errors = validate(licenseClassificationsExample, licenseClassificationsSchema)

        errors should beEmpty()
    }

    "Snippet choices validate successfully" {
        val repositoryConfiguration = readResource("/snippet-choices-repository-configuration.ort.yml")

        val errors = validate(repositoryConfiguration, repositoryConfigurationSchema)

        errors should beEmpty()
    }

    "Package configuration with no matchers validates successfully" {
        val packageConfigurationSchema = File("../integrations/schemas/package-configuration-schema.json")
        val configWithNoMatchers = """
            id: "Pip::example-package:0.0.1"
        """.trimIndent()

        val errors = validate(configWithNoMatchers, packageConfigurationSchema)

        errors should beEmpty()
    }

    "Package configuration with only vcs validates successfully" {
        val packageConfigurationSchema = File("../integrations/schemas/package-configuration-schema.json")
        val configWithVcs = """
            id: "Pip::example-package:0.0.1"
            vcs:
              type: "Git"
              url: "https://github.com/example/repo.git"
        """.trimIndent()

        val errors = validate(configWithVcs, packageConfigurationSchema)

        errors should beEmpty()
    }

    "Package configuration with only source_artifact_url validates successfully" {
        val packageConfigurationSchema = File("../integrations/schemas/package-configuration-schema.json")
        val configWithSourceArtifact = """
            id: "Pip::example-package:0.0.1"
            source_artifact_url: "https://example.com/package.tar.gz"
        """.trimIndent()

        val errors = validate(configWithSourceArtifact, packageConfigurationSchema)

        errors should beEmpty()
    }

    "Package configuration with only source_code_origin validates successfully" {
        val packageConfigurationSchema = File("../integrations/schemas/package-configuration-schema.json")
        val configWithSourceCodeOrigin = """
            id: "Pip::example-package:0.0.1"
            source_code_origin: "VCS"
        """.trimIndent()

        val errors = validate(configWithSourceCodeOrigin, packageConfigurationSchema)

        errors should beEmpty()
    }

    "Package configuration with vcs and source_artifact_url fails validation" {
        val packageConfigurationSchema = File("../integrations/schemas/package-configuration-schema.json")
        val configWithVcsAndSourceArtifact = """
            id: "Pip::example-package:0.0.1"
            vcs:
              type: "Git"
              url: "https://github.com/example/repo.git"
            source_artifact_url: "https://example.com/package.tar.gz"
        """.trimIndent()

        val errors = validate(configWithVcsAndSourceArtifact, packageConfigurationSchema)

        errors shouldNot beEmpty()
    }

    "Package configuration with vcs and source_code_origin fails validation" {
        val packageConfigurationSchema = File("../integrations/schemas/package-configuration-schema.json")
        val configWithVcsAndSourceCodeOrigin = """
            id: "Pip::example-package:0.0.1"
            vcs:
              type: "Git"
              url: "https://github.com/example/repo.git"
            source_code_origin: "VCS"
        """.trimIndent()

        val errors = validate(configWithVcsAndSourceCodeOrigin, packageConfigurationSchema)

        errors shouldNot beEmpty()
    }

    "Package configuration with source_artifact_url and source_code_origin fails validation" {
        val packageConfigurationSchema = File("../integrations/schemas/package-configuration-schema.json")
        val configWithSourceArtifactAndSourceCodeOrigin = """
            id: "Pip::example-package:0.0.1"
            source_artifact_url: "https://example.com/package.tar.gz"
            source_code_origin: "ARTIFACT"
        """.trimIndent()

        val errors = validate(configWithSourceArtifactAndSourceCodeOrigin, packageConfigurationSchema)

        errors shouldNot beEmpty()
    }

    "Package configuration with all three matchers fails validation" {
        val packageConfigurationSchema = File("../integrations/schemas/package-configuration-schema.json")
        val configWithAllMatchers = """
            id: "Pip::example-package:0.0.1"
            vcs:
              type: "Git"
              url: "https://github.com/example/repo.git"
            source_artifact_url: "https://example.com/package.tar.gz"
            source_code_origin: "VCS"
        """.trimIndent()

        val errors = validate(configWithAllMatchers, packageConfigurationSchema)

        errors shouldNot beEmpty()
    }
})
