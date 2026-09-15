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

// Types and a tokeniser for SPDX license expressions, following the grammar at
// https://spdx.github.io/spdx-spec/v3.0.1/annexes/spdx-license-expressions/.
//
// A license expression is either a simple expression (a single license-id, an optional "+" suffix or
// a license-ref) or a composite expression built from the operators OR, AND and WITH (each also valid
// in lower case) and parentheses. The right operand of WITH is an addition expression (a
// license-exception-id or addition-ref).
//
// The tokeniser is deliberately lightweight: it splits an expression into an ordered list of simple
// expressions, exceptions, operators and parentheses so the UI can render a badge per license instead
// of one long block, and so each individual simple expression is filterable on its own. It does not
// build a precedence tree - the ordered tokens (with the original parentheses preserved) are enough
// to render the expression faithfully.

/** A simple SPDX license expression: a single license-id or license-ref, e.g. "Apache-2.0" or "LicenseRef-23". */
export type SpdxSimpleLicenseExpression = string;

/** An SPDX license-exception-id or addition-ref, the right operand of WITH, e.g. "Bison-exception-2.2". */
export type SpdxExceptionId = string;

/** An SPDX license expression operator. */
export type SpdxOperator = "AND" | "OR" | "WITH";

/** One element of a tokenised SPDX composite license expression. */
export type SpdxToken =
    | { type: "license"; value: SpdxSimpleLicenseExpression }
    | { type: "exception"; value: SpdxExceptionId }
    | { type: "operator"; value: SpdxOperator }
    | { type: "open" }
    | { type: "close" };

/** A parsed SPDX license expression, e.g. "Apache-2.0 WITH LLVM-exception" or "BSD-3-Clause AND MIT". */
export interface SpdxCompositeLicenseExpression {
    /** Whether the tokens form a structurally valid expression (balanced parens, no dangling operators). */
    isValid: boolean;
    /** The distinct simple expressions in the expression (excluding WITH exceptions), for filtering. */
    simpleExpressions: SpdxSimpleLicenseExpression[];
    /** The original expression text. */
    text: string;
    /** The expression split into ordered tokens, for rendering. */
    tokens: SpdxToken[];
}

// Validate the token sequence with a small state machine over the grammar: an expression alternates
// operands (a license or a parenthesised group) and binary operators, WITH is followed by an
// exception, and parentheses are balanced. This catches malformed input (e.g. "MIT AND", "(MIT",
// "AND OR") so the caller can fall back to rendering the raw text instead of mis-splitting it.
function isValidTokenSequence(tokens: SpdxToken[]): boolean {
    if (tokens.length === 0) {
        return false;
    }
    let depth = 0;
    let state: "operand" | "exception" | "operatorOrEnd" = "operand";
    for (const token of tokens) {
        switch (token.type) {
            case "open":
                if (state !== "operand") return false;
                depth += 1;
                break;
            case "close":
                if (state !== "operatorOrEnd") return false;
                depth -= 1;
                if (depth < 0) return false;
                break;
            case "license":
                if (state !== "operand") return false;
                state = "operatorOrEnd";
                break;
            case "exception":
                if (state !== "exception") return false;
                state = "operatorOrEnd";
                break;
            case "operator":
                if (state !== "operatorOrEnd") return false;
                state = token.value === "WITH" ? "exception" : "operand";
                break;
        }
    }
    return depth === 0 && state === "operatorOrEnd";
}

const OPERATORS = new Set<SpdxOperator>(["AND", "OR", "WITH"]);

function toOperator(token: string): SpdxOperator | undefined {
    // Operators may be written in upper or lower case (RFC 7405 %s"AND" / %s"and"); normalise to upper.
    const upper = token.toUpperCase();
    return (OPERATORS as Set<string>).has(upper) ? (upper as SpdxOperator) : undefined;
}

// Isolate parentheses so they tokenise separately even when written against an identifier, e.g. "(MIT".
function tokenize(text: string): string[] {
    return text
        .replace(/([()])/g, " $1 ")
        .split(/\s+/)
        .filter((token) => token.length > 0);
}

export function parseSpdxLicenseExpression(text: string): SpdxCompositeLicenseExpression {
    const tokens: SpdxToken[] = [];
    const simpleExpressions: SpdxSimpleLicenseExpression[] = [];
    let afterWith = false;

    for (const raw of tokenize(text)) {
        if (raw === "(") {
            tokens.push({ type: "open" });
            continue;
        }
        if (raw === ")") {
            tokens.push({ type: "close" });
            continue;
        }

        const operator = toOperator(raw);
        if (operator !== undefined) {
            tokens.push({ type: "operator", value: operator });
            afterWith = operator === "WITH";
            continue;
        }

        if (afterWith) {
            tokens.push({ type: "exception", value: raw });
            afterWith = false;
        } else {
            tokens.push({ type: "license", value: raw });
            simpleExpressions.push(raw);
        }
    }

    return {
        text,
        tokens,
        simpleExpressions: [...new Set(simpleExpressions)],
        isValid: isValidTokenSequence(tokens),
    };
}
