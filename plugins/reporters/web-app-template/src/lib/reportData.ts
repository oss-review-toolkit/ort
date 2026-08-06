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

import type { EvaluatedModel } from "@/types/evaluatedModelData";

/**
 * Decode a base64-encoded gzip payload to its JSON text. Uses `DecompressionStream` so the inflate
 * runs incrementally rather than allocating the whole decompressed buffer up front. Both `atob` and
 * `DecompressionStream` exist on the main thread and inside a Web Worker, so this helper is shared by
 * the worker and the main-thread fallback.
 */
export async function decodeBase64Gzip(b64: string): Promise<string> {
    const binaryString = atob(b64);
    const bytes = new Uint8Array(binaryString.length);
    for (let i = 0; i < binaryString.length; i++) {
        bytes[i] = binaryString.charCodeAt(i);
    }

    const stream = new Blob([bytes]).stream().pipeThrough(new DecompressionStream("gzip"));
    return await new Response(stream).text();
}

/**
 * Turn a raw report payload (either plain JSON text or a base64-gzip blob) into the parsed
 * EvaluatedModel. This is the heavy, main-thread-blocking work (decode + inflate + JSON.parse) that
 * the app runs inside a Web Worker so a very large report cannot freeze the browser tab.
 */
export async function payloadToEvaluatedModel(payload: string, gzip: boolean): Promise<EvaluatedModel> {
    const json = gzip ? await decodeBase64Gzip(payload) : payload;
    return JSON.parse(json) as EvaluatedModel;
}
