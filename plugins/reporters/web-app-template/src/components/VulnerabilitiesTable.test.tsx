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
import { beforeAll, describe, expect, it } from "vitest";

import { VulnerabilitiesTable } from "@/components/VulnerabilitiesTable";
import type WebAppVulnerability from "@/models/WebAppVulnerability";
import { buildResult, loadSampleEvaluatedModel } from "@/test/fixture";

describe("VulnerabilitiesTable", () => {
    let vulnerabilities: readonly WebAppVulnerability[];

    beforeAll(async () => {
        const result = await buildResult(loadSampleEvaluatedModel());
        vulnerabilities = result.vulnerabilities;
    });

    it("renders its column headers", () => {
        render(<VulnerabilitiesTable vulnerabilities={vulnerabilities} />);
        expect(screen.getByRole("columnheader", { name: /rating/i })).toBeInTheDocument();
        expect(screen.getByRole("columnheader", { name: /package/i })).toBeInTheDocument();
        expect(screen.getByRole("columnheader", { name: /summary/i })).toBeInTheDocument();
    });

    it("shows the empty state when there are no vulnerabilities", () => {
        render(<VulnerabilitiesTable vulnerabilities={[]} />);
        expect(screen.getByText("No vulnerabilities")).toBeInTheDocument();
    });
});
