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

import copy from 'rollup-plugin-copy'
import { defineConfig } from 'vite'
import { viteSingleFile } from 'vite-plugin-singlefile'

import react from '@vitejs/plugin-react'

// https://vitejs.dev/config/
export default defineConfig({
    build: {
        outDir: 'build',
        reportCompressedSize: false
    },
    plugins: [
        react(),
        viteSingleFile(),
        copy({
            targets: [
                {
                    src: 'build/index.html',
                    dest: 'build/',
                    rename: 'scan-report-template.html',
                    transform: (contents, filename) => contents.toString().replace(
                        /(<script type=")(.*)(" id="ort-report-data">)([\s\S]*?)(<\/script>)/,
                        '$1application/gzip$3ORT_REPORT_DATA_PLACEHOLDER$5')
                }
            ],
            hook: 'writeBundle',
            verbose: true
        })
    ]
})
