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

import { convertIso8601Date2Sentence } from "@/components/Shared";
import type Run from "@/models/Run";
import type ToolsMetadata from "@/models/ToolsMetadata";

export interface ToolsMetadataCardsProps {
    metadata: ToolsMetadata;
}

interface ToolEntry {
    key: string;
    run: Run | null;
    title: string;
}

function buildRows(run: Run): Array<{ label: string; value: string }> {
    const env = run.environment;
    const dur = durationMinutesSeconds(run.startTime, run.endTime);
    return [
        { label: "Started", value: convertIso8601Date2Sentence(run.startTime) },
        {
            label: "Duration",
            value: dur ? `${dur.mins} minutes ${String(dur.secs).padStart(2, "0")} seconds` : "—",
        },
        { label: "ORT version", value: run.environment?.ortVersion ?? "—" },
        { label: "Java version", value: env?.javaVersion ?? "—" },
        { label: "JDK version", value: env?.buildJdk ?? "—" },
        { label: "OS / CPUs", value: env?.os ? `${env.os} / ${env.processors ?? "—"} CPU` : "—" },
        { label: "Max Memory", value: formatBytesAsMiB(env?.maxMemory) },
    ];
}

interface Duration {
    mins: number;
    secs: number;
}

function durationMinutesSeconds(startIso: string | undefined, endIso: string | undefined): Duration | null {
    if (!startIso || !endIso) return null;
    const trim = (iso: string) => iso.replace(/(\.\d{3})\d*Z$/, "$1Z");
    const s = new Date(trim(startIso));
    const e = new Date(trim(endIso));
    if (Number.isNaN(s.getTime()) || Number.isNaN(e.getTime())) return null;
    const totalSec = Math.max(0, Math.round((e.getTime() - s.getTime()) / 1000));
    return { mins: Math.floor(totalSec / 60), secs: totalSec % 60 };
}

function formatBytesAsMiB(maxMemory: number | undefined): string {
    if (maxMemory === undefined || maxMemory === null) return "—";
    return `${maxMemory / 1024 ** 2} MiB`;
}

// Cards summarising each ORT tool run: name, version, start/end time and environment.
function ToolsMetadataCards({ metadata }: ToolsMetadataCardsProps): JSX.Element {
    const tools = (
        [
            { key: "analyzer", title: "Analyzer", run: metadata.analyzer },
            { key: "scanner", title: "Scanner", run: metadata.scanner },
            { key: "advisor", title: "Advisor", run: metadata.advisor },
            { key: "evaluator", title: "Evaluator", run: metadata.evaluator },
        ] satisfies ToolEntry[]
    ).filter((tool): tool is ToolEntry & { run: Run } => tool.run !== null);

    if (tools.length === 0) {
        return <p className="text-muted-foreground text-sm">No run details available.</p>;
    }

    return (
        <div className="space-y-4">
            {tools.map(({ key, run, title }) => (
                <div className="rounded-md border bg-muted/30 p-4" key={key}>
                    <h3 className="mb-2 font-semibold text-sm">{title}</h3>
                    <dl className="grid grid-cols-[max-content_1fr] gap-x-4 gap-y-2 text-sm">
                        {buildRows(run).map((row) => (
                            <div className="contents" key={row.label}>
                                <dt className="font-medium text-muted-foreground">{row.label}</dt>
                                <dd className="break-all">{row.value}</dd>
                            </div>
                        ))}
                    </dl>
                </div>
            ))}
        </div>
    );
}

export { ToolsMetadataCards };
export default ToolsMetadataCards;
