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

import type { JSX } from "react";

import { OrtLogo } from "@/components/OrtLogo";
import { Card, CardContent } from "@/components/ui/card";
import { Progress } from "@/components/ui/progress";

export interface LoadingPageProps {
    percent?: number;
    text?: string;
}

function LoadingPage({ percent, text = "Loading report data..." }: LoadingPageProps): JSX.Element {
    const value = typeof percent === "number" ? Math.max(0, Math.min(100, percent)) : undefined;
    const isIndeterminate = value === undefined;

    return (
        <main className="flex min-h-screen flex-col items-center justify-center gap-6 bg-background p-6">
            <OrtLogo />
            <Card className="w-full max-w-md border-0 bg-transparent shadow-none">
                <CardContent className="space-y-4 px-0 pt-6">
                    <p aria-live="polite" className="text-muted-foreground text-sm" role="status">
                        {text}
                    </p>
                    <Progress
                        aria-label={text}
                        className={isIndeterminate ? "animate-pulse" : undefined}
                        value={isIndeterminate ? undefined : value}
                    />
                </CardContent>
            </Card>
        </main>
    );
}

export { LoadingPage };
export default LoadingPage;
