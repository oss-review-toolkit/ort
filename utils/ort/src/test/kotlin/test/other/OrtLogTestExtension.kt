/*
 * Copyright (C) 2021 Bosch.IO GmbH
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

// Note that this deliberately is not an ORT package name.
package test.other

import java.io.File

import org.ossreviewtoolkit.utils.common.CommandLineTool
import org.ossreviewtoolkit.utils.ort.log

/**
 * A test class simulating an external base class for ORT extensions. This is used to check whether extensions are
 * correctly detected over more complex hierarchies.
 */
@Suppress("UnnecessaryAbstractClass")
abstract class OrtLogTestBaseExtension : CommandLineTool

/**
 * A test class simulating an extension of ORT that produces log output. This is used to test whether classes from
 * different packages extending ORT classes can use logging.
 */
class OrtLogTestExtension : OrtLogTestBaseExtension() {
    override fun command(workingDir: File?): String {
        log.info { "Test of the logger." }
        return "success"
    }
}
