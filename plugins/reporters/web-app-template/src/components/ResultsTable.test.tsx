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
import { beforeAll, describe, expect, it } from "vitest";

import { ResultsTable } from "@/components/ResultsTable";
import { SettingsProvider } from "@/components/SettingsProvider";
import type WebAppEvaluatedModel from "@/models/WebAppEvaluatedModel";
import { buildResult, loadSampleEvaluatedModel } from "@/test/fixture";

// ResultsTable reads the user's column preferences through useSettings, so it must render inside a
// SettingsProvider.
function renderTable(result: WebAppEvaluatedModel): void {
    render(
        <SettingsProvider>
            <ResultsTable webAppEvaluatedModel={result} />
        </SettingsProvider>,
    );
}

describe("ResultsTable", () => {
    let result: WebAppEvaluatedModel;

    beforeAll(async () => {
        result = await buildResult(loadSampleEvaluatedModel());
    });

    it("renders the always-visible Package column header", () => {
        renderTable(result);
        expect(screen.getByRole("columnheader", { name: /package/i })).toBeInTheDocument();
    });

    it("expands and collapses every row from the header toggle", async () => {
        const user = userEvent.setup();
        renderTable(result);

        const toggle = screen.getByRole("button", { name: /expand all rows/i });
        expect(toggle).toBeInTheDocument();

        await user.click(toggle);

        expect(screen.getByRole("button", { name: /collapse all rows/i })).toBeInTheDocument();
        expect(screen.queryByRole("button", { name: /expand all rows/i })).not.toBeInTheDocument();
    });
});
