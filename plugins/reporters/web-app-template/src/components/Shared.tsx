/*
 * Copyright (C) 2021 The ORT Project Copyright Holders <https://github.com/oss-review-toolkit/ort/blob/main/NOTICE>
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
    Check,
    ChevronRight,
    Copy,
    createLucideIcon,
    ExternalLink,
    File,
    FileBox,
    FileCheck,
    FileCode,
    FileX,
    type IconNode,
    type LucideIcon,
    type LucideProps,
    Pencil,
} from "lucide-react";
import Markdown from "markdown-to-jsx";
import type { JSX, ReactNode } from "react";
import { Children, forwardRef, isValidElement, useCallback, useState } from "react";
import { Light as SyntaxHighlighter } from "react-syntax-highlighter";
import yaml from "react-syntax-highlighter/dist/esm/languages/hljs/yaml";
import github from "react-syntax-highlighter/dist/esm/styles/hljs/github";

import { Badge } from "@/components/ui/Badge";
import { Button } from "@/components/ui/Button";
import { parseSpdxLicenseExpression } from "@/lib/spdx-license-expressions";
import { cn } from "@/lib/utils";
import { licenseToHslColor } from "@/utils";

SyntaxHighlighter.registerLanguage("yaml", yaml);

// The definitions below are ordered alphabetically by their exported name. Each component keeps its
// props interface and any private helpers (regexes, sub-components, constants it consumes) next to it.

const ISO_TRIM_REGEX = /(\.\d{3})\d*Z$/;

function convertIso8601Date2Sentence(iso8601Date: string | undefined | null): string {
    if (!iso8601Date) return "—";
    const date = new Date(iso8601Date.replace(ISO_TRIM_REGEX, "$1Z"));
    if (Number.isNaN(date.getTime())) return "—";

    const timeFormatter = new Intl.DateTimeFormat(undefined, {
        hour: "numeric",
        minute: "2-digit",
        hour12: true,
        timeZoneName: "short",
    });

    const dateFormatter = new Intl.DateTimeFormat(undefined, {
        year: "numeric",
        month: "long",
        day: "numeric",
    });

    return `${timeFormatter.format(date)} on ${dateFormatter.format(date)}`;
}

export interface CopyToClipboardProps {
    className?: string;
    label?: string;
    value: string;
}

function CopyToClipboard({ className, label = "Copy", value }: CopyToClipboardProps): JSX.Element {
    const [copied, setCopied] = useState(false);

    const handleCopy = useCallback(async () => {
        try {
            await navigator.clipboard.writeText(value);
            setCopied(true);
            window.setTimeout(() => setCopied(false), 1500);
        } catch {
            setCopied(false);
        }
    }, [value]);

    return (
        <Button
            aria-label={label}
            className={cn("size-7", className)}
            onClick={handleCopy}
            size="icon"
            title={copied ? "Copied" : label}
            type="button"
            variant="ghost"
        >
            {copied ? <Check aria-hidden="true" className="size-4" /> : <Copy aria-hidden="true" className="size-4" />}
        </Button>
    );
}

export interface DefinedTermProps {
    definition: string;
    term: string;
}

// A term shown with a dotted underline and a hover tooltip explaining it; the underline hints that a
// definition is available on hover.
function DefinedTerm({ definition, term }: DefinedTermProps): JSX.Element {
    return (
        <span className="cursor-help underline decoration-dotted underline-offset-2" title={definition}>
            {term}
        </span>
    );
}

// Include/exclude status icon: an excluded item shows a muted-grey FileX, an included one a
// foreground FileCheck. Shared so package tables and finding tables look identical.
function ExcludeStatusIcon({ excluded, reason }: { excluded: boolean; reason?: string }): JSX.Element {
    const text = excluded ? (reason ? `Excluded: ${reason}` : "Excluded") : "Included";
    const Icon = excluded ? FileX : FileCheck;
    return (
        <span className="inline-flex items-center justify-center" title={text}>
            <Icon aria-hidden="true" className="size-4 text-muted-foreground" />
            <span className="sr-only">{text}</span>
        </span>
    );
}

export interface ExpandRowIconProps {
    className?: string;
    expanded: boolean;
}

function ExpandRowIcon({ className, expanded }: ExpandRowIconProps): JSX.Element {
    return (
        <ChevronRight
            aria-hidden="true"
            className={cn("size-4 transition-transform", expanded ? "rotate-90" : undefined, className)}
        />
    );
}

// Compact, icon-only column header (with tooltip) for narrow icon columns, so a long title does not
// dictate the column width. Shared so every table uses identical styling.
function IconHeader({ Icon, label }: { Icon: LucideIcon; label: string }): JSX.Element {
    return (
        <span className="inline-flex items-center justify-center" title={label}>
            <Icon aria-hidden="true" className="size-4 text-muted-foreground" />
            <span className="sr-only">{label}</span>
        </span>
    );
}

/**
 * The canonical one-line definitions of the license types, shared by the Summary license card and the
 * package license rows so both explain "Effective", "Declared" and "Detected" identically.
 */
