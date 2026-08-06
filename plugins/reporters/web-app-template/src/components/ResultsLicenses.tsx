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
import { useMemo } from "react";

import { LicenseChart, type LicenseChartDatum } from "@/components/LicenseChart";
import { LicenseStatsTable } from "@/components/LicenseStatsTable";
import { Tabs, TabsContent, TabsList, TabsTrigger } from "@/components/ui/Tabs";
import type WebAppEvaluatedModel from "@/models/WebAppEvaluatedModel";
import { licenseToHslColor } from "@/utils";

export interface ResultsLicensesProps {
    className?: string;
    onLicenseClick?: (license: string, type: "effective" | "declared" | "detected") => void;
    webAppEvaluatedModel: WebAppEvaluatedModel;
}

function buildLicenseStats(
    licenseCounts: ReadonlyMap<string, number>,
    webAppEvaluatedModel: WebAppEvaluatedModel,
): LicenseChartDatum[] {
    const result: LicenseChartDatum[] = [];
    licenseCounts.forEach((value, name) => {
        const license = webAppEvaluatedModel.getLicenseByName(name);
        result.push({
            color: license?.color ?? licenseToHslColor(name),
            name,
            value,
        });
    });
    result.sort((a, b) => b.value - a.value);
    return result;
}

// The Licenses view: a license distribution chart alongside the per-license statistics table.
function ResultsLicenses({ className, onLicenseClick, webAppEvaluatedModel }: ResultsLicensesProps): JSX.Element {
    const { declaredLicensesProcessed, detectedLicensesProcessed, effectiveLicenses, statistics } =
        webAppEvaluatedModel;

    const declaredLicenseStats = useMemo(
        () => buildLicenseStats(statistics.licenses.declared, webAppEvaluatedModel),
        [statistics, webAppEvaluatedModel],
    );
    const detectedLicenseStats = useMemo(
        () => buildLicenseStats(statistics.licenses.detected, webAppEvaluatedModel),
        [statistics, webAppEvaluatedModel],
    );
    const effectiveLicenseStats = useMemo(
        () => buildLicenseStats(statistics.licenses.effective, webAppEvaluatedModel),
        [statistics, webAppEvaluatedModel],
    );

    const hasEffective = webAppEvaluatedModel.hasEffectiveLicenses();
    const hasDeclared = webAppEvaluatedModel.hasDeclaredLicensesProcessed();
    const hasDetected = webAppEvaluatedModel.hasDetectedLicensesProcessed();

    if (!hasEffective && !hasDeclared && !hasDetected) {
        return <p className="text-muted-foreground text-sm">No licenses found.</p>;
    }

    const firstLicenseTab = hasEffective ? "effective" : hasDeclared ? "declared" : "detected";

    return (
        <Tabs className={className} defaultValue={firstLicenseTab}>
            <TabsList className="flex h-auto flex-wrap">
                {hasEffective ? (
                    <TabsTrigger value="effective">Effective ({effectiveLicenses.length})</TabsTrigger>
                ) : null}
                {hasDeclared ? (
                    <TabsTrigger value="declared">Declared ({declaredLicensesProcessed.length})</TabsTrigger>
                ) : null}
                {hasDetected ? (
                    <TabsTrigger value="detected">Detected ({detectedLicensesProcessed.length})</TabsTrigger>
                ) : null}
            </TabsList>
            {hasEffective ? (
                <TabsContent className="mt-4" value="effective">
                    <div className="grid gap-4 lg:grid-cols-2">
                        <LicenseStatsTable
                            emptyText="No effective licenses"
                            handleClick={(license) => onLicenseClick?.(license, "effective")}
                            licenseStats={effectiveLicenseStats}
                        />
                        <LicenseChart height={400} licenses={effectiveLicenseStats} />
                    </div>
                </TabsContent>
            ) : null}
            {hasDeclared ? (
                <TabsContent className="mt-4" value="declared">
                    <div className="grid gap-4 lg:grid-cols-2">
                        <LicenseStatsTable
                            emptyText="No declared licenses"
                            handleClick={(license) => onLicenseClick?.(license, "declared")}
                            licenseStats={declaredLicenseStats}
                        />
                        <LicenseChart height={400} licenses={declaredLicenseStats} />
                    </div>
                </TabsContent>
            ) : null}
            {hasDetected ? (
                <TabsContent className="mt-4" value="detected">
                    <div className="grid gap-4 lg:grid-cols-2">
                        <LicenseStatsTable
                            emptyText="No detected licenses"
                            handleClick={(license) => onLicenseClick?.(license, "detected")}
                            licenseStats={detectedLicenseStats}
                        />
                        <LicenseChart height={400} licenses={detectedLicenseStats} />
                    </div>
                </TabsContent>
            ) : null}
        </Tabs>
    );
}

export { ResultsLicenses };
export default ResultsLicenses;
