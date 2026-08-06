/*
 * Copyright (C) 2017 The ORT Project Copyright Holders <https://github.com/oss-review-toolkit/ort/blob/main/NOTICE>
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

import type { JSX } from "react";
import { useEffect, useMemo, useState } from "react";

import { payloadToEvaluatedModel } from "@/lib/reportData";
import WebAppEvaluatedModel from "@/models/WebAppEvaluatedModel";
import AppPage from "@/pages/AppPage";
import ErrorPage from "@/pages/ErrorPage";
import LoadingPage from "@/pages/LoadingPage";
import type { ReportWorkerRequest, ReportWorkerResponse } from "@/reportDataWorker";
import ReportDataWorker from "@/reportDataWorker?worker&inline";
import type { EvaluatedModel } from "@/types/evaluatedModelData";
import "@/App.css";

type LoaderStatus =
    | { state: "idle" }
    | { state: "loading"; text: string; percent: number }
    | { state: "ready"; raw: EvaluatedModel }
    | { state: "error"; message: string; submessage?: string };

type ReportPayload = { kind: "placeholder" } | { kind: "data"; payload: string; gzip: boolean };

type ProgressHandler = (text: string, percent: number) => void;

// The placeholder string is exactly 27 characters long. Comparing by length avoids embedding the
// literal placeholder text in the JS bundle, which would otherwise inflate the post-build
// occurrence count beyond the single occurrence inside the data script element.
const PLACEHOLDER_LENGTH = 27;

// Read the embedded report payload off the DOM and remove the script element afterwards. For a large
// report the script's text is tens or hundreds of MB; dropping the node lets that string be garbage
// collected while the worker parses its own copy, roughly halving peak memory during load.
function readReportPayload(): ReportPayload {
    const script = document.getElementById("ort-report-data") as HTMLScriptElement | null;
    if (!script) {
        throw new Error("Report data script element #ort-report-data not found in document.");
    }

    const raw = (script.textContent ?? "").trim();
    if (raw === "" || raw.length === PLACEHOLDER_LENGTH) {
        return { kind: "placeholder" };
    }

    const type = script.type;
    let gzip: boolean;
    if (type === "application/gzip") {
        gzip = true;
    } else if (type === "application/json" || type === "") {
        gzip = false;
    } else {
        throw new Error(`Unsupported report data type "${type}".`);
    }

    script.remove();
    return { kind: "data", payload: raw, gzip };
}

// Progress from the single in-flight load is forwarded to whichever App mount is currently active.
// StrictMode mounts App twice in dev, so the sink is swapped per mount rather than bound to one.
let activeProgress: ProgressHandler | null = null;
const emitProgress: ProgressHandler = (text, percent) => activeProgress?.(text, percent);

// Decode the payload into an EvaluatedModel. The decode (base64 + gzip inflate + JSON.parse) is the
// single biggest main-thread stall for large reports, so it runs in an inlined Web Worker to keep the
// tab responsive and let the progress bar animate. Browsers without Worker/DecompressionStream fall
// back to decoding on the main thread. The worker terminates itself once it reports done or error.
function decodeReport(payload: string, gzip: boolean): Promise<EvaluatedModel> {
    if (typeof Worker === "undefined" || typeof DecompressionStream === "undefined") {
        emitProgress("Parsing report data...", 45);
        return payloadToEvaluatedModel(payload, gzip);
    }

    return new Promise<EvaluatedModel>((resolve, reject) => {
        const worker = new ReportDataWorker();

        worker.onmessage = (event: MessageEvent<ReportWorkerResponse>) => {
            const message = event.data;
            if (message.type === "progress") {
                emitProgress(message.text, message.percent);
            } else if (message.type === "done") {
                worker.terminate();
                resolve(message.data as EvaluatedModel);
            } else {
                worker.terminate();
                reject(new Error(message.message));
            }
        };
        worker.onerror = (event: ErrorEvent) => {
            worker.terminate();
            reject(new Error(event.message || "Report data worker failed."));
        };

        worker.postMessage({ payload, gzip } satisfies ReportWorkerRequest);
    });
}

// readReportPayload removes the script element, so the load must run exactly once - but StrictMode
// double-invokes effects in dev and HMR re-executes this module. Cache the single load promise on
// window so remounts and hot reloads reuse it instead of re-reading the now-removed script element.
type ReportLoad = Promise<EvaluatedModel | "placeholder">;
const LOAD_KEY = "__ortReportDataLoad__";

function startReportLoad(): ReportLoad {
    const payload = readReportPayload();
    if (payload.kind === "placeholder") {
        return Promise.resolve("placeholder");
    }
    return decodeReport(payload.payload, payload.gzip);
}

function loadReportOnce(): ReportLoad {
    const store = window as unknown as Record<string, ReportLoad | undefined>;
    let load = store[LOAD_KEY];
    if (!load) {
        load = startReportLoad();
        store[LOAD_KEY] = load;
    }
    return load;
}

export default function App(): JSX.Element {
    const [status, setStatus] = useState<LoaderStatus>({
        state: "loading",
        text: "Loading report data...",
        percent: 10,
    });

    useEffect(() => {
        let cancelled = false;

        setStatus({ state: "loading", text: "Reading report data...", percent: 30 });
        activeProgress = (text, percent) => {
            if (!cancelled) {
                setStatus({ state: "loading", text, percent });
            }
        };

        loadReportOnce()
            .then((data) => {
                if (cancelled) {
                    return;
                }
                if (data === "placeholder") {
                    setStatus({
                        state: "error",
                        message: "Waiting for report data...",
                        submessage:
                            "Either something went wrong or you are looking at an ORT report template file " +
                            "with no embedded scan results.",
                    });
                    return;
                }
                setStatus({ state: "ready", raw: data });
            })
            .catch((err: unknown) => {
                if (cancelled) {
                    return;
                }
                const message = err instanceof Error ? err.message : "Unknown error";
                setStatus({
                    state: "error",
                    message: "Oops, something went wrong...",
                    submessage: message,
                });
            });

        return () => {
            cancelled = true;
            activeProgress = null;
        };
    }, []);

    const webAppEvaluatedModel = useMemo(
        () => (status.state === "ready" ? new WebAppEvaluatedModel(status.raw) : null),
        [status],
    );

    useEffect(() => {
        if (webAppEvaluatedModel) {
            (window as unknown as { ORT?: WebAppEvaluatedModel }).ORT = webAppEvaluatedModel;
        }
    }, [webAppEvaluatedModel]);

    if (status.state === "error") {
        return <ErrorPage message={status.message} submessage={status.submessage ?? ""} />;
    }

    if (status.state === "ready" && webAppEvaluatedModel) {
        return <AppPage webAppEvaluatedModel={webAppEvaluatedModel} />;
    }

    const text = status.state === "loading" ? status.text : "Loading report data...";
    const percent = status.state === "loading" ? status.percent : undefined;
    return <LoadingPage text={text} {...(percent !== undefined ? { percent } : {})} />;
}