export const LICENSE_TERM_DEFINITIONS = {
    declared: "Licenses from package metadata mapped to an SPDX expression.",
    declaredNonMapped: "Licenses from package metadata.",
    detected: "Licenses from scanning the package sources.",
    effective: "Declared plus detected, after removing non-applicable licenses and applying license choices.",
} as const;

// The SPDX value used when no license has been asserted for a field (rather than leaving it blank).
export const NO_ASSERTION = "NOASSERTION";

const NO_ASSERTION_EXPLANATION = "NOASSERTION is the SPDX value meaning no license was asserted for this field.";

export interface LicenseBadgeProps {
    className?: string;
    // When set, the badge uses the license's assigned color (e.g. to match the Summary chart legend)
    // instead of the default light-grey style.
    color?: string;
    name: string;
}

// The single source of truth for rendering a license name as a badge across the whole app. The badge
// grows to fit longer identifiers (e.g. "LicenseRef-scancode-public-domain-disclaimer") and only when
// one exceeds the max width is it cut off with a CSS ellipsis; the full id stays available on hover.
function LicenseBadge({ className, color, name }: LicenseBadgeProps): JSX.Element {
    const isNoAssertion = name === NO_ASSERTION;
    // The colour is derived from the license id (single source of truth) so the same license gets the
    // same WCAG-AA colour in every column; callers may override it, and NOASSERTION stays neutral.
    const backgroundColor = color ?? (isNoAssertion ? undefined : licenseToHslColor(name));
    const shared = "max-w-sm truncate px-1.5 py-0 text-[11px] font-mono";
    const badge =
        backgroundColor !== undefined ? (
            <Badge
                className={cn(shared, "text-white", className)}
                style={{ backgroundColor }}
                title={isNoAssertion ? undefined : name}
            >
                {name}
            </Badge>
        ) : (
            <Badge className={cn(shared, className)} title={isNoAssertion ? undefined : name} variant="secondary">
                {name}
            </Badge>
        );
    // NOASSERTION gets a styled tooltip explaining what it means; truncated ids rely on the native title.
    if (!isNoAssertion) {
        return badge;
    }
    return <span title={NO_ASSERTION_EXPLANATION}>{badge}</span>;
}

export interface LicenseBadgeListProps {
    className?: string;
    names: readonly string[];
}

function LicenseBadgeList({ className, names }: LicenseBadgeListProps): JSX.Element | null {
    if (names.length === 0) return null;

    return (
        <div className={cn("flex flex-wrap gap-1", className)}>
            {names.map((name) => (
                <LicenseBadge key={name} name={name} />
            ))}
        </div>
    );
}

export interface LicenseExpressionProps {
    className?: string;
    expression: string;
}

