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

import { PathExcludesTable } from "@/components/PathExcludesTable";
import WebAppPathExclude from "@/models/WebAppPathExclude";
import { buildResult, loadSampleEvaluatedModel } from "@/test/fixture";

describe("PathExcludesTable", () => {
    let pathExcludes: readonly WebAppPathExclude[];

    beforeAll(async () => {
        const result = await buildResult(loadSampleEvaluatedModel());
        pathExcludes = result.pathExcludes;
    });

    it("renders its column headers", () => {
        render(<PathExcludesTable pathExcludes={pathExcludes} />);
        expect(screen.getByRole("columnheader", { name: /reason/i })).toBeInTheDocument();
        expect(screen.getByRole("columnheader", { name: /pattern/i })).toBeInTheDocument();
        expect(screen.getByRole("columnheader", { name: /comment/i })).toBeInTheDocument();
    });

    it("renders a row for a path exclude from the sample model", () => {
        render(<PathExcludesTable pathExcludes={pathExcludes} />);
        expect(screen.getByText("**/*doc*/**")).toBeInTheDocument();
    });

    it("hides the pagination controls while every path exclude fits on one page", () => {
        // The sample carries 33 excludes against a first page size of 50, so they all fit.
        expect(pathExcludes.length).toBeLessThan(50);

        render(<PathExcludesTable pathExcludes={pathExcludes} />);
        expect(screen.queryByText("Rows per page")).not.toBeInTheDocument();
        expect(screen.queryByRole("button", { name: /go to next page/i })).not.toBeInTheDocument();
    });

    it("still paginates once the path excludes outgrow a single page", () => {
        // The first LARGE_TABLE_PAGE_SIZES option is 50, so 51 excludes force a second page.
        const many = Array.from(
            { length: 51 },
            (_, index) => new WebAppPathExclude({ _id: index, pattern: `**/vendor-${index}/**`, reason: "OTHER" }),
        );

        render(<PathExcludesTable pathExcludes={many} />);
        expect(screen.getByText("Rows per page")).toBeInTheDocument();
        expect(screen.getByRole("button", { name: /go to next page/i })).toBeInTheDocument();
    });

    it("shows the empty state when there are no path excludes", () => {
        render(<PathExcludesTable pathExcludes={[]} />);
        expect(screen.getByText("No path excludes")).toBeInTheDocument();
    });
});
