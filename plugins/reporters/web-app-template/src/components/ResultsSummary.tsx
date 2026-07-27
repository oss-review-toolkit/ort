/*
 * Copyright (C) 2017 The ORT Project Copyright Holders <https://github.com/oss-review-toolkit/ort/blob/main/NOTICE>
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

import {
    Bug,
    CheckCircle2,
    ChevronRight,
    FileText,
    Folder,
    Layers,
    type LucideIcon,
    Network,
    OctagonAlert,
    Package,
    Scale,
    ShieldAlert,
} from "lucide-react";
import type { JSX, ReactNode } from "react";
import { useMemo } from "react";

import { LICENSE_TERM_DEFINITIONS, Url } from "@/components/Shared";
import { Card } from "@/components/ui/card";
import { indexToRating, type VulnerabilityRatingValue } from "@/components/VulnerabilityRatingBadge";
import { cn } from "@/lib/utils";
import type WebAppEvaluatedModel from "@/models/WebAppEvaluatedModel";

export interface ResultsSummaryProps {
    className?: string;
    // Jump to the table, optionally pre-filtered on the given dependency kinds (null clears filters).
    onFilterByLevel?: (level: string[] | null) => void;
    // Jump to the Run Details tab and open one of its inner tabs (e.g. "package-curations").
    onSelectRunDetailsTab?: (tab: string) => void;
    // Jump to another top-level tab (tech-issues, policy-violations, vulnerabilities, licenses).
    onSelectTab?: (tab: string) => void;
    webAppEvaluatedModel: WebAppEvaluatedModel;
}

const s = (count: number): string => (count === 1 ? "" : "s");

// Pluralise a count label so a value of 1 reads naturally, e.g. "1 Project" instead of "1 Projects".
// Pass an explicit plural form for irregular words (e.g. Dependency -> Dependencies).
function plural(count: number, singular: string, pluralForm = `${singular}s`): string {
    return count === 1 ? singular : pluralForm;
}

interface SeverityBucket {
    // Tailwind border/background/text classes matching the corresponding table badge, so the strip reads
    // in the same (contrast-safe, dark-mode-aware) colours as the Issues/Vulnerabilities tables.
    colorClass: string;
    count: number;
    // Full explanation exposed on hover (native title).
    label: string;
    // The severity spelled out after the count (e.g. "Errors", "Warnings", "Critical"). Naming the
    // severity as a word — not colour alone — is what keeps it legible in grayscale, to colour-blind
    // users, and to screen readers (WCAG 1.4.1).
    text: string;
}

// One severity badge: the open count and its spelled-out severity (e.g. "4 Errors"), coloured to match
// the report tables. The word carries the severity (WCAG 1.4.1); a fuller explanation is on hover.
function SeverityBadge({ colorClass, count, label, text }: SeverityBucket): JSX.Element {
    return (
        <span
            className={cn(
                "inline-flex items-baseline gap-1 whitespace-nowrap rounded-md border px-1.5 py-0 text-[11px] tabular-nums",
                colorClass,
            )}
            title={label}
        >
            <span className="font-semibold">{count}</span> {text}
        </span>
    );
}

// The severity breakdown: a strip of badges, one per severity with a non-zero open count. Rendered only
// when at least one severity is present, and hidden on the narrowest screens.
function SeverityBreakdown({ severities }: { severities: SeverityBucket[] }): JSX.Element | null {
    const openSeverities = severities.filter((severity) => severity.count > 0);
    if (openSeverities.length === 0) return null;
    return (
        <span className="hidden shrink-0 flex-wrap justify-end gap-1 sm:flex">
            {openSeverities.map((severity) => (
                <SeverityBadge key={severity.text} {...severity} />
            ))}
        </span>
    );
}

// Border/background/text badge classes per severity, matching the report tables. Kept in one place so the
// issue, violation and vulnerability strips stay in sync (low reuses hint's yellow, medium reuses warning's
// amber, as in the source data model).
const SEVERITY_STRIP = {
    critical: "border-red-700 bg-red-700/10 text-red-700 dark:text-red-400",
    error: "border-destructive bg-destructive/10 text-destructive",
    high: "border-orange-600 bg-orange-600/10 text-orange-700 dark:text-orange-400",
    hint: "border-yellow-500 bg-yellow-100 text-yellow-800 dark:border-yellow-600 dark:bg-yellow-950 dark:text-yellow-200",
    low: "border-yellow-500 bg-yellow-100 text-yellow-800 dark:border-yellow-600 dark:bg-yellow-950 dark:text-yellow-200",
    medium: "border-amber-500 bg-amber-500/10 text-amber-700 dark:text-amber-400",
    warning: "border-amber-500 bg-amber-500/10 text-amber-700 dark:text-amber-400",
} as const;

// The three error/warning/hint buckets for a technical-issue or policy-violation detector.
function severityBuckets(open: { errors: number; hints: number; warnings: number }, noun: string): SeverityBucket[] {
    return [
        {
            colorClass: SEVERITY_STRIP.error,
            count: open.errors,
            label: `${open.errors} open ${noun}${s(open.errors)} with severity error`,
            text: `Error${s(open.errors)}`,
        },
        {
            colorClass: SEVERITY_STRIP.warning,
            count: open.warnings,
            label: `${open.warnings} open ${noun}${s(open.warnings)} with severity warning`,
            text: `Warning${s(open.warnings)}`,
        },
        {
            colorClass: SEVERITY_STRIP.hint,
            count: open.hints,
            label: `${open.hints} open ${noun}${s(open.hints)} with severity hint`,
            text: `Hint${s(open.hints)}`,
        },
    ];
}

// The four critical/high/medium/low buckets for the vulnerabilities detector, in display order.
const VULNERABILITY_RATINGS = [
    { colorClass: SEVERITY_STRIP.critical, rating: "CRITICAL", text: "Critical" },
    { colorClass: SEVERITY_STRIP.high, rating: "HIGH", text: "High" },
    { colorClass: SEVERITY_STRIP.medium, rating: "MEDIUM", text: "Medium" },
    { colorClass: SEVERITY_STRIP.low, rating: "LOW", text: "Low" },
] as const;

// An inline link that navigates to another tab, used in the status banner call to action.
function TabLink({ children, onClick }: { onClick?: () => void; children: ReactNode }): JSX.Element {
    return (
        <button className="font-medium text-primary underline-offset-4 hover:underline" onClick={onClick} type="button">
            {children}
        </button>
    );
}

interface DetectorRowProps {
    // The one-line description below the detector name.
    description: ReactNode;
    // When true, a non-zero headline count is reddened because it gates the run.
    gate?: boolean;
    icon: LucideIcon;
    // The prominent count (unresolved for gating detectors, open for the rest).
    main: number;
    name: string;
    onClick?: () => void;
    // The severity breakdown, rendered as a strip of mini-badges to the right of the name.
    severities?: SeverityBucket[];
    // The muted line under the count, e.g. "9 resolved" or "effective".
    subLabel: ReactNode;
    // The denominator; omitted for detectors without a total (licenses).
    total?: number;
}

// One row of the findings list: an icon tile, the detector name and description, its severity breakdown,
// and a right-aligned count. The whole row is a button that navigates to the matching tab.
function DetectorRow({
    description,
    gate = false,
    icon: Icon,
    main,
    name,
    onClick,
    severities,
    subLabel,
    total,
}: DetectorRowProps): JSX.Element {
    return (
        <button
            className={cn(
                // flex-1 makes every row take an equal share of the card's height so the four rows fill it.
                "flex w-full flex-1 items-center gap-4 border-t p-4 text-left transition-colors first:border-t-0",
                "hover:bg-muted/60 focus-visible:z-10 focus-visible:outline-none focus-visible:ring-2 focus-visible:ring-ring focus-visible:ring-inset",
            )}
            onClick={onClick}
            type="button"
        >
            <span className="flex size-9 shrink-0 items-center justify-center rounded-lg bg-muted text-muted-foreground">
                <Icon aria-hidden="true" className="size-4.5" />
            </span>
            <span className="min-w-0 flex-1">
                <span className="block font-medium text-sm">{name}</span>
                <span className="mt-0.5 block text-muted-foreground text-xs">{description}</span>
            </span>
            {severities ? <SeverityBreakdown severities={severities} /> : null}
            <span className="w-16 shrink-0 text-right">
                <span className="block font-semibold text-sm tabular-nums">
                    <span className={cn(gate && main > 0 && "text-destructive")}>{main}</span>
                    {total !== undefined ? <span className="font-normal text-muted-foreground"> / {total}</span> : null}
                </span>
                <span className="mt-0.5 block text-[11px] text-muted-foreground">{subLabel}</span>
            </span>
            <ChevronRight aria-hidden="true" className="size-4 shrink-0 text-muted-foreground/50" />
        </button>
    );
}

// One key/value line in the sidebar's Composition list, rendered as a button that navigates to the table.
// The full definition sits behind the native title on hover (matching the licenses row's declared/detected).
function SidebarStat({
    hint,
    icon: Icon,
    label,
    onClick,
    value,
}: {
    hint: string;
    icon: LucideIcon;
    label: string;
    onClick: () => void;
    value: ReactNode;
}): JSX.Element {
    return (
        <button
            className="-mx-1 flex w-[calc(100%+0.5rem)] items-center justify-between gap-2 rounded border-t px-1 py-2 text-left text-sm first:border-t-0 first:pt-0 hover:text-foreground focus-visible:outline-none focus-visible:ring-2 focus-visible:ring-ring"
            onClick={onClick}
            title={hint}
            type="button"
        >
            <span className="flex items-center gap-2 text-muted-foreground">
                <Icon aria-hidden="true" className="size-4 shrink-0" />
                {label}
            </span>
            <span className="font-semibold tabular-nums">{value}</span>
        </button>
    );
}

// One clickable configuration pill in the sidebar (e.g. "18 curations"), navigating to its Run Details
// tab. Its explanation sits behind the native title on hover, matching the Composition rows.
function ConfigTag({
    count,
    label,
    onClick,
    title,
}: {
    count: ReactNode;
    label: string;
    onClick?: () => void;
    title?: string;
}): JSX.Element {
    return (
        <button
            className="inline-flex items-center gap-1.5 rounded-md border bg-muted/40 px-2 py-1 text-muted-foreground text-xs transition-colors hover:bg-muted hover:text-foreground focus-visible:outline-none focus-visible:ring-2 focus-visible:ring-ring"
            onClick={onClick}
            title={title}
            type="button"
        >
            <span className="font-semibold text-foreground tabular-nums">{count}</span>
            {label}
        </button>
    );
}

// A small heading for a sidebar section.
function SidebarHeading({ children }: { children: ReactNode }): JSX.Element {
    return <h3 className="mb-3 font-semibold text-foreground text-xs uppercase tracking-wide">{children}</h3>;
}

// The Summary dashboard, laid out as a security-overview: a run-verdict banner, a findings list (one row
// per detector with its severity breakdown), and an "About this run" sidebar with the repository, the
// scanned inventory, and the configuration applied to the run.
function ResultsSummary({
    className,
    onFilterByLevel,
    onSelectRunDetailsTab,
    onSelectTab,
    webAppEvaluatedModel,
}: ResultsSummaryProps): JSX.Element {
    const {
        declaredLicensesProcessed,
        detectedLicensesProcessed,
        effectiveLicenses,
        issues,
        packages,
        projects,
        ruleViolations,
        scopes,
        statistics,
        vulnerabilities,
    } = webAppEvaluatedModel;

    const { openIssues, openRuleViolations, openVulnerabilities } = statistics;
    // "Unresolved" here means open items at or above the run's configured severe thresholds — the ones
    // that must be resolved for the run to pass — not merely the open errors.
    const unresolvedIssues = webAppEvaluatedModel.severeOpenIssuesCount;
    const unresolvedRuleViolations = webAppEvaluatedModel.severeOpenRuleViolationsCount;
    const resolvedIssues = Math.max(0, issues.length - openIssues.total);
    const resolvedRuleViolations = Math.max(0, ruleViolations.length - openRuleViolations.total);
    const vcsProcessed = webAppEvaluatedModel.repository?.vcsProcessed;

    // Vulnerabilities carry no severe threshold and no per-rating statistics, so tally the list directly.
    const vulnerabilityCounts = useMemo(() => {
        const open: Record<VulnerabilityRatingValue, number> = { CRITICAL: 0, HIGH: 0, MEDIUM: 0, LOW: 0, NONE: 0 };
        let resolved = 0;
        for (const vulnerability of vulnerabilities) {
            if (vulnerability.isResolved) {
                resolved += 1;
            } else {
                open[indexToRating(vulnerability.severityIndex)] += 1;
            }
        }
        return { open, resolved };
    }, [vulnerabilities]);

    // A dependency is "direct" when it appears at dependency-tree level 0, otherwise "transitive".
    const { directDependencies, transitiveDependencies } = useMemo(() => {
        let direct = 0;
        let transitive = 0;
        for (const pkg of packages) {
            if (pkg.isProject) continue;
            if (pkg.hasLevel(0)) direct += 1;
            else transitive += 1;
        }
        return { directDependencies: direct, transitiveDependencies: transitive };
    }, [packages]);

    const issueSeverities = severityBuckets(openIssues, "technical issue");
    const violationSeverities = severityBuckets(openRuleViolations, "policy violation");
    const vulnerabilitySeverities: SeverityBucket[] = VULNERABILITY_RATINGS.map(({ colorClass, rating, text }) => {
        const count = vulnerabilityCounts.open[rating];
        return {
            colorClass,
            count,
            label: `${count} open ${text.toLowerCase()} ${plural(count, "vulnerability", "vulnerabilities")}`,
            text,
        };
    });

    const resolvedVulnerabilities = vulnerabilityCounts.resolved;
    // Every technical issue, policy violation, and vulnerability ORT reported, resolved or not.
    const reportedFindings = issues.length + ruleViolations.length + vulnerabilities.length;
    const severeFindings = unresolvedIssues + unresolvedRuleViolations;
    const resolvedFindings = resolvedIssues + resolvedRuleViolations + resolvedVulnerabilities;

    // Run verdict — the run needs attention when open findings remain at or above the severe thresholds.
    const passed = severeFindings === 0;
    const failureScope = webAppEvaluatedModel.hasExcludes() ? " in non-excluded source code or dependencies" : "";

    const resolvedConfig = statistics.resolvedConfiguration;

    return (
        <div className={cn("space-y-4", className)}>
            {/* Run verdict. Tinted green when the run passed, red when it needs attention. */}
            <div
                className={cn(
                    "flex flex-col gap-3 rounded-xl border p-4 sm:flex-row sm:items-center",
                    passed ? "border-emerald-500/40 bg-emerald-500/5" : "border-destructive/40 bg-destructive/5",
                )}
            >
                <span
                    className={cn(
                        "flex size-9 shrink-0 items-center justify-center rounded-full text-white",
                        passed ? "bg-emerald-600" : "bg-destructive",
                    )}
                >
                    {passed ? (
                        <CheckCircle2 aria-hidden="true" className="size-5" />
                    ) : (
                        <OctagonAlert aria-hidden="true" className="size-5" />
                    )}
                </span>
                <div className="min-w-0 flex-1">
                    <p
                        className={cn(
                            "font-semibold text-sm",
                            passed ? "text-emerald-700 dark:text-emerald-400" : "text-destructive",
                        )}
                    >
                        {passed ? "Run passed" : "Attention required"}
                    </p>
                    <p className="text-muted-foreground text-sm">
                        {passed ? (
                            "No open technical issues or policy violations. You are good to go."
                        ) : (
                            <>
                                Open{" "}
                                {unresolvedIssues > 0 && (
                                    <TabLink onClick={() => onSelectTab?.("tech-issues")}>
                                        {unresolvedIssues} unresolved technical issue{s(unresolvedIssues)}
                                    </TabLink>
                                )}
                                {unresolvedIssues > 0 && unresolvedRuleViolations > 0 && " and "}
                                {unresolvedRuleViolations > 0 && (
                                    <TabLink onClick={() => onSelectTab?.("policy-violations")}>
                                        {unresolvedRuleViolations} unresolved policy violation
                                        {s(unresolvedRuleViolations)}
                                    </TabLink>
                                )}
                                {failureScope} must be resolved for this run to pass.
                            </>
                        )}
                    </p>
                </div>
                {/* At-a-glance tallies, kept out of the way on narrow screens. */}
                <dl className="flex shrink-0 gap-5 text-sm sm:pl-4">
                    <div className="flex flex-col items-end">
                        <dt className="order-2 text-[11px] text-muted-foreground uppercase tracking-wide">
                            Unresolved
                        </dt>
                        <dd
                            className={cn(
                                "order-1 font-semibold text-lg tabular-nums",
                                severeFindings > 0 ? "text-destructive" : "text-foreground",
                            )}
                        >
                            {severeFindings}
                        </dd>
                    </div>
                    <div className="flex flex-col items-end">
                        <dt className="order-2 text-[11px] text-muted-foreground uppercase tracking-wide">Reported</dt>
                        <dd className="order-1 font-semibold text-lg tabular-nums">{reportedFindings}</dd>
                    </div>
                    <div className="flex flex-col items-end">
                        <dt className="order-2 text-[11px] text-muted-foreground uppercase tracking-wide">Resolved</dt>
                        <dd className="order-1 font-semibold text-lg tabular-nums">{resolvedFindings}</dd>
                    </div>
                </dl>
            </div>

            {/* The findings list beside the "About this run" sidebar. */}
            <div className="grid gap-4 lg:grid-cols-[minmax(0,7fr)_minmax(0,3fr)]">
                <Card className="h-full gap-0 overflow-hidden py-0 shadow-none">
                    <DetectorRow
                        description="Reported by the package managers, the scanner, or ORT"
                        gate
                        icon={Bug}
                        main={unresolvedIssues}
                        name="Technical issues"
                        onClick={() => onSelectTab?.("tech-issues")}
                        severities={issueSeverities}
                        subLabel={`${resolvedIssues} resolved`}
                        total={issues.length}
                    />
                    <DetectorRow
                        description="Raised by the configured policy rules"
                        gate
                        icon={Scale}
                        main={unresolvedRuleViolations}
                        name="Policy violations"
                        onClick={() => onSelectTab?.("policy-violations")}
                        severities={violationSeverities}
                        subLabel={`${resolvedRuleViolations} resolved`}
                        total={ruleViolations.length}
                    />
                    <DetectorRow
                        description="From the configured security advisory providers"
                        icon={ShieldAlert}
                        main={openVulnerabilities}
                        name="Vulnerabilities"
                        onClick={() => onSelectTab?.("vulnerabilities")}
                        severities={vulnerabilitySeverities}
                        subLabel={`${resolvedVulnerabilities} resolved`}
                        total={vulnerabilities.length}
                    />
                    <DetectorRow
                        description={
                            <>
                                <span title={LICENSE_TERM_DEFINITIONS.declared}>
                                    {declaredLicensesProcessed.length} declared
                                </span>
                                {" · "}
                                <span title={LICENSE_TERM_DEFINITIONS.detected}>
                                    {detectedLicensesProcessed.length} detected
                                </span>
                            </>
                        }
                        icon={FileText}
                        main={effectiveLicenses.length}
                        name="Licenses"
                        onClick={() => onSelectTab?.("licenses")}
                        subLabel={<span title={LICENSE_TERM_DEFINITIONS.effective}>effective</span>}
                    />
                </Card>

                <Card className="h-fit gap-0 p-4 shadow-none">
                    <section>
                        <SidebarHeading>Repository</SidebarHeading>
                        {vcsProcessed?.url ? (
                            <dl className="grid grid-cols-[max-content_minmax(0,1fr)] gap-x-3 gap-y-1.5 text-sm">
                                <dt className="text-muted-foreground">URL</dt>
                                <dd className="min-w-0">
                                    <Url href={vcsProcessed.url} truncate />
                                </dd>
                                <dt className="text-muted-foreground">Revision</dt>
                                <dd
                                    className="min-w-0 truncate font-mono text-xs"
                                    title={vcsProcessed.revision || "unknown"}
                                >
                                    {vcsProcessed.revision || "unknown"}
                                </dd>
                                {vcsProcessed.type ? (
                                    <>
                                        <dt className="text-muted-foreground">Type</dt>
                                        <dd>{vcsProcessed.type}</dd>
                                    </>
                                ) : null}
                            </dl>
                        ) : (
                            <p className="text-muted-foreground text-sm">No repository information available.</p>
                        )}
                    </section>

                    <section className="mt-4 border-t pt-4">
                        <SidebarHeading>Composition</SidebarHeading>
                        <SidebarStat
                            hint="Software projects ORT detected within the code repository, each declaring its own dependencies."
                            icon={Folder}
                            label={plural(projects.length, "Project")}
                            onClick={() => onFilterByLevel?.(["project"])}
                            value={projects.length}
                        />
                        <SidebarStat
                            hint="Every distinct package (dependency) resolved across all projects in this run."
                            icon={Package}
                            label={plural(packages.length, "Package")}
                            onClick={() => onFilterByLevel?.(null)}
                            value={packages.length}
                        />
                        <SidebarStat
                            hint="Dependency scopes declared by the projects (e.g. compile or test) that group how each dependency is used."
                            icon={Layers}
                            label={plural(scopes.length, "Scope")}
                            onClick={() => onFilterByLevel?.(null)}
                            value={scopes.length}
                        />
                        <SidebarStat
                            hint="Direct dependencies are declared straight by a project; transitive ones are pulled in indirectly through other dependencies."
                            icon={Network}
                            label="Direct / transitive"
                            onClick={() => onFilterByLevel?.(["direct", "transitive"])}
                            value={`${directDependencies} / ${transitiveDependencies}`}
                        />
                    </section>

                    {webAppEvaluatedModel.hasRepositoryConfiguration() ||
                    resolvedConfig.packageCurations > 0 ||
                    resolvedConfig.packageConfigurations > 0 ||
                    resolvedConfig.licenseChoices > 0 ||
                    resolvedConfig.resolutions > 0 ? (
                        <section className="mt-4 border-t pt-4">
                            <SidebarHeading>Applied configuration</SidebarHeading>
                            <div className="flex flex-wrap gap-1.5">
                                {webAppEvaluatedModel.hasRepositoryConfiguration() ? (
                                    <ConfigTag
                                        count={1}
                                        label=".ort.yml"
                                        onClick={() => onSelectRunDetailsTab?.("ort-yml")}
                                        title="The .ort.yml at the repository root tailors how ORT analyzes this project: path and scope excludes, license choices, license-finding curations, and resolutions."
                                    />
                                ) : null}
                                {resolvedConfig.packageCurations > 0 ? (
                                    <ConfigTag
                                        count={resolvedConfig.packageCurations}
                                        label={plural(resolvedConfig.packageCurations, "package curation")}
                                        onClick={() => onSelectRunDetailsTab?.("package-curations")}
                                        title="Package curations correct invalid or missing package metadata and set the concluded license for packages."
                                    />
                                ) : null}
                                {resolvedConfig.packageConfigurations > 0 ? (
                                    <ConfigTag
                                        count={resolvedConfig.packageConfigurations}
                                        label={plural(resolvedConfig.packageConfigurations, "package configuration")}
                                        onClick={() => onSelectRunDetailsTab?.("package-configurations")}
                                        title="Package configurations mark file paths in dependencies as excluded and correct scanner license findings."
                                    />
                                ) : null}
                                {resolvedConfig.licenseChoices > 0 ? (
                                    <ConfigTag
                                        count={resolvedConfig.licenseChoices}
                                        label={plural(resolvedConfig.licenseChoices, "license choice")}
                                        onClick={() => onSelectRunDetailsTab?.("ort-yml")}
                                        title="License choices resolve a dependency's disjunctive (OR) license expression to a single selected license."
                                    />
                                ) : null}
                                {resolvedConfig.resolutions > 0 ? (
                                    <ConfigTag
                                        count={resolvedConfig.resolutions}
                                        label={plural(resolvedConfig.resolutions, "resolution")}
                                        onClick={() => onSelectRunDetailsTab?.("resolutions")}
                                        title="Resolutions mark technical issues, policy violations, or vulnerabilities that cannot be fixed as resolved, each with a reason."
                                    />
                                ) : null}
                            </div>
                        </section>
                    ) : null}
                </Card>
            </div>
        </div>
    );
}

export { ResultsSummary };
export default ResultsSummary;
