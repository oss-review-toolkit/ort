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
import { useMemo, useState } from "react";
import { PolarAngleAxis, PolarGrid, PolarRadiusAxis, Radar, RadarChart, Tooltip } from "recharts";

import { computeCvssScores, cvssVersionLabel, cvssVersionRank, parseCvssVector } from "@/lib/cvss";
import { cn } from "@/lib/utils";

export interface CvssVectorInput {
    score?: number | undefined;
    /** Raw scoring system as reported, e.g. "CVSS_V3"; used only when the vector has no version. */
    system?: string | undefined;
    vector: string;
}

export interface CvssVectorChartProps {
    className?: string | undefined;
    vectors: CvssVectorInput[];
}

interface CvssEntry {
    label: string;
    rank: number;
    score: number | undefined;
    vector: string;
}

// The six axes of the severity radar, mirroring the metaeffekt universal CVSS calculator. Each is a
// CVSS sub-score on a 0-10 scale. Axes that a vector does not define fall back to a related score
// (as the reference does), so the shape still closes into a hexagon.
const AXES = [
    { key: "base", label: "Base", full: "Base Score" },
    { key: "adjustedImpact", label: "Adj. Impact", full: "Adjusted Impact" },
    { key: "impact", label: "Impact", full: "Impact" },
    { key: "temporal", label: "Temporal", full: "Temporal" },
    { key: "exploitability", label: "Exploit.", full: "Exploitability" },
    { key: "environmental", label: "Env.", full: "Environmental" },
] as const;

// Derive a version label from a raw scoring system (e.g. "CVSS_V3" -> "v3") when the vector itself
// carries no explicit version.
function systemLabel(system: string | undefined): string {
    if (!system) {
        return "";
    }
    const match = system.match(/v?(\d+)/i);
    return match ? `v${match[1]}` : system;
}

// Collapse the many (often duplicated) references down to one entry per CVSS version, keeping the
// highest-scored vector of each, ordered oldest-to-newest.
function buildEntries(vectors: CvssVectorInput[]): CvssEntry[] {
    const byLabel = new Map<string, CvssEntry>();

    for (const input of vectors) {
        if (!input.vector) {
            continue;
        }
        const { version } = parseCvssVector(input.vector);
        const label = cvssVersionLabel(version) || systemLabel(input.system) || "CVSS";
        const entry: CvssEntry = { label, vector: input.vector, score: input.score, rank: cvssVersionRank(version) };
        const existing = byLabel.get(label);
        if (!existing || (input.score ?? -1) > (existing.score ?? -1)) {
            byLabel.set(label, entry);
        }
    }

    return [...byLabel.values()].sort((a, b) => a.rank - b.rank);
}

function radarData(vector: string, score: number | undefined) {
    const scores = computeCvssScores(vector, score);
    const overall = scores.overall ?? scores.base ?? 0;
    const base = scores.base ?? overall;

    // Only base, impact and exploitability are derived from a base-metrics vector; the temporal,
    // environmental and adjusted-impact axes fall back to the closest available score. CVSS v4.0
    // vectors are not scored per sub-metric, so they plot as a hexagon at the overall score.
    const resolved: Record<string, number> = {
        adjustedImpact: scores.impact ?? overall,
        base,
        environmental: overall,
        exploitability: scores.exploitability ?? overall,
        impact: scores.impact ?? overall,
        temporal: base,
    };

    return AXES.map((axis) => ({
        axis: axis.label,
        full: axis.full,
        value: Math.round((resolved[axis.key] ?? 0) * 10) / 10,
    }));
}

// A radar chart of a CVSS vector's metric values, with a selector to switch between CVSS versions.
function CvssVectorChart({ className, vectors }: CvssVectorChartProps): JSX.Element | null {
    const entries = useMemo(() => buildEntries(vectors), [vectors]);

    // Default to the most severe (highest-scored) version.
    const defaultLabel = useMemo(() => {
        let best: CvssEntry | undefined;
        for (const entry of entries) {
            if (!best || (entry.score ?? -1) > (best.score ?? -1)) {
                best = entry;
            }
        }
        return best?.label ?? "";
    }, [entries]);

    const [selectedLabel, setSelectedLabel] = useState(defaultLabel);
    const selected = entries.find((entry) => entry.label === selectedLabel) ?? entries[0];

    const data = useMemo(() => (selected ? radarData(selected.vector, selected.score) : []), [selected]);

    if (!selected || data.every((point) => point.value === 0)) {
        return null;
    }

    return (
        // Fixed width/height instead of ResponsiveContainer: a radar has no natural aspect ratio to
        // fill, and a fixed size renders identically regardless of how the surrounding column is sized.
        <div className={cn("flex flex-col items-center", className)}>
            <h4 className="mb-1 text-center font-semibold text-muted-foreground text-xs uppercase">
                {selected.score !== undefined ? `CVSS Score ${selected.score}` : "CVSS Score"}
            </h4>
            <RadarChart
                data={data}
                desc={`CVSS sub-scores for ${selected.label}`}
                height={281}
                outerRadius="68%"
                title={selected.score !== undefined ? `CVSS Score ${selected.score}` : "CVSS Score"}
                width={300}
            >
                <PolarGrid stroke="var(--border)" />
                <PolarAngleAxis dataKey="axis" tick={{ fill: "var(--muted-foreground)", fontSize: 11 }} />
                <PolarRadiusAxis axisLine={false} domain={[0, 10]} tick={false} tickCount={6} />
                <Tooltip
                    contentStyle={{
                        background: "var(--popover)",
                        border: "1px solid var(--border)",
                        borderRadius: "var(--radius)",
                        color: "var(--popover-foreground)",
                        fontSize: 12,
                    }}
                    cursor={false}
                    formatter={(value, _name, item) => [
                        typeof value === "number" ? value.toFixed(1) : String(value),
                        (item?.payload as { full?: string })?.full ?? "Score",
                    ]}
                />
                <Radar
                    dataKey="value"
                    dot={{ r: 2.5, fill: "var(--destructive)", stroke: "var(--background)", strokeWidth: 1 }}
                    fill="var(--destructive)"
                    fillOpacity={0.25}
                    isAnimationActive={false}
                    name="CVSS"
                    stroke="var(--destructive)"
                    strokeWidth={2}
                />
            </RadarChart>

            {entries.length > 1 ? (
                <fieldset aria-label="CVSS version" className="mt-1 flex justify-center gap-1 border-0 p-0">
                    {entries.map((entry) => {
                        const isActive = entry.label === selected.label;
                        // Prefix "CVSS" for clarity, unless the label already carries it.
                        const buttonLabel = /^cvss/i.test(entry.label) ? entry.label : `CVSS ${entry.label}`;
                        return (
                            <button
                                aria-pressed={isActive}
                                className={cn(
                                    "rounded-md border px-2 py-0.5 text-xs transition-colors",
                                    isActive
                                        ? "border-border bg-muted font-medium text-foreground"
                                        : "border-transparent text-muted-foreground hover:text-foreground",
                                )}
                                key={entry.label}
                                onClick={() => setSelectedLabel(entry.label)}
                                type="button"
                            >
                                {buttonLabel}
                            </button>
                        );
                    })}
                </fieldset>
            ) : null}
        </div>
    );
}

export { CvssVectorChart };
export default CvssVectorChart;
