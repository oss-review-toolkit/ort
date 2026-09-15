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

import { render as baseRender, type RenderOptions, type RenderResult } from "@testing-library/react";
import type { JSX, ReactElement, ReactNode } from "react";

import { SettingsProvider } from "@/components/SettingsProvider";

function Providers({ children }: { children: ReactNode }): JSX.Element {
    return <SettingsProvider>{children}</SettingsProvider>;
}

/**
 * render() with the providers the app mounts around the whole tree in main.tsx. Components that read the
 * user's settings — anything rendering a timestamp, among others — throw without them, so tests should
 * import this instead of @testing-library/react's render.
 */
export function render(ui: ReactElement, options?: Omit<RenderOptions, "wrapper">): RenderResult {
    return baseRender(ui, { ...options, wrapper: Providers });
}
