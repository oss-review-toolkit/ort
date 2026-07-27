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

import { ChevronLeft, ChevronRight } from "lucide-react";
import { Fragment, type JSX, type ReactNode, useState } from "react";

import { Button } from "@/components/ui/button";

export interface PaginatedProps<T> {
    // Classes for the container that lays out the current page's items (e.g. a grid).
    containerClassName?: string;
    getKey: (item: T, index: number) => string;
    // Optional noun for the summary text, e.g. "paths" → "1–4 of 12 paths".
    itemLabel?: string;
    items: readonly T[];
    pageSize: number;
    renderItem: (item: T, index: number) => ReactNode;
}

// Renders a list/grid of items one page at a time, with prev/next controls shown only when the items
// do not all fit on a single page.
export function Paginated<T>({
    containerClassName,
    getKey,
    itemLabel,
    items,
    pageSize,
    renderItem,
}: PaginatedProps<T>): JSX.Element {
    const [page, setPage] = useState(0);
    const pageCount = Math.max(1, Math.ceil(items.length / pageSize));
    const current = Math.min(page, pageCount - 1);
    const start = current * pageSize;
    const pageItems = items.slice(start, start + pageSize);

    return (
        <div className="space-y-3">
            <div className={containerClassName}>
                {pageItems.map((item, i) => (
                    <Fragment key={getKey(item, start + i)}>{renderItem(item, start + i)}</Fragment>
                ))}
            </div>
            {pageCount > 1 ? (
                <div className="flex flex-wrap items-center justify-between gap-2 text-muted-foreground text-sm">
                    <span>
                        {start + 1}–{start + pageItems.length} of {items.length}
                        {itemLabel ? ` ${itemLabel}` : ""}
                    </span>
                    <div className="flex items-center gap-2">
                        <Button
                            aria-label="Previous page"
                            disabled={current === 0}
                            onClick={() => setPage(current - 1)}
                            size="icon-sm"
                            type="button"
                            variant="outline"
                        >
                            <ChevronLeft aria-hidden="true" className="size-4" />
                        </Button>
                        <span>
                            Page {current + 1} of {pageCount}
                        </span>
                        <Button
                            aria-label="Next page"
                            disabled={current >= pageCount - 1}
                            onClick={() => setPage(current + 1)}
                            size="icon-sm"
                            type="button"
                            variant="outline"
                        >
                            <ChevronRight aria-hidden="true" className="size-4" />
                        </Button>
                    </div>
                </div>
            ) : null}
        </div>
    );
}

export default Paginated;
