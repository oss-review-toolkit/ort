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

package com.here.ort.helper

import com.beust.jcommander.JCommander
import com.beust.jcommander.Parameter

import com.here.ort.utils.PARAMETER_ORDER_HELP

/**
 * A JCommander command that offers command line help.
 */
abstract class CommandWithHelp {
    @Parameter(
        description = "Display the command line help.",
        names = ["--help", "-h"],
        help = true,
        order = PARAMETER_ORDER_HELP
    )
    private var help = false

    /**
     * Run the command after processing command line help.
     */
    fun run(jc: JCommander): Int {
        if (jc.parsedCommand == null) {
            jc.usage()
            return 0
        }

        if (help) {
            jc.usageFormatter.usage(jc.parsedCommand)
            return 0
        }

        return runCommand(jc)
    }

    /**
     * Run the underlying command.
     */
    protected abstract fun runCommand(jc: JCommander): Int
}
