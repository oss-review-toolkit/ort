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

import { type CvssScores, computeCvssScores, cvssVersionLabel, cvssVersionOf, cvssVersionRank } from "@/lib/cvss";
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
    scores: CvssScores;
    style: SeriesStyle;
}

// The six axes of the severity radar, mirroring the metaeffekt universal CVSS calculator. Each is a
// CVSS sub-score on a 0-10 scale. Axes that a version does not define fall back to a related score
// (as the reference does), so the shape still closes into a hexagon.
const AXES = [
    { key: "base", label: "Base", full: "Base Score" },
    { key: "adjustedImpact", label: "Adj. Impact", full: "Adjusted Impact" },
    { key: "impact", label: "Impact", full: "Impact" },
    { key: "temporal", label: "Temporal", full: "Temporal" },
    { key: "exploitability", label: "Exploit.", full: "Exploitability" },
    { key: "environmental", label: "Env.", full: "Environmental" },
] as const;

interface SeriesStyle {
    color: string;
    /** An SVG dash pattern, so the versions stay apart for a reader who cannot tell the colours apart. */
    dash: string | undefined;
}

// One style per CVSS version, so a version keeps the same colour and line across every report. The
// colours were picked to clear the WCAG 2.2 contrast requirement for graphical objects (1.4.11,
// at least 3:1) against both the light and the dark card background, and the dash patterns keep the
// series distinguishable without relying on colour at all (1.4.1). `CvssVectorChart.test.tsx`
// asserts both.
const SERIES_STYLES: Record<string, SeriesStyle> = {
    "v2.0": { color: "hsl(295, 55%, 53%)", dash: "1 3" },
    "v3.0": { color: "hsl(175, 75%, 31%)", dash: "6 3" },
    "v3.1": { color: "hsl(225, 62%, 59%)", dash: "2 2" },
    "v4.0": { color: "hsl(0, 70%, 56%)", dash: undefined },
};
const FALLBACK_STYLE: SeriesStyle = { color: "hsl(35, 85%, 33%)", dash: "8 2 1 2" };

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
        const version = cvssVersionOf(input.vector);
        const label = cvssVersionLabel(version) || systemLabel(input.system) || "CVSS";
        const existing = byLabel.get(label);
        if (existing && (input.score ?? -1) <= (existing.score ?? -1)) {
            continue;
        }
        byLabel.set(label, {
            label,
            rank: cvssVersionRank(version),
            score: input.score,
            scores: computeCvssScores(input.vector, input.score),
            style: SERIES_STYLES[label] ?? FALLBACK_STYLE,
        });
    }

    return [...byLabel.values()].sort((a, b) => a.rank - b.rank);
}

// The value each axis takes for one version, with the fallbacks the reference calculator applies to
// the axes a version does not define.
function axisValues(scores: CvssScores): Record<string, number> {
    const overall = scores.overall ?? scores.base ?? 0;
    const base = scores.base ?? overall;

    return {
        adjustedImpact: scores.modifiedImpact ?? scores.impact ?? overall,
        base,
        environmental: scores.environmental ?? overall,
        exploitability: scores.exploitability ?? overall,
        impact: scores.impact ?? overall,
        temporal: scores.temporal ?? scores.threat ?? base,
    };
}

// What the chart is called wherever it appears: the heading, the accessible title and the description.
const TITLE = "Severity Radar";

