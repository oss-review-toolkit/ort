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

import WebAppEvaluatedModel from "@/models/WebAppEvaluatedModel";
import AppPage from "@/pages/AppPage";
import ErrorPage from "@/pages/ErrorPage";
import LoadingPage from "@/pages/LoadingPage";
import type { EvaluatedModel } from "@/types/evaluatedModelData";
import "@/App.css";

type LoaderStatus =
    | { state: "idle" }
    | { state: "loading"; text: string; percent: number }
    | { state: "ready"; raw: EvaluatedModel }
    | { state: "error"; message: string; submessage?: string };

// The placeholder string is exactly 27 characters long. Comparing by length avoids embedding the
// literal placeholder text in the JS bundle, which would otherwise inflate the post-build
// occurrence count beyond the single occurrence inside the data script element.
const PLACEHOLDER_LENGTH = 27;

async function decodeBase64Gzip(b64: string): Promise<string> {
    const binaryString = atob(b64);
    const bytes = new Uint8Array(binaryString.length);
    for (let i = 0; i < binaryString.length; i++) {
        bytes[i] = binaryString.charCodeAt(i);
    }

    const stream = new Blob([bytes]).stream();
    const decompressedStream = stream.pipeThrough(new DecompressionStream("gzip"));
    const response = new Response(decompressedStream);
    return await response.text();
}

async function readReportData(): Promise<EvaluatedModel | "placeholder"> {
    const script = document.getElementById("ort-report-data") as HTMLScriptElement | null;
    if (!script) {
        throw new Error("Report data script element #ort-report-data not found in document.");
    }

    const raw = (script.textContent ?? "").trim();
    if (raw === "" || raw.length === PLACEHOLDER_LENGTH) {
        return "placeholder";
    }

    const type = script.type;
    let json: string;
    if (type === "application/gzip") {
        json = await decodeBase64Gzip(raw);
    } else if (type === "application/json" || type === "") {
        json = raw;
    } else {
        throw new Error(`Unsupported report data type "${type}".`);
    }

    return JSON.parse(json) as EvaluatedModel;
}

export default function App(): JSX.Element {
    const [status, setStatus] = useState<LoaderStatus>({
        state: "loading",
        text: "Loading report data...",
        percent: 10,
    });

    useEffect(() => {
        let cancelled = false;

        const run = async (): Promise<void> => {
            try {
                if (!cancelled) {
                    setStatus({ state: "loading", text: "Reading report data...", percent: 30 });
                }
                await Promise.resolve();
                const data = await readReportData();
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

                setStatus({ state: "loading", text: "Processing report data...", percent: 70 });
                await Promise.resolve();
                if (cancelled) {
                    return;
                }
                setStatus({ state: "ready", raw: data });
            } catch (err) {
                if (cancelled) {
                    return;
                }
                const message = err instanceof Error ? err.message : "Unknown error";
                setStatus({
                    state: "error",
                    message: "Oops, something went wrong...",
                    submessage: message,
                });
            }
        };

        void run();

        return () => {
            cancelled = true;
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
