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

import {
    Bug,
    FileText,
    Info,
    LifeBuoy,
    type LucideIcon,
    MonitorCheck,
    Network,
    PanelLeftClose,
    PanelLeftOpen,
    Scale,
    Settings,
    ShieldAlert,
    Table as TableIcon,
} from "lucide-react";
import type { JSX } from "react";
import { useCallback, useEffect, useState } from "react";

import { About } from "@/components/About";
import { IssuesTable } from "@/components/IssuesTable";
import { OrtLogo, OrtLogoMark } from "@/components/OrtLogo";
import { ResultsLicenses } from "@/components/ResultsLicenses";
import { ResultsSummary } from "@/components/ResultsSummary";
import { ResultsTable } from "@/components/ResultsTable";
import { ResultsTree } from "@/components/ResultsTree";
import { RuleViolationsTable } from "@/components/RuleViolationsTable";
import { RunDetails } from "@/components/RunDetails";
import { useSettings } from "@/components/SettingsProvider";
import { SummaryIcon } from "@/components/Shared";
import { VulnerabilitiesTable } from "@/components/VulnerabilitiesTable";
import { cn } from "@/lib/utils";
import type WebAppEvaluatedModel from "@/models/WebAppEvaluatedModel";
import { SettingsPage } from "@/pages/SettingsPage";

export interface AppPageProps {
    webAppEvaluatedModel: WebAppEvaluatedModel;
}

type TabId =
    | "summary"
    | "tree"
    | "table"
    | "tech-issues"
    | "policy-violations"
    | "vulnerabilities"
    | "licenses"
    | "run-details"
    | "settings"
    | "about";

const TAB_IDS: readonly TabId[] = [
    "summary",
    "tree",
    "table",
    "tech-issues",
    "policy-violations",
    "vulnerabilities",
    "licenses",
    "run-details",
    "settings",
    "about",
];

const DEFAULT_TAB: TabId = "summary";

const HELP_URL = "https://oss-review-toolkit.org/ort/";

const LICENSE_COLUMN: Record<"effective" | "declared" | "detected", string> = {
    declared: "declaredLicenses",
    detected: "detectedLicenses",
    effective: "effectiveLicense",
};

// Severity rank (ERROR is the most severe, then WARNING, then HINT).
const SEVERITY_RANK: Record<string, number> = { ERROR: 3, WARNING: 2, HINT: 1 };

// The severities at or above a finding type's severe threshold — the open findings that must be resolved
// for the run to pass. Used to pre-filter the Technical Issues / Policy Violations tables so they open on
// exactly what needs fixing. An absent or unknown threshold includes every severity.
function severitiesAtOrAboveThreshold(threshold: string | undefined): string[] {
    const cutoff = threshold ? (SEVERITY_RANK[threshold.toUpperCase()] ?? 1) : 1;
    return ["ERROR", "WARNING", "HINT"].filter((severity) => (SEVERITY_RANK[severity] ?? 0) >= cutoff);
}

