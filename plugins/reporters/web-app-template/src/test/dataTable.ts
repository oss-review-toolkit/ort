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

/** Show a column that a data table hides by default, as a user would through its "Customize Columns" menu. */
export async function showColumn(label: string): Promise<void> {
    const user = userEvent.setup();
    await user.click(screen.getByRole("button", { name: "Customize Columns" }));
    await user.click(await screen.findByRole("menuitemcheckbox", { name: label }));
}
