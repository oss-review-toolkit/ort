/*
 * Copyright (C) 2025 The ORT Project Copyright Holders <https://github.com/oss-review-toolkit/ort/blob/main/NOTICE>
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

import { Badge } from "@/components/ui/badge";
import { cn } from "@/lib/utils";

export type SeverityValue = "ERROR" | "WARNING" | "HINT";

export interface SeverityTagProps {
    className?: string;
    isResolved?: boolean;
    severity: SeverityValue;
    tooltipText?: string;
}

const SEVERITY_CLASSES: Record<SeverityValue, string> = {
    ERROR: "bg-destructive text-white",
    HINT: "bg-yellow-300 text-yellow-900",
    WARNING: "bg-amber-500 text-amber-50",
};

// A coloured badge for an issue/violation severity (or resolved state), with an optional tooltip.
function SeverityTag({ className, isResolved = false, severity, tooltipText = "" }: SeverityTagProps): JSX.Element {
    const label = severity.toUpperCase();

    return (
        <Badge
            className={cn(
                isResolved ? "bg-slate-400 text-white" : SEVERITY_CLASSES[severity],
                "text-[11px]",
                className,
            )}
            title={tooltipText || undefined}
        >
            {isResolved ? <s>{label}</s> : label}
        </Badge>
    );
}

export { SeverityTag };
export default SeverityTag;
