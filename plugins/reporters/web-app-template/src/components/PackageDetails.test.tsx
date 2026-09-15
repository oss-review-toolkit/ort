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

import { screen } from "@testing-library/react";
import { afterEach, beforeAll, describe, expect, it } from "vitest";
import { PackageDetails } from "@/components/PackageDetails";
import { BROWSER_DATE_FORMAT, convertIso8601Date2Sentence } from "@/lib/dates";
import type WebAppEvaluatedModel from "@/models/WebAppEvaluatedModel";
import WebAppPackage from "@/models/WebAppPackage";
import { buildResult, loadSampleEvaluatedModel } from "@/test/fixture";
import { render } from "@/test/render";

// An instant late enough in the UTC day that it lands on the previous date in the Americas.
const LATE_NIGHT_UTC = "2022-06-29T01:52:10Z";
const LATE_NIGHT_PACKAGE = "Maven:junit:junit:4.12";

describe("PackageDetails", () => {
    let result: WebAppEvaluatedModel;

    beforeAll(async () => {
        result = await buildResult(loadSampleEvaluatedModel());
    });

    // Settings persist in local storage, so a test that seeds them must not leak into the next one.
    afterEach(() => {
        window.localStorage.clear();
    });

    it("renders the identity of a realistic package", () => {
        const pkg = result.packages[0];
        expect(pkg).toBeDefined();
        if (!pkg) return;

        render(<PackageDetails pkg={pkg} />);
        expect(screen.getByText("Id")).toBeInTheDocument();
        expect(screen.getByText(pkg.id ?? "")).toBeInTheDocument();
    });

    it("lays every rendered field out as a definition-list row", () => {
        const pkg = result.packages[0];
        if (!pkg) return;

        const { container } = render(<PackageDetails pkg={pkg} />);
        // Every field is emitted as a <dt>/<dd> pair; a realistic package has at least the Id term.
        expect(container.querySelectorAll("dt").length).toBeGreaterThan(0);
        expect(container.querySelectorAll("dd").length).toBeGreaterThan(0);
    });

    it("does not fall back to the empty state for a realistic package", () => {
        const pkg = result.packages[0];
        if (!pkg) return;

        render(<PackageDetails pkg={pkg} />);
        expect(screen.queryByText("No package details.")).not.toBeInTheDocument();
    });

    it("shows the empty state when the package carries no details", () => {
        render(<PackageDetails pkg={new WebAppPackage()} />);
        expect(screen.getByText("No package details.")).toBeInTheDocument();
    });

    it("shows the publish date when the package carries one", () => {
        // Noon UTC so the calendar year cannot shift under whatever local timezone the test runs in.
        const pkg = new WebAppPackage({ _id: 0, id: "Maven:junit:junit:4.12", published_at: "2014-12-04T12:00:00Z" });

        render(<PackageDetails pkg={pkg} />);
        expect(screen.getByText("Published")).toBeInTheDocument();
        expect(screen.getByText(/2014/)).toBeInTheDocument();
    });

    it("shows the publish date of a package from the sample model", () => {
        const pkg = result.packages.find((candidate) => candidate.publishedAt);
        expect(pkg).toBeDefined();
        if (!pkg?.publishedAt) return;

        const { container } = render(<PackageDetails pkg={pkg} />);
        expect(screen.getByText("Published")).toBeInTheDocument();
        // Both the helper and getFullYear() resolve in the local timezone, so the years agree.
        expect(container.textContent).toContain(String(new Date(pkg.publishedAt).getFullYear()));
    });

    it("renders publish dates in UTC by default, whatever the reader's timezone", () => {
        const pkg = new WebAppPackage({ _id: 0, id: LATE_NIGHT_PACKAGE, published_at: LATE_NIGHT_UTC });

        render(<PackageDetails pkg={pkg} />);
        // 01:52 UTC falls on the previous day in the Americas. Pinning the report to UTC is what stops two
        // readers of the same report disagreeing about the release date, so assert the exact string.
        expect(screen.getByText("1:52 AM UTC on June 29, 2022")).toBeInTheDocument();
    });

    it("switches to the reader's own locale and timezone when the setting is on", () => {
        window.localStorage.setItem("ort-settings", JSON.stringify({ useBrowserDateFormat: true }));
        const pkg = new WebAppPackage({ _id: 0, id: LATE_NIGHT_PACKAGE, published_at: LATE_NIGHT_UTC });

        render(<PackageDetails pkg={pkg} />);
        // Comparing against the helper's own browser-format output keeps this true in any test timezone,
        // including a runner that happens to be on UTC.
        const expected = convertIso8601Date2Sentence(LATE_NIGHT_UTC, BROWSER_DATE_FORMAT);
        expect(screen.getByText(expected)).toBeInTheDocument();
    });

    it("omits the publish date when the package carries none", () => {
        const pkg = new WebAppPackage({ _id: 0, id: "Maven:junit:junit:4.12" });

        render(<PackageDetails pkg={pkg} />);
        expect(screen.queryByText("Published")).not.toBeInTheDocument();
    });
});
