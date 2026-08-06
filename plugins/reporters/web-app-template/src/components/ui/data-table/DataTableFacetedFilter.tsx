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
import { Check, FilterX, ListFilter } from "lucide-react";
import type { ComponentType } from "react";
import { useMemo } from "react";

import { Button } from "@/components/ui/Button";
import { Command, CommandEmpty, CommandGroup, CommandInput, CommandItem, CommandList } from "@/components/ui/Command";
import { Popover, PopoverContent, PopoverTrigger } from "@/components/ui/Popover";
import { cn } from "@/lib/utils";

interface FacetedFilterOption {
    icon?: ComponentType<{ className?: string }>;
    label: string;
    // Render this option's label greyed to signal it is de-emphasized (e.g. an excluded scope). Unlike an
    // inactive value (see activeValues) the label is greyed but not struck through.
    muted?: boolean;
    value: string;
}

interface DataTableFacetedFilterProps<TData, TValue> {
    column: Column<TData, TValue>;
    title: string;
    options?: FacetedFilterOption[];
    // When provided, any option whose value is not in this set is shown muted (struck through and grey)
    // to signal it is no longer applicable, e.g. a license that is not part of the effective license.
    activeValues?: ReadonlySet<string>;
}

export function DataTableFacetedFilter<TData, TValue>({
    column,
    title,
    options,
    activeValues,
}: DataTableFacetedFilterProps<TData, TValue>) {
    const facets = column.getFacetedUniqueValues();

    const resolvedOptions = useMemo<FacetedFilterOption[]>(() => {
        if (options && options.length > 0) {
            return options;
        }
        const derived: FacetedFilterOption[] = [];
        for (const key of facets.keys()) {
            // Skip empty/blank facet values (e.g. rows with no license) so the dropdown has no blank entry.
            if (key === undefined || key === null || String(key).trim() === "") continue;
            const value = String(key);
            derived.push({ label: value, value });
        }
        derived.sort((a, b) => a.label.localeCompare(b.label));
        return derived;
    }, [options, facets]);

    const filterValue = column.getFilterValue() as string[] | undefined;
    const selectedValues = useMemo(() => new Set(filterValue ?? []), [filterValue]);

    const toggleValue = (value: string) => {
        const next = new Set(selectedValues);
        if (next.has(value)) {
            next.delete(value);
        } else {
            next.add(value);
        }
        const arr = Array.from(next);
        column.setFilterValue(arr.length === 0 ? undefined : arr);
    };

    const clearFilter = () => {
        column.setFilterValue(undefined);
    };

    const hasFilter = selectedValues.size > 0;
    // "Filter by …" reads as a sentence, so lower-case the column title that follows the leading words.
    const titleLowerCase = title.toLowerCase();

    return (
        <Popover>
            <PopoverTrigger asChild>
                <Button
                    aria-label={
                        hasFilter
                            ? `Filter by ${titleLowerCase} (${selectedValues.size} selected)`
                            : `Filter by ${titleLowerCase}`
                    }
                    className={cn(
                        "h-7 gap-1 px-1.5 text-muted-foreground data-[state=open]:bg-accent",
                        hasFilter && "text-primary",
                    )}
                    size="sm"
                    title={`Filter by ${titleLowerCase}`}
                    variant="ghost"
                >
                    <ListFilter aria-hidden="true" className="size-3.5" />
                    {hasFilter && <span className="font-mono text-xs leading-none">{selectedValues.size}</span>}
                </Button>
            </PopoverTrigger>
            <PopoverContent align="start" className="w-[220px] p-0">
                <Command>
                    {/* Keep a clear control in the always-visible search row so a single column's filter can
                        be reset without scrolling past the options or reaching for the toolbar's global Reset. */}
                    <div className="relative">
                        <CommandInput className={cn(hasFilter && "pr-9")} placeholder={title} />
                        {hasFilter ? (
                            <Button
                                aria-label="Clear selection"
                                className="absolute top-1.5 right-1.5 size-6"
                                onClick={clearFilter}
                                size="icon"
                                title="Clear selection"
                                type="button"
                                variant="ghost"
                            >
                                <FilterX aria-hidden="true" className="size-3.5" />
                            </Button>
                        ) : null}
                    </div>
                    <CommandList>
                        <CommandEmpty>No results found.</CommandEmpty>
                        <CommandGroup>
                            {resolvedOptions.map((option) => {
                                const isSelected = selectedValues.has(option.value);
                                const isInactive = activeValues !== undefined && !activeValues.has(option.value);
                                return (
                                    <CommandItem key={option.value} onSelect={() => toggleValue(option.value)}>
                                        <div
                                            className={cn(
                                                "mr-2 flex size-4 items-center justify-center rounded-sm border border-primary",
                                                isSelected ? "bg-background text-foreground" : "[&_svg]:invisible",
                                            )}
                                        >
                                            <Check aria-hidden="true" className="size-3 text-foreground" />
                                        </div>
                                        {option.icon && <option.icon className="mr-2 size-4 text-muted-foreground" />}
                                        <span
                                            className={cn(
                                                (isInactive || option.muted) && "text-muted-foreground",
                                                isInactive && "line-through",
                                            )}
                                        >
                                            {option.label}
                                        </span>
                                        <span className="ml-auto flex size-4 items-center justify-center font-mono text-xs">
                                            {facets.get(option.value) ?? 0}
                                        </span>
                                    </CommandItem>
                                );
                            })}
                        </CommandGroup>
                    </CommandList>
                </Command>
            </PopoverContent>
        </Popover>
    );
}
