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
import userEvent from "@testing-library/user-event";
import { beforeAll, describe, expect, it, vi } from "vitest";

import { ResultsLicenses } from "@/components/ResultsLicenses";
import type WebAppEvaluatedModel from "@/models/WebAppEvaluatedModel";
import { buildResult, loadSampleEvaluatedModel } from "@/test/fixture";

describe("ResultsLicenses", () => {
    let result: WebAppEvaluatedModel;

    beforeAll(async () => {
        result = await buildResult(loadSampleEvaluatedModel());
    });

    it("renders a tab and stats table for the effective licenses", () => {
        render(<ResultsLicenses webAppEvaluatedModel={result} />);
        expect(screen.getByRole("tab", { name: /effective/i })).toBeInTheDocument();
        expect(screen.getByRole("columnheader", { name: /license/i })).toBeInTheDocument();
    });

    it("reports the clicked license and its type through onLicenseClick", async () => {
        const onLicenseClick = vi.fn();
        const user = userEvent.setup();
        render(<ResultsLicenses onLicenseClick={onLicenseClick} webAppEvaluatedModel={result} />);

        // BSD-3-Clause is the most-used effective license in the sample, so it sorts to the first page.
        await user.click(screen.getByRole("button", { name: "BSD-3-Clause" }));

        expect(onLicenseClick).toHaveBeenCalledWith("BSD-3-Clause", "effective");
    });
});
