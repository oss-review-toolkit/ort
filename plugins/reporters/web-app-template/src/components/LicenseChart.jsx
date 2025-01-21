/*
 * Copyright (C) 2019 The ORT Project Authors (see <https://github.com/oss-review-toolkit/ort/blob/main/NOTICE>)
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

import {
    useState
} from 'react';

import {
    Cell, PieChart, Pie, Sector
} from 'recharts';

const renderActiveShape = ({
    cx,
    cy,
    midAngle = 0,
    innerRadius,
    outerRadius,
    startAngle = 0,
    endAngle = 0,
    payload,
    percent = 0,
    value
}) => {
    const RADIAN = Math.PI / 180;
    const sin = Math.sin(-RADIAN * midAngle);
    const cos = Math.cos(-RADIAN * midAngle);
    const sx = cx + (outerRadius + 10) * cos;
    const sy = cy + (outerRadius + 10) * sin;
    const mx = cx + (outerRadius + 30) * cos;
    const my = cy + (outerRadius + 30) * sin;
    const ex = mx + (cos >= 0 ? 1 : -1) * 22;
    const ey = my;
    const textAnchor = cos >= 0 ? 'start' : 'end';

    return (
        <g>
            <text x={cx} y={cy} dy={8} textAnchor="middle" fill="#333">
                {payload.name}
            </text>
            <Sector
                cx={cx}
                cy={cy}
                innerRadius={innerRadius}
                outerRadius={outerRadius}
                startAngle={startAngle}
                endAngle={endAngle}
                fill={payload.color}
            />
            <Sector
                cx={cx}
                cy={cy}
                startAngle={startAngle}
                endAngle={endAngle}
                innerRadius={outerRadius + 6}
                outerRadius={outerRadius + 10}
                fill={payload.color}
            />
            <path d={`M${sx},${sy}L${mx},${my}L${ex},${ey}`} stroke={payload.color} fill="none" />
            <circle cx={ex} cy={ey} r={2} fill={payload.color} stroke="none" />
            <text x={ex + (cos >= 0 ? 1 : -1) * 12} y={ey} textAnchor={textAnchor} fill="#333">
                {`${value} package(s)`}
            </text>
            <text x={ex + (cos >= 0 ? 1 : -1) * 12} y={ey} dy={18} textAnchor={textAnchor} fill="#999">
                {`${(percent * 100).toFixed(2)}%`}
            </text>
        </g>
    );
};

const LicenseChart = ({
    cx = 400,
    cy = 250,
    dataKey = 'value',
    height = 500,
    licenses = [],
    width = 800
}) => {
    /* === Chart state handling === */
    const [activeIndex, setActiveIndex] = useState(0);

    return (
        <PieChart
            width={width}
            height={height}
        >
            <Pie
                activeIndex={activeIndex}
                activeShape={renderActiveShape}
                data={licenses}
                dataKey={dataKey}
                cx={cx}
                cy={cy}
                innerRadius={135}
                outerRadius={165}
                onMouseEnter={
                    (data, index) => setActiveIndex(index)
                }
            >
                {
                    licenses.map((entry) => <Cell key={entry.name} fill={entry.color} />)
                }
            </Pie>
        </PieChart>
    );
};

export default LicenseChart;
