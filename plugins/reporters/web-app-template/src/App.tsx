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

import type { JSX } from "react";
import { useEffect, useMemo, useState } from "react";

import { payloadToEvaluatedModel } from "@/lib/reportData";
import { type ReportErrorKind, ReportLoadError, toReportErrorDetail, toReportErrorKind } from "@/lib/reportErrors";
import WebAppEvaluatedModel from "@/models/WebAppEvaluatedModel";
import AppPage from "@/pages/AppPage";
import ErrorPage from "@/pages/ErrorPage";
import LoadingPage from "@/pages/LoadingPage";
import TemplatePage from "@/pages/TemplatePage";
import type { ReportWorkerRequest, ReportWorkerResponse } from "@/reportDataWorker";
import ReportDataWorker from "@/reportDataWorker?worker&inline";
import type { EvaluatedModel } from "@/types/evaluatedModelData";
import "@/App.css";

type LoaderStatus =
    | { state: "idle" }
    | { state: "loading"; text: string; percent: number }
    | { state: "ready"; raw: EvaluatedModel }
    | { state: "template" }
    | { state: "error"; detail: string; kind: ReportErrorKind };

type ReportPayload = { kind: "placeholder" } | { kind: "data"; payload: string; gzip: boolean };

type ProgressHandler = (text: string, percent: number) => void;

// The reporter splits the built template on the first occurrence of this token, so it must not appear
// in the bundle as a literal or the split would land in the JavaScript instead of the data element.
// Joining the fragments at run time keeps it out: esbuild folds string concatenation, but not this.
const PLACEHOLDER = ["ORT_REPORT_DATA", "PLACEHOLDER"].join("_");

// Read the embedded report payload off the DOM and remove the script element afterwards. For a large
// report the script's text is tens or hundreds of MB; dropping the node lets that string be garbage
// collected while the worker parses its own copy, roughly halving peak memory during load.
function readReportPayload(): ReportPayload {
    const script = document.getElementById("ort-report-data") as HTMLScriptElement | null;
    if (!script) {
        throw new ReportLoadError("missing-data", "Report data script element #ort-report-data not found in document.");
    }

    const raw = (script.textContent ?? "").trim();
    if (raw === PLACEHOLDER) {
        return { kind: "placeholder" };
    }

    // An empty element is not the template: the one ORT ships always carries the placeholder. Reaching
    // here means the report was written without its data, so say so rather than showing the template
    // page, which would tell the reader nothing had gone wrong.
    if (raw === "") {
        throw new ReportLoadError("missing-data", "Report data script element #ort-report-data is empty.");
    }

    const type = script.type;
    let gzip: boolean;
    if (type === "application/gzip") {
        gzip = true;
    } else if (type === "application/json" || type === "") {
        gzip = false;
    } else {
        throw new ReportLoadError("missing-data", `Unsupported report data type "${type}".`);
    }

    script.remove();
    return { kind: "data", payload: raw, gzip };
}

/** Matches the note in index.html; the floor comes from Tailwind CSS v4. */
const SUPPORTED_BROWSERS = "Chrome 111, Edge 111, Safari 16.4 or Firefox 128, or newer";

// Progress from the single in-flight load is forwarded to whichever App mount is currently active.
// StrictMode mounts App twice in dev, so the sink is swapped per mount rather than bound to one.
let activeProgress: ProgressHandler | null = null;
const emitProgress: ProgressHandler = (text, percent) => activeProgress?.(text, percent);

// Decode the payload into an EvaluatedModel. The decode (base64 + gzip inflate + JSON.parse) is the
// single biggest main-thread stall for large reports, so it runs in an inlined Web Worker to keep the
// tab responsive and let the progress bar animate. Browsers without Worker fall back to decoding on
// the main thread. The worker terminates itself once it reports done or error.
function decodeReport(payload: string, gzip: boolean): Promise<EvaluatedModel> {
    // A browser without DecompressionStream cannot read a gzip payload, and is too old for the stylesheet
    // anyway, so report it rather than failing on a missing global. A missing Worker is different: the
    // main thread can still decode.
    if (gzip && typeof DecompressionStream === "undefined") {
        return Promise.reject(new ReportLoadError("unsupported-browser", `This report needs ${SUPPORTED_BROWSERS}.`));
    }

    if (typeof Worker === "undefined") {
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
                reject(new ReportLoadError("unknown", message.message));
            }
        };
        worker.onerror = (event: ErrorEvent) => {
            worker.terminate();
            reject(new ReportLoadError("unknown", event.message || "Report data worker failed."));
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
    // The function `readReportPayload` throws for a missing or unreadable script element. Turn that
    // into a rejected promise, as a throw here escapes the effect calling this and unmounts the app,
    // leaving a blank page with the message only in the console.
    let payload: ReportPayload;
    try {
        payload = readReportPayload();
    } catch (err: unknown) {
        return Promise.reject(err);
    }

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
                    setStatus({ state: "template" });
                    return;
                }
                setStatus({ state: "ready", raw: data });
            })
            .catch((err: unknown) => {
                if (cancelled) {
                    return;
                }
                setStatus({
                    state: "error",
                    detail: toReportErrorDetail(err),
                    kind: toReportErrorKind(err),
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

    if (status.state === "template") {
        return <TemplatePage />;
    }

    if (status.state === "error") {
        return <ErrorPage detail={status.detail} kind={status.kind} />;
    }

    if (status.state === "ready" && webAppEvaluatedModel) {
        return <AppPage webAppEvaluatedModel={webAppEvaluatedModel} />;
    }

    const text = status.state === "loading" ? status.text : "Loading report data...";
    const percent = status.state === "loading" ? status.percent : undefined;
    return <LoadingPage text={text} {...(percent !== undefined ? { percent } : {})} />;
}