// Render an SPDX license expression as a badge per license (and WITH exception), with the AND / OR /
// WITH operators and any parentheses shown between them, so no structure is lost to a single block.
function LicenseExpression({ className, expression }: LicenseExpressionProps): JSX.Element | null {
    const { isValid, tokens } = parseSpdxLicenseExpression(expression);
    if (tokens.length === 0) return null;

    // A malformed / non-SPDX expression is shown verbatim as a single badge rather than being mis-split
    // into wrong licenses and operators.
    if (!isValid) {
        return <LicenseBadge name={expression} {...(className ? { className } : {})} />;
    }

    // Keys are positional but stable (the tokens of an expression never reorder); build them outside
    // the JSX so they are not flagged as array-index keys.
    const items = tokens.map((token, index) => ({ token, key: `token-${index}` }));

    return (
        <span className={cn("inline-flex flex-wrap items-center gap-1", className)}>
            {items.map(({ key, token }) => {
                switch (token.type) {
                    case "license":
                    case "exception":
                        return <LicenseBadge key={key} name={token.value} />;
                    case "operator":
                        return (
                            <span className="font-semibold text-muted-foreground text-xs" key={key}>
                                {token.value}
                            </span>
                        );
                    default:
                        return (
                            <span className="text-muted-foreground" key={key}>
                                {token.type === "open" ? "(" : ")"}
                            </span>
                        );
                }
            })}
        </span>
    );
}

export interface LicenseExpressionListProps {
    className?: string;
    expressions: readonly string[];
}

// Render several SPDX license expressions, each on its own line, as structured badges.
function LicenseExpressionList({ className, expressions }: LicenseExpressionListProps): JSX.Element | null {
    if (expressions.length === 0) return null;

    return (
        <div className={cn("flex flex-col items-start gap-1.5", className)}>
            {expressions.map((expression) => (
                <LicenseExpression expression={expression} key={expression} />
            ))}
        </div>
    );
}

export interface MarkdownTextProps {
    children: string;
    className?: string;
}

