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

import { readFileSync } from "node:fs";
import path from "node:path";

import { vi } from "vitest";

import WebAppEvaluatedModel from "@/models/WebAppEvaluatedModel";
import type { EvaluatedModel } from "@/types/evaluatedModelData";

let cachedRaw: EvaluatedModel | undefined;

/**
 * The realistic sample evaluated model embedded in index.html is the single source of truth for fixture
 * data, so the tests never drift from what the app actually renders in development.
 */
export function loadSampleEvaluatedModel(): EvaluatedModel {
    if (!cachedRaw) {
        // Vitest runs from the package root, so index.html sits directly in the working directory.
        const html = readFileSync(path.resolve(process.cwd(), "index.html"), "utf-8");
        const match = html.match(/<script type="application\/json" id="ort-report-data">([\s\S]*?)<\/script>/);
        const json = match?.[1]?.trim();
        if (!json || json.length < 100) {
            throw new Error("No embedded ORT report data found in index.html.");
        }
        cachedRaw = JSON.parse(json) as EvaluatedModel;
    }
    return cachedRaw;
}

/**
 * Build a WebAppEvaluatedModel with its dependency trees (and scan results) fully materialised. Both are
 * populated lazily through nested setTimeout(0) calls, so fake timers drain the whole queue first.
 *
 * The timer callbacks are purely synchronous (they build nodes and schedule child timers, without awaiting
 * any promise), so the queue is drained synchronously with runAllTimers(). The async variant flushes the
 * microtask queue between every timer, which for a realistic tree of thousands of nested timers is orders
 * of magnitude slower — enough to exceed the default test timeout.
 */
export async function buildResult(raw: EvaluatedModel): Promise<WebAppEvaluatedModel> {
    vi.useFakeTimers({ loopLimit: 5_000_000 });
    try {
        const result = new WebAppEvaluatedModel(raw);
        vi.runAllTimers();
        return result;
    } finally {
        vi.useRealTimers();
    }
}
