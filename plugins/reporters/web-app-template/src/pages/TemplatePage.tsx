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

import { ExternalLink } from "lucide-react";
import type { JSX } from "react";

import { OrtLogo } from "@/components/OrtLogo";
import { Card } from "@/components/ui/Card";

/**
 * Shown for the report template that ORT ships, which carries a placeholder instead of scan results.
 *
 * Deliberately not the error page: an empty template is the file working as designed, and dressing it as
 * a failure both alarms the reader and makes a real load error indistinguishable from it. Whoever opens
 * this file has no report yet, so the page says what the file is and gives the commands that produce one.
 *
 * The steps chain, each reading the result the one before it wrote. They write to their own directory
 * rather than the working one, both to keep ORT's output out of the sources being analyzed and because
 * a step refuses to overwrite a result file that already exists. See the getting started tutorial for
 * the full invocations. Only top-level URLs are linked, so a rewrite of the documentation cannot leave
 * this page pointing at pages that no longer exist.
 */
function TemplatePage(): JSX.Element {
    return (
        <main className="flex min-h-screen items-center justify-center bg-background p-6">
            <Card className="w-full max-w-3xl gap-0 overflow-hidden p-0">
                <div className="grid md:grid-cols-[1fr_1.25fr]">
                    <div className="border-b bg-muted p-7 md:border-r md:border-b-0">
                        <OrtLogo className="mb-4 h-10" />
                        <h1 className="mb-2 font-semibold text-base tracking-tight">An empty report</h1>
                        <p className="text-muted-foreground text-sm">
                            Nothing has gone wrong. This is the template ORT fills in with your scan results, and it has
                            none yet.
                        </p>
                    </div>
                    <div className="p-7">
                        <p className="mb-4 text-muted-foreground text-xs uppercase tracking-wider">Produce a report</p>
                        <ol className="grid gap-4">
                            <li className="grid grid-cols-[auto_1fr] items-start gap-3">
                                <span className="mt-0.5 flex size-5.5 items-center justify-center rounded-full bg-primary font-medium font-mono text-[0.7rem] text-primary-foreground">
                                    1
                                </span>
                                <div>
                                    <p className="mb-1 text-muted-foreground text-sm">Resolve the dependencies</p>
                                    <code className="block overflow-x-auto whitespace-nowrap rounded-md border bg-muted px-2.5 py-1.5 font-mono text-xs">
                                        ort analyze -i . -o ort-results
                                    </code>
                                </div>
                            </li>
                            <li className="grid grid-cols-[auto_1fr] items-start gap-3">
                                <span className="mt-0.5 flex size-5.5 items-center justify-center rounded-full bg-primary font-medium font-mono text-[0.7rem] text-primary-foreground">
                                    2
                                </span>
                                <div>
                                    <p className="mb-1 text-muted-foreground text-sm">
                                        Scan for licenses and copyrights
                                    </p>
                                    <code className="block overflow-x-auto whitespace-nowrap rounded-md border bg-muted px-2.5 py-1.5 font-mono text-xs">
                                        ort scan -i ort-results/analyzer-result.yml -o ort-results
                                    </code>
                                </div>
                            </li>
                            <li className="grid grid-cols-[auto_1fr] items-start gap-3">
                                <span className="mt-0.5 flex size-5.5 items-center justify-center rounded-full bg-primary font-medium font-mono text-[0.7rem] text-primary-foreground">
                                    3
                                </span>
                                <div>
                                    <p className="mb-1 text-muted-foreground text-sm">Generate this report</p>
                                    <code className="block overflow-x-auto whitespace-nowrap rounded-md border bg-muted px-2.5 py-1.5 font-mono text-xs">
                                        ort report -f WebApp -i ort-results/scan-result.yml -o ort-results
                                    </code>
                                </div>
                            </li>
                        </ol>
                    </div>
                </div>
                <div className="flex flex-wrap items-center gap-x-6 gap-y-1 border-t px-7 py-3 text-xs">
                    <a
                        className="flex items-center gap-1.5 text-muted-foreground transition-colors hover:text-foreground"
                        href="https://oss-review-toolkit.org/"
                        rel="noopener noreferrer"
                        target="_blank"
                    >
                        Project website
                        <ExternalLink aria-hidden="true" className="size-3" />
                    </a>
                    <a
                        className="flex items-center gap-1.5 text-muted-foreground transition-colors hover:text-foreground"
                        href="https://github.com/oss-review-toolkit/ort"
                        rel="noopener noreferrer"
                        target="_blank"
                    >
                        ORT on GitHub
                        <ExternalLink aria-hidden="true" className="size-3" />
                    </a>
                </div>
            </Card>
        </main>
    );
}

export { TemplatePage };
export default TemplatePage;
