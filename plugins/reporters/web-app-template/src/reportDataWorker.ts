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

// Off-main-thread decoder for the embedded ORT report. Decoding a large report (base64 decode, gzip
// inflate and JSON.parse) is atomic, main-thread-blocking work that freezes the tab on big scans, so
// it runs here in a Web Worker. The worker is imported with Vite's `?worker&inline` suffix, which
// bundles it as an inlined Blob - keeping the reporter's single-HTML-file output intact.

import { payloadToEvaluatedModel } from "@/lib/reportData";

export interface ReportWorkerRequest {
    payload: string;
    gzip: boolean;
}

export type ReportWorkerResponse =
    | { type: "progress"; text: string; percent: number }
    | { type: "done"; data: unknown }
    | { type: "error"; message: string };

// The DOM lib (not the WebWorker lib) is configured for the project, so `self` is typed as a window.
// A minimal local interface avoids pulling in the conflicting WebWorker lib just for two members.
interface WorkerScope {
    postMessage(message: unknown): void;
    addEventListener(type: "message", listener: (event: MessageEvent) => void): void;
}

const workerScope = self as unknown as WorkerScope;

workerScope.addEventListener("message", (event: MessageEvent) => {
    const { payload, gzip } = event.data as ReportWorkerRequest;

    void (async () => {
        try {
            workerScope.postMessage({
                type: "progress",
                text: gzip ? "Decompressing report data..." : "Parsing report data...",
                percent: 45,
            } satisfies ReportWorkerResponse);

            const data = await payloadToEvaluatedModel(payload, gzip);

            workerScope.postMessage({ type: "done", data } satisfies ReportWorkerResponse);
        } catch (err) {
            workerScope.postMessage({
                type: "error",
                message: err instanceof Error ? err.message : "Unknown error",
            } satisfies ReportWorkerResponse);
        }
    })();
});
