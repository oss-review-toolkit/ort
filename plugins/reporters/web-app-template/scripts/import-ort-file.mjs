/*
 * Copyright (C) 2026 The ORT Project Copyright Holders <https://github.com/oss-review-toolkit/ort/blob/main/NOTICE>
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

/*
 * Embed an ORT EvaluatedModel JSON file into index.html: replaces the contents of
 * <script type="application/json" id="ort-report-data"> with the contents of the given file, so the
 * dev app (and the test fixtures that read index.html) render that report.
 *
 * Usage: npm run import-ort-file -- <path-to-evaluated-model.json>
 */

import { readFileSync, writeFileSync } from "node:fs";
import { resolve } from "node:path";

const OPEN_TAG = '<script type="application/json" id="ort-report-data">';
const CLOSE_TAG = "</script>";

function fail(message) {
    console.error(`✗ ${message}`);
    process.exit(1);
}

const inputArg = process.argv[2];
if (!inputArg) {
    fail("Usage: npm run import-ort-file -- <path-to-evaluated-model.json>");
}

const indexHtmlPath = resolve(process.cwd(), "index.html");
const inputPath = resolve(process.cwd(), inputArg);

let data;
try {
    data = readFileSync(inputPath, "utf-8").trim();
} catch (error) {
    fail(`Cannot read ${inputArg}: ${error.message}`);
}

// Validate that the file is JSON before embedding it, so a bad file fails here instead of in the app.
try {
    JSON.parse(data);
} catch (error) {
    fail(`${inputArg} is not valid JSON: ${error.message}`);
}

let html;
try {
    html = readFileSync(indexHtmlPath, "utf-8");
} catch (error) {
    fail(`Cannot read index.html: ${error.message}`);
}

const openIndex = html.indexOf(OPEN_TAG);
if (openIndex === -1) {
    fail(`Could not find ${OPEN_TAG} in index.html.`);
}
const contentStart = openIndex + OPEN_TAG.length;
const contentEnd = html.indexOf(CLOSE_TAG, contentStart);
if (contentEnd === -1) {
    fail("Could not find the closing </script> for the report data in index.html.");
}

// Reuse the indentation of the opening <script> line for the closing tag so the markup stays tidy.
const lineStart = html.lastIndexOf("\n", openIndex) + 1;
const indent = html.slice(lineStart, openIndex);

const before = html.slice(0, contentStart);
const after = html.slice(contentEnd);
const updated = `${before}\n${data}\n${indent}${after}`;

writeFileSync(indexHtmlPath, updated, "utf-8");

console.log(`✓ Imported ${inputArg} (${data.length.toLocaleString()} chars) into index.html`);
