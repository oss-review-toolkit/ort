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

import { type LucideIcon, Monitor, Moon, Sun } from "lucide-react";
import { useTheme } from "next-themes";
import type { JSX } from "react";
import { useEffect, useState } from "react";

import { RESULTS_TABLE_COLUMNS, type ResultsTableColumn } from "@/components/ResultsTable";
import { useSettings } from "@/components/SettingsProvider";
import { Card, CardContent, CardDescription, CardHeader, CardTitle } from "@/components/ui/Card";
import { Checkbox } from "@/components/ui/Checkbox";
import { Switch } from "@/components/ui/Switch";
import { cn } from "@/lib/utils";
import type WebAppEvaluatedModel from "@/models/WebAppEvaluatedModel";

export interface SettingsPageProps {
    webAppEvaluatedModel: WebAppEvaluatedModel;
}

const THEME_OPTIONS: { value: string; label: string; icon: LucideIcon }[] = [
    { value: "light", label: "Light", icon: Sun },
    { value: "dark", label: "Dark", icon: Moon },
    { value: "system", label: "System", icon: Monitor },
];

// A column is only offered when the report actually carries the data it needs; otherwise the column never
// appears in the table, so listing it here would be misleading.
function isColumnPresent(column: ResultsTableColumn, model: WebAppEvaluatedModel): boolean {
    switch (column.presence) {
        case "excludes":
            return model.hasExcludes();
        case "curations":
            return model.hasPackageCurations();
        case "configurations":
            return model.hasPackageConfigurations();
        default:
            return true;
    }
}

function SettingsPage({ webAppEvaluatedModel }: SettingsPageProps): JSX.Element {
    const { settings, updateSettings } = useSettings();
    const { setTheme, theme } = useTheme();

    // next-themes only knows the stored theme after mount; before that, assume the "light" default so the
    // control does not flash the wrong selection.
    const [mounted, setMounted] = useState(false);
    useEffect(() => {
        setMounted(true);
    }, []);
    const currentTheme = mounted ? (theme ?? "system") : "light";

    // Sort alphabetically by label to match the order of the table's "Customize Columns" menu.
    const presentColumns = RESULTS_TABLE_COLUMNS.filter((column) => isColumnPresent(column, webAppEvaluatedModel)).sort(
        (a, b) => a.label.localeCompare(b.label),
    );

    // The effective set of visible column ids: the user's choice, or the built-in defaults when untouched.
    const visibleIds = new Set(
        settings.defaultVisibleColumns ??
            RESULTS_TABLE_COLUMNS.filter((column) => column.defaultVisible).map((column) => column.id),
    );
    const isColumnVisible = (column: ResultsTableColumn): boolean =>
        column.alwaysVisible === true || visibleIds.has(column.id);

    const toggleColumn = (id: string, visible: boolean): void => {
        const next = new Set(visibleIds);
        // Keep always-visible columns in the stored set so they survive a customised configuration.
        for (const column of RESULTS_TABLE_COLUMNS) {
            if (column.alwaysVisible) {
                next.add(column.id);
            }
        }
        if (visible) {
            next.add(id);
        } else {
            next.delete(id);
        }
        updateSettings({ defaultVisibleColumns: [...next] });
    };

    return (
        <div className="mx-auto flex max-w-4xl flex-col gap-4">
            <Card>
                <CardHeader>
                    <CardTitle>Appearance</CardTitle>
                    <CardDescription>
                        Switch the interface between light and dark, or follow your operating system.
                    </CardDescription>
                </CardHeader>
                <CardContent>
                    <fieldset
                        aria-label="Theme"
                        className="m-0 inline-flex min-w-0 gap-1 rounded-lg border bg-muted p-1"
                    >
                        {THEME_OPTIONS.map(({ icon: Icon, label, value }) => {
                            const active = currentTheme === value;
                            return (
                                <button
                                    aria-pressed={active}
                                    className={cn(
                                        "inline-flex items-center gap-2 rounded-md px-3 py-1.5 text-sm transition-colors",
                                        active
                                            ? "bg-card font-medium text-foreground shadow-sm"
                                            : "text-muted-foreground hover:text-foreground",
                                    )}
                                    key={value}
                                    onClick={() => setTheme(value)}
                                    type="button"
                                >
                                    <Icon aria-hidden="true" className="size-4" />
                                    {label}
                                </button>
                            );
                        })}
                    </fieldset>
                </CardContent>
            </Card>

            <Card>
                <CardHeader>
                    <CardTitle>Deep linking</CardTitle>
                    <CardDescription>
                        Reflect the selected package or vulnerability in the browser URL (e.g. <code>?pkg-id=…</code>,{" "}
                        <code>?vul-id=…</code>) so a view can be bookmarked or shared. Turn off to keep the URL clean.
                    </CardDescription>
                </CardHeader>
                <CardContent>
                    <div className="flex items-center justify-between gap-4 text-sm">
                        <label className="cursor-pointer" htmlFor="deep-linking">
                            Update the URL as you browse
                        </label>
                        <Switch
                            checked={settings.deepLinking}
                            id="deep-linking"
                            onCheckedChange={(value) => updateSettings({ deepLinking: value })}
                        />
                    </div>
                </CardContent>
            </Card>

            <Card>
                <CardHeader>
                    <CardTitle>Default table columns</CardTitle>
                    <CardDescription>
                        Choose which columns are shown by default in the Table view. You can still show or hide any
                        column per session from the table's “Customize Columns” menu.
                    </CardDescription>
                </CardHeader>
                <CardContent>
                    <div className="grid grid-cols-1 gap-x-6 gap-y-1 sm:grid-cols-2 lg:grid-cols-3">
                        {presentColumns.map((column) => (
                            <div className="flex items-center gap-2.5 py-1.5 text-sm" key={column.id}>
                                <Checkbox
                                    checked={isColumnVisible(column)}
                                    disabled={column.alwaysVisible}
                                    id={`column-${column.id}`}
                                    onCheckedChange={(value) => toggleColumn(column.id, value === true)}
                                />
                                <label
                                    className={cn(
                                        "flex-1",
                                        column.alwaysVisible
                                            ? "cursor-default text-muted-foreground"
                                            : "cursor-pointer",
                                    )}
                                    htmlFor={`column-${column.id}`}
                                >
                                    {column.label}
                                </label>
                                {column.alwaysVisible ? (
                                    <span className="rounded border px-1.5 text-muted-foreground text-xs">always</span>
                                ) : null}
                            </div>
                        ))}
                    </div>
                </CardContent>
            </Card>

            <p className="text-center text-muted-foreground text-xs">
                Settings are saved in your browser and re-applied the next time you open a report.
            </p>
        </div>
    );
}

export { SettingsPage };
export default SettingsPage;
