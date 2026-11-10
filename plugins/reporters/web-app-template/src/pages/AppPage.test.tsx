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
import userEvent from "@testing-library/user-event";
import { beforeAll, beforeEach, describe, expect, it, vi } from "vitest";

import type WebAppEvaluatedModel from "@/models/WebAppEvaluatedModel";
import AppPage from "@/pages/AppPage";
import { buildResult, loadSampleEvaluatedModel } from "@/test/fixture";
import { render } from "@/test/render";

const STORAGE_KEY = "ort-sidebar-collapsed";

describe("AppPage sidebar persistence", () => {
    let result: WebAppEvaluatedModel;

    beforeAll(async () => {
        result = await buildResult(loadSampleEvaluatedModel());
    });

    beforeEach(() => {
        window.localStorage.clear();
        window.history.replaceState(null, "", "/");
    });

    it("defaults to expanded and persists sidebar changes", async () => {
        const user = userEvent.setup();
        render(<AppPage webAppEvaluatedModel={result} />);

        expect(window.localStorage.getItem(STORAGE_KEY)).toBe("false");
        await user.click(screen.getByRole("button", { name: "Collapse sidebar" }));

        expect(screen.getByRole("button", { name: "Expand sidebar" })).toBeInTheDocument();
        expect(window.localStorage.getItem(STORAGE_KEY)).toBe("true");

        await user.click(screen.getByRole("button", { name: "Expand sidebar" }));

        expect(screen.getByRole("button", { name: "Collapse sidebar" })).toBeInTheDocument();
        expect(window.localStorage.getItem(STORAGE_KEY)).toBe("false");
    });

    it("restores a persisted collapsed sidebar", () => {
        window.localStorage.setItem(STORAGE_KEY, "true");
        render(<AppPage webAppEvaluatedModel={result} />);

        expect(screen.getByRole("button", { name: "Expand sidebar" })).toBeInTheDocument();
    });

    it.each(["access", "read", "write"] as const)(
        "keeps the sidebar usable when storage %s throws",
        async (operation) => {
            const user = userEvent.setup();
            const fail = (): never => {
                throw new DOMException("The operation is insecure", "SecurityError");
            };

            if (operation === "access") {
                vi.spyOn(window, "localStorage", "get").mockImplementation(fail);
            } else {
                vi.spyOn(Storage.prototype, operation === "read" ? "getItem" : "setItem").mockImplementation(fail);
            }

            render(<AppPage webAppEvaluatedModel={result} />);

            await user.click(screen.getByRole("button", { name: "Collapse sidebar" }));
            expect(screen.getByRole("button", { name: "Expand sidebar" })).toBeInTheDocument();

            await user.click(screen.getByRole("button", { name: "Expand sidebar" }));
            expect(screen.getByRole("button", { name: "Collapse sidebar" })).toBeInTheDocument();
        },
    );
});
