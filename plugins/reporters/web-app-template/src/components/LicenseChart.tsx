/*
 * Copyright (C) 2019 The ORT Project Copyright Holders <https://github.com/oss-review-toolkit/ort/blob/main/NOTICE>
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
import { Cell, Pie, PieChart, ResponsiveContainer, Sector } from "recharts";

export interface LicenseChartDatum {
    color: string;
    name: string;
    value: number;
}

export interface LicenseChartProps {
    dataKey?: keyof LicenseChartDatum;
    height?: number;
    licenses: readonly LicenseChartDatum[];
    title?: string;
}

interface ActiveShapeProps {
    cx?: number;
    cy?: number;
    endAngle?: number;
    innerRadius?: number;
    midAngle?: number;
    outerRadius?: number;
    payload?: LicenseChartDatum;
    percent?: number;
    startAngle?: number;
    value?: number;
}

function renderActiveShape(props: unknown): JSX.Element {
    const {
        cx = 0,
        cy = 0,
        midAngle = 0,
        innerRadius = 0,
        outerRadius = 0,
        startAngle = 0,
        endAngle = 0,
        payload,
        percent = 0,
        value = 0,
    } = (props as ActiveShapeProps) ?? {};
    if (!payload) return <g />;

    const RADIAN = Math.PI / 180;
    const sin = Math.sin(-RADIAN * midAngle);
    const cos = Math.cos(-RADIAN * midAngle);
    const sx = cx + (outerRadius + 10) * cos;
    const sy = cy + (outerRadius + 10) * sin;
    const mx = cx + (outerRadius + 30) * cos;
    const my = cy + (outerRadius + 30) * sin;
    const ex = mx + (cos >= 0 ? 1 : -1) * 22;
    const ey = my;
    const textAnchor = cos >= 0 ? "start" : "end";

    return (
        <g>
            <text dy={8} fill="currentColor" textAnchor="middle" x={cx} y={cy}>
                {payload.name}
            </text>
            <Sector
                cx={cx}
                cy={cy}
                endAngle={endAngle}
                fill={payload.color}
                innerRadius={innerRadius}
                outerRadius={outerRadius}
                startAngle={startAngle}
            />
            <Sector
                cx={cx}
                cy={cy}
                endAngle={endAngle}
                fill={payload.color}
                innerRadius={outerRadius + 6}
                outerRadius={outerRadius + 10}
                startAngle={startAngle}
            />
            <path d={`M${sx},${sy}L${mx},${my}L${ex},${ey}`} fill="none" stroke={payload.color} />
            <circle cx={ex} cy={ey} fill={payload.color} r={2} stroke="none" />
            <text fill="currentColor" textAnchor={textAnchor} x={ex + (cos >= 0 ? 1 : -1) * 12} y={ey}>
                {`${value} package(s)`}
            </text>
            <text
                dy={18}
                fill="currentColor"
                opacity={0.6}
                textAnchor={textAnchor}
                x={ex + (cos >= 0 ? 1 : -1) * 12}
                y={ey}
            >
                {`${(percent * 100).toFixed(2)}%`}
            </text>
        </g>
    );
}

// A pie chart of license distribution, used by the Summary and Licenses views.
function LicenseChart({ dataKey = "value", height = 500, licenses, title }: LicenseChartProps): JSX.Element {
    const data = licenses.map((entry) => ({ ...entry }));

    return (
        <div className="w-full">
            {title ? <h3 className="mb-2 font-semibold text-sm">{title}</h3> : null}
            <ResponsiveContainer height={height} width="100%">
                <PieChart>
                    <Pie
                        activeShape={renderActiveShape}
                        data={data}
                        dataKey={dataKey}
                        innerRadius="55%"
                        outerRadius="70%"
                    >
                        {data.map((entry) => (
                            <Cell fill={entry.color} key={entry.name} />
                        ))}
                    </Pie>
                </PieChart>
            </ResponsiveContainer>
        </div>
    );
}

export { LicenseChart };
export default LicenseChart;
