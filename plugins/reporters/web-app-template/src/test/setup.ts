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

import "@testing-library/jest-dom/vitest";

import { cleanup } from "@testing-library/react";
import { afterEach } from "vitest";

// jsdom has no layout engine, so components that scroll a node into view need an inert stub.
if (typeof Element.prototype.scrollIntoView !== "function") {
    Element.prototype.scrollIntoView = function scrollIntoView(): void {
        // Intentionally empty: there is nothing to scroll in jsdom.
    };
}

// Radix popovers/selects probe pointer-capture APIs that jsdom does not implement.
if (typeof Element.prototype.hasPointerCapture !== "function") {
    Element.prototype.hasPointerCapture = function hasPointerCapture(): boolean {
        return false;
    };
    Element.prototype.setPointerCapture = function setPointerCapture(): void {
        // Intentionally empty.
    };
    Element.prototype.releasePointerCapture = function releasePointerCapture(): void {
        // Intentionally empty.
    };
}

// Several UI primitives observe element size; jsdom does not implement ResizeObserver.
if (typeof globalThis.ResizeObserver === "undefined") {
    class ResizeObserverStub implements ResizeObserver {
        observe(): void {
            // Intentionally empty.
        }

        unobserve(): void {
            // Intentionally empty.
        }

        disconnect(): void {
            // Intentionally empty.
        }
    }

    globalThis.ResizeObserver = ResizeObserverStub;
}

// jsdom in this configuration does not expose localStorage; provide a minimal in-memory implementation
// so settings persistence (and anything else reading/writing localStorage) works under test.
if (typeof window !== "undefined" && !window.localStorage) {
    const store = new Map<string, string>();
    const localStorageStub = {
        get length(): number {
            return store.size;
        },
        clear(): void {
            store.clear();
        },
        getItem(key: string): string | null {
            return store.get(key) ?? null;
        },
        key(index: number): string | null {
            return Array.from(store.keys())[index] ?? null;
        },
        removeItem(key: string): void {
            store.delete(key);
        },
        setItem(key: string, value: string): void {
            store.set(key, String(value));
        },
    } as Storage;
    Object.defineProperty(window, "localStorage", { configurable: true, value: localStorageStub });
}

// Unmount React trees after each test since automatic cleanup is disabled with globals: false.
afterEach(() => {
    cleanup();
});
