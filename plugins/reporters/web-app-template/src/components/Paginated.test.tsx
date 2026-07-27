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
import { describe, expect, it } from "vitest";

import { Paginated } from "@/components/Paginated";

const items = Array.from({ length: 12 }, (_, i) => `Item ${i + 1}`);

function renderPaginated() {
    return render(
        <Paginated
            getKey={(item) => item}
            itemLabel="items"
            items={items}
            pageSize={4}
            renderItem={(item) => <div>{item}</div>}
        />,
    );
}

describe("Paginated", () => {
    it("renders only the first page of items", () => {
        renderPaginated();
        expect(screen.getByText("Item 1")).toBeInTheDocument();
        expect(screen.getByText("Item 4")).toBeInTheDocument();
        expect(screen.queryByText("Item 5")).not.toBeInTheDocument();
    });

    it("shows the range summary and disables Previous on the first page", () => {
        renderPaginated();
        // The en dash between the bounds is matched with "." to avoid depending on the exact glyph.
        expect(screen.getByText(/^1.4 of 12 items$/)).toBeInTheDocument();
        expect(screen.getByText("Page 1 of 3")).toBeInTheDocument();
        expect(screen.getByRole("button", { name: "Previous page" })).toBeDisabled();
    });

    it("advances to the next page and back to the previous one", async () => {
        const user = userEvent.setup();
        renderPaginated();

        await user.click(screen.getByRole("button", { name: "Next page" }));
        expect(screen.getByText("Item 5")).toBeInTheDocument();
        expect(screen.getByText("Item 8")).toBeInTheDocument();
        expect(screen.queryByText("Item 1")).not.toBeInTheDocument();
        expect(screen.getByText(/^5.8 of 12 items$/)).toBeInTheDocument();

        await user.click(screen.getByRole("button", { name: "Previous page" }));
        expect(screen.getByText("Item 1")).toBeInTheDocument();
        expect(screen.queryByText("Item 5")).not.toBeInTheDocument();
    });
});
