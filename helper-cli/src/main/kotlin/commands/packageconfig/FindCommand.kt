/*
 * Copyright (C) 2020 HERE Europe B.V.
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

package org.ossreviewtoolkit.helper.commands.packageconfig

import com.github.ajalt.clikt.core.CliktCommand
import com.github.ajalt.clikt.parameters.options.convert
import com.github.ajalt.clikt.parameters.options.option
import com.github.ajalt.clikt.parameters.options.required
import com.github.ajalt.clikt.parameters.types.file

import org.ossreviewtoolkit.model.FileFormat
import org.ossreviewtoolkit.model.Identifier
import org.ossreviewtoolkit.model.config.PackageConfiguration
import org.ossreviewtoolkit.model.readValue
import org.ossreviewtoolkit.utils.common.expandTilde

internal class FindCommand : CliktCommand(
    help = "Searches the given directory for a package configuration file matching the given identifier." +
            "If found the absolute path is written to the output."
) {
    private val packageId by option(
        "--package-id",
        help = "The target package for which the package configurations shall be searched."
    ).convert { Identifier(it) }
        .required()

    private val packageConfigurationDir by option(
        "--package-configuration-dir",
        help = "The package configurations directory."
    ).convert { it.expandTilde() }
        .file(mustExist = false, canBeFile = false, canBeDir = true, mustBeWritable = false, mustBeReadable = false)
        .convert { it.absoluteFile.normalize() }
        .required()

    override fun run() {
        // TODO: There could be multiple package configurations matching the given identifier which is not handled.
        FileFormat.findFilesWithKnownExtensions(packageConfigurationDir).find {
            it.readValue<PackageConfiguration>().id == packageId
        }?.let {
            println(it.absolutePath)
        }
    }
}
