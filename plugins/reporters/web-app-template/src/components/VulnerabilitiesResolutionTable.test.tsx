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

import { VulnerabilitiesResolutionTable } from "@/components/VulnerabilitiesResolutionTable";
import type WebAppVulnerabilityResolution from "@/models/WebAppVulnerabilityResolution";
import { buildResult, loadSampleEvaluatedModel } from "@/test/fixture";

describe("VulnerabilitiesResolutionTable", () => {
    let resolutions: readonly WebAppVulnerabilityResolution[];

    beforeAll(async () => {
        const result = await buildResult(loadSampleEvaluatedModel());
        resolutions = result.vulnerabilityResolutions;
    });

    it("renders its column headers", () => {
        render(<VulnerabilitiesResolutionTable resolutions={resolutions} />);
        expect(screen.getByRole("columnheader", { name: /reason/i })).toBeInTheDocument();
        expect(screen.getByRole("columnheader", { name: /id/i })).toBeInTheDocument();
        expect(screen.getByRole("columnheader", { name: /comment/i })).toBeInTheDocument();
    });

    it("renders a row for a vulnerability resolution from the sample model", () => {
        render(<VulnerabilitiesResolutionTable resolutions={resolutions} />);
        expect(screen.getByText("GHSA-g4m4-9q4c-mfw6")).toBeInTheDocument();
    });

    it("shows the empty state when there are no resolutions", () => {
        render(<VulnerabilitiesResolutionTable resolutions={[]} />);
        expect(screen.getByText("No resolutions")).toBeInTheDocument();
    });
});
