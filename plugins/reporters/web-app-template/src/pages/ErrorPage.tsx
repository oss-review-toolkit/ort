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

import { AlertTriangle } from "lucide-react";
import type { JSX } from "react";

import { Alert, AlertDescription, AlertTitle } from "@/components/ui/alert";

export interface ErrorPageProps {
    message: string;
    submessage?: string;
    title?: string;
}

function ErrorPage({ message, submessage, title = "Error" }: ErrorPageProps): JSX.Element {
    return (
        <main className="flex min-h-screen items-center justify-center bg-background p-6">
            <Alert className="w-full max-w-xl" variant="destructive">
                <AlertTriangle aria-hidden="true" />
                <AlertTitle>{title}</AlertTitle>
                <AlertDescription>
                    <p>{message}</p>
                    {submessage ? <p>{submessage}</p> : null}
                    <p>
                        If you believe you found a bug please file an{" "}
                        <a
                            className="underline underline-offset-4"
                            href="https://github.com/oss-review-toolkit/ort/issues"
                            rel="noopener noreferrer"
                            target="_blank"
                        >
                            issue on GitHub
                        </a>
                        .
                    </p>
                </AlertDescription>
            </Alert>
        </main>
    );
}

export { ErrorPage };
export default ErrorPage;