function readHashTab(): TabId {
    if (typeof window === "undefined") {
        return DEFAULT_TAB;
    }
    const hash = window.location.hash.replace(/^#/, "");
    return (TAB_IDS as readonly string[]).includes(hash) ? (hash as TabId) : DEFAULT_TAB;
}

// The deep-linked targets from the query string: a package (+ its inner detail tab) for the
// Table/Tree/Technical Issues/Policy Violations views, e.g. ?pkg-id=...&pkg-tab=..., and a vulnerability (its advisory
// id, scoped by the affected package) for the Vulnerabilities tab, e.g. ?vul-id=...&pkg-id=...
function getActiveFocusFromUrl(): {
    packageId: string | null;
    packageTab: string | null;
    vulnerabilityId: string | null;
} {
    if (typeof window === "undefined") {
        return { packageId: null, packageTab: null, vulnerabilityId: null };
    }
    const params = new URLSearchParams(window.location.search);
    return {
        packageId: params.get("pkg-id"),
        packageTab: params.get("pkg-tab"),
        vulnerabilityId: params.get("vul-id"),
    };
}

function AppPage({ webAppEvaluatedModel }: AppPageProps): JSX.Element {
    const { settings } = useSettings();
    const [activeTab, setActiveTab] = useState<TabId>(() => readHashTab());
    const [focusPackageId, setFocusPackageId] = useState<string | null>(() => getActiveFocusFromUrl().packageId);
    const [focusPackageTab, setFocusPackageTab] = useState<string | null>(() => getActiveFocusFromUrl().packageTab);
    const [focusVulnerabilityId, setFocusVulnerabilityId] = useState<string | null>(
        () => getActiveFocusFromUrl().vulnerabilityId,
    );
    const [focusLicense, setFocusLicense] = useState<{ columnId: string; license: string } | null>(null);
    const [focusLevel, setFocusLevel] = useState<string[] | null>(null);
    // Severity values to pre-filter the Technical Issues / Policy Violations tables on (set when navigating to them).
    const [focusSeverity, setFocusSeverity] = useState<string[] | null>(null);
    // Which Run Details inner tab to open when navigating there from a Summary stat card (null = default).
    const [focusRunDetailsTab, setFocusRunDetailsTab] = useState<string | null>(null);
    const [collapsed, setCollapsed] = useState<boolean>(() => {
        if (typeof window === "undefined") {
            return false;
        }
        return window.localStorage.getItem("ort-sidebar-collapsed") === "true";
    });

    useEffect(() => {
        window.localStorage.setItem("ort-sidebar-collapsed", String(collapsed));
    }, [collapsed]);

    useEffect(() => {
        const onHashChange = (): void => {
            setActiveTab(readHashTab());
        };
        window.addEventListener("hashchange", onHashChange);
        return () => {
            window.removeEventListener("hashchange", onHashChange);
        };
    }, []);

    // Keep the URL in sync with the active tab (hash) and the deep-linked package + inner tab (query),
    // so reloading or sharing the link restores the same view.
    useEffect(() => {
        if (typeof window === "undefined") {
            return;
        }
        const url = new URL(window.location.href);
        url.hash = activeTab;
        // The focus query params are only written when deep linking is enabled in Settings; otherwise they
        // are stripped so the URL stays clean. The tab hash is always kept for basic navigation.
        if (settings.deepLinking && focusPackageId) {
            url.searchParams.set("pkg-id", focusPackageId);
        } else {
            url.searchParams.delete("pkg-id");
        }
        if (settings.deepLinking && focusPackageId && focusPackageTab) {
            url.searchParams.set("pkg-tab", focusPackageTab);
        } else {
            url.searchParams.delete("pkg-tab");
        }
        if (settings.deepLinking && focusVulnerabilityId) {
            url.searchParams.set("vul-id", focusVulnerabilityId);
        } else {
            url.searchParams.delete("vul-id");
        }
        const next = `${url.pathname}${url.search}${url.hash}`;
        const current = `${window.location.pathname}${window.location.search}${window.location.hash}`;
        if (next !== current) {
            try {
                window.history.replaceState(null, "", next);
            } catch {
                // Some environments (e.g. file://) block history updates; deep links then only work in-session.
            }
        }
    }, [activeTab, focusPackageId, focusPackageTab, focusVulnerabilityId, settings.deepLinking]);

    const handleTabChange = useCallback(
        (value: string): void => {
            if (!(TAB_IDS as readonly string[]).includes(value)) {
                return;
            }
            // Navigation clears any package / vulnerability / license focus. Opening the Technical Issues or
            // Policy Violations tab pre-filters it to the unresolved findings at or above that type's severe
            // threshold — i.e. what must be fixed for the run to pass (the filter shows in the toolbar and can
            // be cleared).
            setFocusPackageId(null);
            setFocusPackageTab(null);
            setFocusVulnerabilityId(null);
            setFocusLicense(null);
            setFocusLevel(null);
            setFocusSeverity(
                value === "tech-issues"
                    ? severitiesAtOrAboveThreshold(webAppEvaluatedModel.severeIssueThreshold)
                    : value === "policy-violations"
                      ? severitiesAtOrAboveThreshold(webAppEvaluatedModel.severeRuleViolationThreshold)
                      : null,
            );
            setFocusRunDetailsTab(null);
            setActiveTab(value as TabId);
        },
        [webAppEvaluatedModel],
    );

    // The Vulnerabilities view reports the expanded vulnerability (its advisory id) together with the
    // package it affects, so the URL can deep-link the exact row via ?vul-id=...&pkg-id=...
    const handleFocusVulnerabilityChange = useCallback(
        (vulnerabilityId: string | null, packageId: string | null): void => {
            setFocusVulnerabilityId(vulnerabilityId);
            setFocusPackageId(packageId);
        },
        [],
    );

    // Jump to the Table tab, search for the given package id and expand its row.
    const focusPackage = useCallback((packageId: string): void => {
        setFocusLicense(null);
        setFocusLevel(null);
        setFocusPackageTab(null);
        setFocusPackageId(packageId);
        setActiveTab("table");
    }, []);

    // Jump to the Table tab and filter the given license column on the given license.
    const focusLicenseInTable = useCallback((license: string, type: "effective" | "declared" | "detected"): void => {
        setFocusPackageId(null);
        setFocusPackageTab(null);
        setFocusLevel(null);
        setFocusLicense({ columnId: LICENSE_COLUMN[type], license });
        setActiveTab("table");
    }, []);

    // Jump to the Table tab, optionally pre-filtered on the given dependency kinds (null = unfiltered).
    const focusTableWithLevel = useCallback((level: string[] | null): void => {
        setFocusPackageId(null);
        setFocusPackageTab(null);
        setFocusLicense(null);
        setFocusLevel(level);
        setActiveTab("table");
    }, []);

    // Jump to the Run Details tab and open one of its inner tabs (e.g. "package-curations").
    const focusRunDetails = useCallback((tab: string): void => {
        setFocusPackageId(null);
        setFocusPackageTab(null);
        setFocusVulnerabilityId(null);
        setFocusLicense(null);
        setFocusLevel(null);
        setFocusSeverity(null);
        setFocusRunDetailsTab(tab);
        setActiveTab("run-details");
    }, []);

    // A view (Table/Tree) reports which package is expanded/selected so the URL can deep-link it;
    // changing the package drops any stale inner-tab selection.
    const handleFocusPackageChange = useCallback(
        (packageId: string | null): void => {
            setFocusPackageId(packageId);
            if (packageId !== focusPackageId) {
                setFocusPackageTab(null);
            }
        },
        [focusPackageId],
    );

    const handleFocusPackageTabChange = useCallback((tab: string | null): void => {
        setFocusPackageTab(tab);
    }, []);

    const toggleSidebar = useCallback(() => {
        setCollapsed((prev) => !prev);
    }, []);

    // Sidebar badges show only open (unresolved) counts. The technical issue and policy violation badges
    // count only the open items at or above their configured severe thresholds; vulnerabilities have no
    // such threshold, so their badge stays the full open count.
    const openIssuesCount = webAppEvaluatedModel.severeOpenIssuesCount;
    const openRuleViolationsCount = webAppEvaluatedModel.severeOpenRuleViolationsCount;
    const openVulnerabilitiesCount = webAppEvaluatedModel.statistics.openVulnerabilities;

    // Icon colors are the ORT logo palette, matching the Summary stat-card icons.
    const nav: { id: TabId; label: string; icon: LucideIcon; color: string; count?: number }[] = [
        { id: "summary", label: "Summary", icon: SummaryIcon, color: "text-foreground" },
        { id: "table", label: "Table", icon: TableIcon, color: "text-[#0E8079]" },
        { id: "tree", label: "Tree", icon: Network, color: "text-[#3A8DCC]" },
        { id: "tech-issues", label: "Technical Issues", icon: Bug, color: "text-[#C41C33]", count: openIssuesCount },
        {
            id: "policy-violations",
            label: "Policy Violations",
            icon: Scale,
            color: "text-[#EC610E]",
            count: openRuleViolationsCount,
        },
        {
            id: "vulnerabilities",
            label: "Vulnerabilities",
            icon: ShieldAlert,
            color: "text-[#C41C33]",
            count: openVulnerabilitiesCount,
        },
        {
            id: "licenses",
            label: "Licenses",
            icon: FileText,
            color: "text-[#673A93]",
            count: webAppEvaluatedModel.effectiveLicenses.length,
        },
        { id: "run-details", label: "Run Details", icon: MonitorCheck, color: "text-[#C88A00]" },
    ];

    const activeItem = nav.find((item) => item.id === activeTab);
    // Settings and About are not in the nav list; give them a header label and icon here.
    const activeLabel = activeItem?.label ?? (activeTab === "settings" ? "Settings" : "About");
    const HeaderIcon = activeItem?.icon ?? (activeTab === "settings" ? Settings : Info);
    const headerIconColor = activeItem?.color;

    const menuItemBase =
        "flex w-full items-center gap-2 rounded-md px-2 py-1.5 text-sm outline-none transition-colors focus-visible:ring-2 focus-visible:ring-sidebar-ring";
    const menuItemInactive = "text-sidebar-foreground/80 hover:bg-sidebar-accent/60 hover:text-sidebar-foreground";

    return (
        <div className="flex h-dvh bg-sidebar text-foreground">
            <aside
                className={cn(
                    "flex h-full shrink-0 flex-col text-sidebar-foreground transition-[width] duration-200",
                    collapsed ? "w-16" : "w-60",
                )}
            >
                <div
                    className={cn(
                        "flex h-16 shrink-0 items-center border-sidebar-border border-b",
                        collapsed ? "justify-center px-2" : "px-4",
                    )}
                >
                    <a
                        aria-label="OSS Review Toolkit website"
                        className="rounded focus-visible:outline-none focus-visible:ring-2 focus-visible:ring-sidebar-ring"
                        href="https://oss-review-toolkit.org"
                        rel="noopener noreferrer"
                        target="_blank"
                    >
                        {collapsed ? <OrtLogoMark className="size-7" /> : <OrtLogo className="h-7 w-auto" />}
                    </a>
                </div>
                <nav aria-label="Report sections" className="flex-1 overflow-y-auto p-2">
                    <ul className="flex flex-col gap-1">
                        {nav.map((item) => {
                            const Icon = item.icon;
                            const active = item.id === activeTab;
                            return (
                                <li key={item.id}>
                                    <button
                                        aria-current={active ? "page" : undefined}
                                        className={cn(
                                            menuItemBase,
                                            collapsed && "justify-center",
                                            active
                                                ? "bg-sidebar-accent font-medium text-sidebar-accent-foreground"
                                                : menuItemInactive,
                                        )}
                                        onClick={() => handleTabChange(item.id)}
                                        title={collapsed ? item.label : undefined}
                                        type="button"
                                    >
                                        <Icon aria-hidden="true" className={cn("size-4 shrink-0", item.color)} />
                                        {collapsed ? null : (
                                            <>
                                                <span className="flex-1 truncate text-left">{item.label}</span>
                                                {item.count !== undefined ? (
                                                    <span className="ml-auto text-muted-foreground text-xs tabular-nums">
                                                        {item.count}
                                                    </span>
                                                ) : null}
                                            </>
                                        )}
                                    </button>
                                </li>
                            );
                        })}
                    </ul>
                </nav>
                <div className="flex shrink-0 flex-col gap-1 border-sidebar-border border-t p-2">
                    <button
                        aria-label={collapsed ? "Expand sidebar" : "Collapse sidebar"}
                        className={cn(menuItemBase, collapsed && "justify-center", menuItemInactive)}
                        onClick={toggleSidebar}
                        title={collapsed ? "Expand" : undefined}
                        type="button"
                    >
                        {collapsed ? (
                            <PanelLeftOpen aria-hidden="true" className="size-4 shrink-0" />
                        ) : (
                            <PanelLeftClose aria-hidden="true" className="size-4 shrink-0" />
                        )}
                        {collapsed ? null : <span>Collapse</span>}
                    </button>
                    <button
                        aria-current={activeTab === "settings" ? "page" : undefined}
                        className={cn(
                            menuItemBase,
                            collapsed && "justify-center",
                            activeTab === "settings"
                                ? "bg-sidebar-accent font-medium text-sidebar-accent-foreground"
                                : menuItemInactive,
                        )}
                        onClick={() => handleTabChange("settings")}
                        title={collapsed ? "Settings" : undefined}
                        type="button"
                    >
                        <Settings aria-hidden="true" className="size-4 shrink-0" />
                        {collapsed ? null : <span>Settings</span>}
                    </button>
                    <a
                        className={cn(menuItemBase, collapsed && "justify-center", menuItemInactive)}
                        href={HELP_URL}
                        rel="noopener noreferrer"
                        target="_blank"
                        title={collapsed ? "Get Help" : undefined}
                    >
                        <LifeBuoy aria-hidden="true" className="size-4 shrink-0" />
                        {collapsed ? null : <span>Get Help</span>}
                    </a>
                    <button
                        aria-current={activeTab === "about" ? "page" : undefined}
                        className={cn(
                            menuItemBase,
                            collapsed && "justify-center",
                            activeTab === "about"
                                ? "bg-sidebar-accent font-medium text-sidebar-accent-foreground"
                                : menuItemInactive,
                        )}
                        onClick={() => handleTabChange("about")}
                        title={collapsed ? "About" : undefined}
                        type="button"
                    >
                        <Info aria-hidden="true" className="size-4 shrink-0" />
                        {collapsed ? null : <span>About</span>}
                    </button>
                </div>
            </aside>

            <div className="m-2 flex min-w-0 flex-1 flex-col overflow-hidden rounded-xl border bg-background shadow-sm">
                <header className="flex h-14 shrink-0 items-center justify-between gap-2 border-b px-4 sm:px-6">
                    <div className="flex min-w-0 items-center gap-2">
                        <HeaderIcon aria-hidden="true" className={cn("size-5 shrink-0", headerIconColor)} />
                        <h1 className="truncate font-semibold text-base tracking-tight sm:text-lg">{activeLabel}</h1>
                    </div>
                </header>

                <main className="flex-1 overflow-auto">
                    <div className="flex min-h-full w-full flex-col p-4 sm:p-6">
                        {activeTab === "summary" ? (
                            <ResultsSummary
                                onFilterByLevel={focusTableWithLevel}
                                onSelectRunDetailsTab={focusRunDetails}
                                onSelectTab={handleTabChange}
                                webAppEvaluatedModel={webAppEvaluatedModel}
                            />
                        ) : null}
                        {activeTab === "table" ? (
                            <ResultsTable
                                webAppEvaluatedModel={webAppEvaluatedModel}
                                {...(focusPackageId ? { focusPackageId } : {})}
                                {...(focusLicense ? { focusLicense } : {})}
                                {...(focusLevel ? { focusLevel } : {})}
                                {...(focusPackageTab ? { focusPackageTab } : {})}
                                onFocusPackageChange={handleFocusPackageChange}
                                onFocusPackageTabChange={handleFocusPackageTabChange}
                            />
                        ) : null}
                        {activeTab === "tree" ? (
                            <ResultsTree
                                className="min-h-0 flex-1"
                                webAppEvaluatedModel={webAppEvaluatedModel}
                                {...(focusPackageId ? { focusPackageId } : {})}
                                {...(focusPackageTab ? { focusPackageTab } : {})}
                                onFocusPackageChange={handleFocusPackageChange}
                                onFocusPackageTabChange={handleFocusPackageTabChange}
                            />
                        ) : null}
                        {activeTab === "tech-issues" ? (
                            <IssuesTable
                                issues={webAppEvaluatedModel.issues}
                                onPackageClick={focusPackage}
                                {...(focusPackageId ? { focusPackageId } : {})}
                                {...(focusSeverity ? { focusSeverity } : {})}
                                onFocusPackageChange={handleFocusPackageChange}
                            />
                        ) : null}
                        {activeTab === "policy-violations" ? (
                            <RuleViolationsTable
                                onPackageClick={focusPackage}
                                ruleViolations={webAppEvaluatedModel.ruleViolations}
                                {...(focusPackageId ? { focusPackageId } : {})}
                                {...(focusSeverity ? { focusSeverity } : {})}
                                onFocusPackageChange={handleFocusPackageChange}
                            />
                        ) : null}
                        {activeTab === "vulnerabilities" ? (
                            <VulnerabilitiesTable
                                vulnerabilities={webAppEvaluatedModel.vulnerabilities}
                                {...(focusVulnerabilityId ? { focusVulnerabilityId } : {})}
                                {...(focusPackageId ? { focusPackageId } : {})}
                                onFocusVulnerabilityChange={handleFocusVulnerabilityChange}
                            />
                        ) : null}
                        {activeTab === "licenses" ? (
                            <ResultsLicenses
                                onLicenseClick={focusLicenseInTable}
                                webAppEvaluatedModel={webAppEvaluatedModel}
                            />
                        ) : null}
                        {activeTab === "run-details" ? (
                            <RunDetails
                                webAppEvaluatedModel={webAppEvaluatedModel}
                                {...(focusRunDetailsTab ? { focusTab: focusRunDetailsTab } : {})}
                            />
                        ) : null}
                        {activeTab === "settings" ? <SettingsPage webAppEvaluatedModel={webAppEvaluatedModel} /> : null}
                        {activeTab === "about" ? <About /> : null}
                    </div>
                </main>
            </div>
        </div>
    );
}

export { AppPage };
export default AppPage;