// markdown-to-jsx renders an empty code block when a fence is immediately followed by a blank line, and
// leaks the code out as markdown (headings, paragraphs). ORT how-to-fix templates are written exactly
// that way, so drop the blank line right after an opening fence before rendering.
function normalizeFencedCodeBlocks(markdown: string): string {
    return markdown.replace(/(^|\n)([ \t]*`{3,}[^\n]*)\n[ \t]*\n/g, "$1$2\n");
}

// Render markdown fenced code blocks like the .ort.yml view in Run Details: a grey, bordered,
// syntax-highlighted block instead of the default dark prose code block. markdown-to-jsx wraps a fenced
// block as <pre><code class="lang-...">text</code></pre>, so pull the language and text out of it.
function MarkdownCodeBlock({ children }: { children?: ReactNode }): JSX.Element {
    const code = Children.toArray(children)[0];
    const props = isValidElement<{ className?: string; children?: ReactNode }>(code) ? code.props : undefined;
    const language = /lang-([\w-]+)/.exec(props?.className ?? "")?.[1] ?? "yaml";
    const raw = props?.children ?? children;
    const text = (typeof raw === "string" ? raw : Children.toArray(raw).join("")).replace(/\n+$/, "");
    // not-prose keeps the typography plugin from restyling (and shrinking, via its em-based font size)
    // the block; force 11px so it reads like the .ort.yml view rather than prose's tiny code text.
    return (
        <SyntaxHighlight className="not-prose my-3 text-[11px]" language={language} showLineNumbers={false}>
            {text}
        </SyntaxHighlight>
    );
}

function MarkdownText({ children, className }: MarkdownTextProps): JSX.Element {
    return (
        // Links render through Url and are marked not-prose so the typography plugin leaves them alone and
        // they match every other link in the app; fenced code blocks render like the .ort.yml view.
        <div className={cn("prose prose-sm dark:prose-invert min-w-0 max-w-none prose-p:leading-relaxed", className)}>
            <Markdown
                options={{
                    overrides: {
                        a: { component: Url, props: { className: "break-all not-prose" } },
                        pre: { component: MarkdownCodeBlock },
                    },
                }}
            >
                {normalizeFencedCodeBlocks(children)}
            </Markdown>
        </div>
    );
}

// The .ort.yml run-configuration icon: a combined icon (see
// https://lucide.dev/guide/react/advanced/combining-icons) — a plain file with the letters "ORT" set
// inside it. The text is given an explicit fill because a lucide icon's <svg> defaults to
// fill="none", which would hide it. It is a drop-in LucideIcon, so it accepts the usual props.
const OrtYmlFileIcon = forwardRef<SVGSVGElement, LucideProps>((props, ref) => (
    <File ref={ref} {...props}>
        <text
            fill="currentColor"
            fontFamily="Verdana, sans-serif"
            fontSize={6}
            stroke="none"
            textAnchor="middle"
            x={12}
            y={18}
        >
            ORT
        </text>
    </File>
));

OrtYmlFileIcon.displayName = "OrtYmlFileIcon";

// The package-configurations icon: a combined icon (see
// https://lucide.dev/guide/react/advanced/combining-icons) — the file-code icon with a small pencil
// nested in its lower-right corner, conveying an "edited/overridden config file". It is a drop-in
// LucideIcon, so it accepts the usual size/className props.
const PackageConfigurationIcon = forwardRef<SVGSVGElement, LucideProps>((props, ref) => (
    <FileCode ref={ref} {...props}>
        <Pencil absoluteStrokeWidth size={12} x={12} y={11} />
    </FileCode>
));
PackageConfigurationIcon.displayName = "PackageConfigurationIcon";

// The package-curations icon: a combined icon (see
// https://lucide.dev/guide/react/advanced/combining-icons) — the file-box icon with a small pencil
// nested in its lower-right corner, conveying an "edited/curated package". It is a drop-in
// LucideIcon, so it accepts the usual size/className props.
const PackageCurationIcon = forwardRef<SVGSVGElement, LucideProps>((props, ref) => (
    <FileBox ref={ref} {...props}>
        <Pencil absoluteStrokeWidth size={12} x={12} y={11} />
    </FileBox>
));
PackageCurationIcon.displayName = "PackageCurationIcon";

// A package identifier. When an onClick handler is provided it renders as a link (used to jump to the
// package in the main results table); otherwise it is plain monospace text.
function PackageLink({ id, onClick }: { id: string; onClick?: (id: string) => void }): JSX.Element | null {
    if (!id) return null;
    if (!onClick) {
        return <span className="break-all font-mono text-xs">{id}</span>;
    }
    return (
        <button
            className="break-all text-left font-mono text-primary text-xs underline-offset-4 hover:underline"
            onClick={(event) => {
                event.stopPropagation();
                onClick(id);
            }}
            type="button"
        >
            {id}
        </button>
    );
}

function renderAnchor(text: string, href?: string): JSX.Element {
    return <Url href={href ?? text}>{text}</Url>;
}

// The lucide "summary" icon (https://lucide.dev/icons/summary) used for the Summary section. It is
// not part of the pinned lucide-react 0.475 release, so its paths are vendored here as a drop-in
// LucideIcon rather than bumping the whole icon set.
const SUMMARY_ICON_NODE: IconNode = [
    ["path", { d: "M15 4H7", key: "row-top" }],
    ["path", { d: "m18 16 3 3-3 3", key: "arrow" }],
    ["path", { d: "M3 4v13a2 2 0 0 0 2 2h16", key: "frame" }],
    ["path", { d: "M7 14h7", key: "row-bottom" }],
    ["path", { d: "M7 9h12", key: "row-middle" }],
];

const SummaryIcon = createLucideIcon("Summary", SUMMARY_ICON_NODE);

// Mirror the Overview key/value styling: keys use the muted-foreground grey, values use the normal
// foreground color (inverting the github theme, which emphasises keys and colors the values).
const syntaxTheme = {
    ...github,
    hljs: { ...github.hljs, color: "var(--foreground)", background: "transparent" },
    "hljs-attr": { color: "var(--muted-foreground)" },
    "hljs-bullet": { color: "var(--muted-foreground)" },
    "hljs-symbol": { color: "var(--muted-foreground)" },
    "hljs-string": { color: "var(--foreground)" },
    "hljs-doctag": { color: "var(--foreground)" },
    "hljs-title": { color: "var(--muted-foreground)" },
    "hljs-section": { color: "var(--muted-foreground)" },
    "hljs-selector-id": { color: "var(--muted-foreground)" },
};

export interface SyntaxHighlightProps {
    children: string;
    className?: string;
    language?: string;
    showLineNumbers?: boolean;
}

function SyntaxHighlight({
    children,
    className,
    language = "yaml",
    showLineNumbers = true,
}: SyntaxHighlightProps): JSX.Element {
    return (
        <div className={cn("overflow-auto rounded-md border bg-muted/30 text-xs", className)}>
            <SyntaxHighlighter
                codeTagProps={{ style: { background: "transparent" } }}
                customStyle={{ background: "transparent", margin: 0 }}
                language={language}
                lineNumberStyle={{ color: "var(--muted-foreground)" }}
                showLineNumbers={showLineNumbers}
                style={syntaxTheme}
            >
                {children}
            </SyntaxHighlighter>
        </div>
    );
}

// The lucide "toolbox" icon (https://lucide.dev/icons/toolbox) used for the Run Details "Tools" tab.
// It is not part of the pinned lucide-react 0.475 release, so its paths are vendored here as a
// drop-in LucideIcon rather than bumping the whole icon set.
const TOOLBOX_ICON_NODE: IconNode = [
    ["path", { d: "M16 12v4", key: "right-slot" }],
    [
        "path",
        {
            d: "M16 6a2 2 0 0 1 1.414.586l4 4A2 2 0 0 1 22 12v7a2 2 0 0 1-2 2H4a2 2 0 0 1-2-2v-7a2 2 0 0 1 .586-1.414l4-4A2 2 0 0 1 8 6z",
            key: "body",
        },
    ],
    ["path", { d: "M16 6V4a2 2 0 0 0-2-2h-4a2 2 0 0 0-2 2v2", key: "handle" }],
    ["path", { d: "M2 14h20", key: "divider" }],
    ["path", { d: "M8 12v4", key: "left-slot" }],
];

const ToolsIcon = createLucideIcon("Toolbox", TOOLBOX_ICON_NODE);

export interface UrlProps {
    children?: ReactNode;
    className?: string;
    href: string;
    showIcon?: boolean;
    truncate?: boolean;
}

function Url({ children, className, href, showIcon = true, truncate = false }: UrlProps): JSX.Element {
    if (truncate) {
        // Constrain the link to its container and clip the (often very long) URL with an ellipsis so
        // it can never push adjacent content aside; the icon stays pinned and the full URL is on hover.
        return (
            <a
                className={cn("flex min-w-0 items-center text-primary underline-offset-4 hover:underline", className)}
                href={href}
                rel="noopener noreferrer"
                target="_blank"
                title={typeof children === "string" ? children : href}
            >
                <span className="truncate">{children ?? href}</span>
                {showIcon ? <ExternalLink aria-hidden="true" className="ml-0.5 size-3 shrink-0 align-middle" /> : null}
            </a>
        );
    }
    return (
        // A plain inline link (not inline-flex) so long URLs wrap with the surrounding text instead
        // of forming an unbreakable atomic box; the icon rides along inline.
        <a
            className={cn("text-primary underline-offset-4 hover:underline", className)}
            href={href}
            rel="noopener noreferrer"
            target="_blank"
        >
            {children ?? href}
            {showIcon ? <ExternalLink aria-hidden="true" className="ml-0.5 inline size-3 align-middle" /> : null}
        </a>
    );
}

export {
    CopyToClipboard,
    convertIso8601Date2Sentence,
    DefinedTerm,
    ExcludeStatusIcon,
    ExpandRowIcon,
    IconHeader,
    LicenseBadge,
    LicenseBadgeList,
    LicenseExpression,
    LicenseExpressionList,
    MarkdownText,
    OrtYmlFileIcon,
    PackageConfigurationIcon,
    PackageCurationIcon,
    PackageLink,
    renderAnchor,
    SummaryIcon,
    SyntaxHighlight,
    ToolsIcon,
    Url,
};
