/*
 * Copyright (C) 2019 The ORT Project Copyright Holders <https://github.com/oss-review-toolkit/ort/blob/main/NOTICE>
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

import { Paginated } from "@/components/Paginated";
import { cn } from "@/lib/utils";
import type WebAppPackage from "@/models/WebAppPackage";
import type WebAppPath from "@/models/WebAppPath";

export interface PackagePathsProps {
    paths: readonly WebAppPath[];
}

interface StepItem {
    description?: { definitionFilePath: string; scopeName: string };
    key: string;
    title: string;
}

function buildSteps(webAppPath: WebAppPath): StepItem[] {
    const items: StepItem[] = [];
    const project = webAppPath.project;
    const projectName = webAppPath.projectName ?? project?.id ?? "—";

    items.push({
        key: `project-${projectName}`,
        title: projectName,
        description: {
            definitionFilePath: project?.definitionFilePath ?? "",
            scopeName: webAppPath.scopeName ?? "",
        },
    });

    const pathSet = webAppPath.path;
    if (pathSet) {
        let index = 0;
        pathSet.forEach((pathPackage: WebAppPackage) => {
            items.push({ key: `path-${index}-${pathPackage.id ?? "pkg"}`, title: pathPackage.id ?? "—" });
            index += 1;
        });
    }

    items.push({
        key: `package-${webAppPath.packageId ?? "target"}`,
        title: webAppPath.packageId ?? "—",
    });

    return items;
}

// Renders the dependency paths from project roots down to a package as ordered step lists.
function PackagePaths({ paths }: PackagePathsProps): JSX.Element {
    if (paths.length === 0) {
        return <p className="text-muted-foreground text-sm">No paths.</p>;
    }

    return (
        <Paginated
            containerClassName={cn("grid gap-4", paths.length === 1 ? "grid-cols-1" : "grid-cols-1 sm:grid-cols-2")}
            getKey={(webAppPath) =>
                `${webAppPath.projectName ?? "project"}-${webAppPath.scopeName ?? "scope"}-${webAppPath.packageId ?? "pkg"}`
            }
            itemLabel="paths"
            items={paths}
            pageSize={4}
            renderItem={(webAppPath) => {
                const steps = buildSteps(webAppPath);
                return (
                    <ol>
                        {steps.map((step, stepIndex) => {
                            const isLast = stepIndex === steps.length - 1;
                            return (
                                <li className="flex gap-3" key={step.key}>
                                    <div className="flex flex-col items-center">
                                        <span className="flex size-6 shrink-0 items-center justify-center rounded-full bg-muted-foreground font-semibold text-background text-xs">
                                            {stepIndex + 1}
                                        </span>
                                        {isLast ? null : <span className="w-0.5 flex-1 bg-border" />}
                                    </div>
                                    <div className={cn("flex-1", isLast ? "pb-0" : "pb-4")}>
                                        <p className="break-all font-medium text-sm">{step.title}</p>
                                        {step.description ? (
                                            <dl className="mt-1 grid grid-cols-[max-content_1fr] gap-x-3 gap-y-0.5 text-muted-foreground text-xs">
                                                {step.description.definitionFilePath ? (
                                                    <div className="contents">
                                                        <dt>Defined in</dt>
                                                        <dd className="font-mono">
                                                            {step.description.definitionFilePath}
                                                        </dd>
                                                    </div>
                                                ) : null}
                                                {step.description.scopeName ? (
                                                    <div className="contents">
                                                        <dt>Scope</dt>
                                                        <dd>{step.description.scopeName}</dd>
                                                    </div>
                                                ) : null}
                                            </dl>
                                        ) : null}
                                    </div>
                                </li>
                            );
                        })}
                    </ol>
                );
            }}
        />
    );
}

export { PackagePaths };
export default PackagePaths;
