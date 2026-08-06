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

import type { Column } from "@tanstack/react-table";
import { Search } from "lucide-react";
import { useState } from "react";

import { Button } from "@/components/ui/Button";
import { Input } from "@/components/ui/Input";
import { Popover, PopoverContent, PopoverTrigger } from "@/components/ui/Popover";
import { cn } from "@/lib/utils";

interface DataTableColumnSearchProps<TData, TValue> {
    column: Column<TData, TValue>;
    title: string;
}

/**
 * A per-column free-text filter shown in a popover, so each searchable column can be filtered by its own
 * term independently (the terms combine, e.g. a path term and a copyright term at the same time). The
 * typed value is a draft: the column is only filtered when Search is pressed (or Enter), and Reset clears
 * it. The column must use a string filter such as `filterFn: "includesString"`.
 */
export function DataTableColumnSearch<TData, TValue>({ column, title }: DataTableColumnSearchProps<TData, TValue>) {
    const applied = (column.getFilterValue() as string | undefined) ?? "";
    const hasFilter = applied.length > 0;

    const [open, setOpen] = useState(false);
    const [draft, setDraft] = useState(applied);

    const handleOpenChange = (next: boolean): void => {
        // Re-sync the draft to the currently applied term each time the popover opens.
        if (next) {
            setDraft(applied);
        }
        setOpen(next);
    };

    const applySearch = (): void => {
        column.setFilterValue(draft.trim() || undefined);
        setOpen(false);
    };

    const resetSearch = (): void => {
        setDraft("");
        column.setFilterValue(undefined);
        setOpen(false);
    };

    return (
        <Popover onOpenChange={handleOpenChange} open={open}>
            <PopoverTrigger asChild>
                <Button
                    aria-label={hasFilter ? `Search ${title} (filtered by "${applied}")` : `Search ${title}`}
                    className={cn(
                        "h-7 gap-1 px-1.5 text-muted-foreground data-[state=open]:bg-accent",
                        hasFilter && "text-primary",
                    )}
                    size="sm"
                    title={`Search ${title}`}
                    variant="ghost"
                >
                    <Search aria-hidden="true" className="size-3.5" />
                </Button>
            </PopoverTrigger>
            <PopoverContent align="start" className="w-[240px] space-y-2 p-2">
                <Input
                    aria-label={`Search ${title}`}
                    className="h-8"
                    onChange={(event) => setDraft(event.target.value)}
                    onKeyDown={(event) => {
                        if (event.key === "Enter") {
                            event.preventDefault();
                            applySearch();
                        }
                    }}
                    placeholder={`Search ${title.toLowerCase()}...`}
                    value={draft}
                />
                <div className="flex justify-end gap-2">
                    <Button className="h-7" onClick={resetSearch} size="sm" type="button" variant="outline">
                        Reset
                    </Button>
                    <Button className="h-7" onClick={applySearch} size="sm" type="button">
                        Search
                    </Button>
                </div>
            </PopoverContent>
        </Popover>
    );
}
