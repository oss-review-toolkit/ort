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

import { render, screen } from "@testing-library/react";
import { describe, expect, it } from "vitest";

import { CvssVectorChart } from "@/components/CvssVectorChart";

describe("CvssVectorChart", () => {
    it("renders nothing for an empty list of vectors", () => {
        const { container } = render(<CvssVectorChart vectors={[]} />);
        expect(container).toBeEmptyDOMElement();
    });

    it("renders nothing for a vector whose sub-scores cannot be derived", () => {
        const { container } = render(<CvssVectorChart vectors={[{ vector: "not-a-cvss-vector" }]} />);
        expect(container).toBeEmptyDOMElement();
    });

    it("mounts a chart for a valid vector without throwing", () => {
        // recharts renders into a 0-size container under jsdom, so only assert that the chart mounted
        // (heading text plus rendered content), not any specific SVG geometry.
        const { container } = render(
            <div style={{ height: 400, width: 600 }}>
                <CvssVectorChart vectors={[{ score: 9.8, vector: "CVSS:3.1/AV:N/AC:L/PR:N/UI:N/S:U/C:H/I:H/A:H" }]} />
            </div>,
        );
        expect(screen.getAllByText(/CVSS Score/).length).toBeGreaterThan(0);
        expect(container).not.toBeEmptyDOMElement();
    });
});