// A radar chart of the CVSS sub-scores, with one series per CVSS version the vulnerability was
// scored under so the versions can be compared at a glance, and switched on and off individually.
function CvssVectorChart({ className, vectors }: CvssVectorChartProps): JSX.Element | null {
    const entries = useMemo(() => buildEntries(vectors), [vectors]);

    const data = useMemo(
        () =>
            AXES.map((axis) => {
                const point: Record<string, number | string> = { axis: axis.label, full: axis.full };
                for (const entry of entries) {
                    point[entry.label] = Math.round((axisValues(entry.scores)[axis.key] ?? 0) * 10) / 10;
                }

                return point;
            }),
        [entries],
    );

    const [hidden, setHidden] = useState<ReadonlySet<string>>(new Set());

    const hasValues = entries.some((entry) => data.some((point) => Number(point[entry.label]) > 0));
    if (entries.length === 0 || !hasValues) {
        return null;
    }

    const shown = entries.filter((entry) => !hidden.has(entry.label));
    const toggle = (label: string) =>
        setHidden((previous) => {
            const next = new Set(previous);
            if (!next.delete(label)) {
                next.add(label);
            }

            return next;
        });

    const described = shown.map((entry) => entry.label).join(", ");

    return (
        // Fixed width/height instead of ResponsiveContainer: a radar has no natural aspect ratio to
        // fill, and a fixed size renders identically regardless of how the surrounding column is sized.
        <div className={cn("flex flex-col items-center", className)}>
            <h4 className="mb-1 text-center font-semibold text-muted-foreground text-xs uppercase">{TITLE}</h4>
            <RadarChart
                data={data}
                desc={`CVSS sub-scores for ${described}`}
                height={281}
                outerRadius="68%"
                title={TITLE}
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
                    formatter={(value, name) => [typeof value === "number" ? value.toFixed(1) : String(value), name]}
                    labelFormatter={(_label, payload) => (payload?.[0]?.payload as { full?: string })?.full ?? "Score"}
                />
                {entries.map((entry) => (
                    <Radar
                        dataKey={entry.label}
                        dot={{ r: 2.5, fill: entry.style.color, stroke: "var(--background)", strokeWidth: 1 }}
                        fill={entry.style.color}
                        // Several series overlap, so keep the fills faint enough to read through.
                        fillOpacity={shown.length > 1 ? 0.12 : 0.25}
                        hide={hidden.has(entry.label)}
                        isAnimationActive={false}
                        key={entry.label}
                        name={entry.label}
                        stroke={entry.style.color}
                        {...(entry.style.dash !== undefined ? { strokeDasharray: entry.style.dash } : {})}
                        strokeWidth={2}
                    />
                ))}
            </RadarChart>

            {/* The legend is written here rather than taken from recharts so that each entry is a real
                button: one that takes keyboard focus, says whether its version is shown, and draws the
                colour and the dash pattern of the series it stands for. */}
            <fieldset aria-label="CVSS versions charted" className="flex flex-wrap justify-center gap-1 border-0 p-0">
                {entries.map((entry) => {
                    const isShown = !hidden.has(entry.label);
                    // Hiding the last remaining version would leave an empty radar behind.
                    const isLastShown = isShown && shown.length === 1;
                    const label = `CVSS ${entry.label}${entry.score !== undefined ? ` (${entry.score})` : ""}`;

                    return (
                        <button
                            aria-pressed={isShown}
                            className={cn(
                                "flex items-center gap-1.5 rounded-md border border-transparent px-2 py-0.5 text-xs",
                                "transition-colors hover:text-foreground focus-visible:ring-[3px] focus-visible:ring-ring/50",
                                isShown ? "text-foreground" : "text-muted-foreground line-through",
                                isLastShown ? "cursor-default" : "cursor-pointer",
                            )}
                            disabled={isLastShown}
                            key={entry.label}
                            onClick={() => toggle(entry.label)}
                            type="button"
                        >
                            <svg aria-hidden="true" height="8" viewBox="0 0 16 8" width="16">
                                <line
                                    stroke={isShown ? entry.style.color : "var(--muted-foreground)"}
                                    strokeDasharray={entry.style.dash}
                                    strokeWidth="2"
                                    x1="0"
                                    x2="16"
                                    y1="4"
                                    y2="4"
                                />
                            </svg>
                            {label}
                        </button>
                    );
                })}
            </fieldset>
        </div>
    );
}

export { AXES, axisValues, buildEntries, CvssVectorChart, FALLBACK_STYLE, SERIES_STYLES };
export default CvssVectorChart;
