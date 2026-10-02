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

import { describe, expect, it, vi } from "vitest";

import { decodeBase64Gzip, payloadToEvaluatedModel } from "@/lib/reportData";

async function gzipToBase64(text: string): Promise<string> {
    const source = new ReadableStream<BufferSource>({
        start(controller) {
            controller.enqueue(new TextEncoder().encode(text));
            controller.close();
        },
    });
    const buffer = await new Response(source.pipeThrough(new CompressionStream("gzip"))).arrayBuffer();
    const bytes = new Uint8Array(buffer);
    let binary = "";
    for (let i = 0; i < bytes.length; i += 0x8000) {
        binary += String.fromCharCode(...bytes.subarray(i, i + 0x8000));
    }

    return btoa(binary);
}

describe("decodeBase64Gzip", () => {
    it("inflates a payload back to the exact text it was built from", async () => {
        const text = JSON.stringify(
            Array.from({ length: 2000 }, (_, index) => ({ id: `Maven:com.example:lib:${index}`, license: "MIT" })),
        );
        expect(await decodeBase64Gzip(await gzipToBase64(text))).toBe(text);
    });

    it("decodes text whose multi-byte characters straddle an inflate chunk", async () => {
        // The inflate arrives in chunks, so the decoder has to be fed with `stream: true`; without it a
        // character split across two chunks comes back as a replacement character.
        const text = `${"ü".repeat(100_000)}\u{1F600}`;
        expect(await decodeBase64Gzip(await gzipToBase64(text))).toBe(text);
    });

    it("reads the payload without constructing a Blob", async () => {
        // Reading a Blob inside a Web Worker fails in Safari with "The I/O read operation failed", which
        // made every report unreadable there (issue #12553). Keep the Blob out of the decode path.
        const blobSpy = vi.spyOn(globalThis, "Blob");
        try {
            await decodeBase64Gzip(await gzipToBase64("[]"));
            expect(blobSpy).not.toHaveBeenCalled();
        } finally {
            blobSpy.mockRestore();
        }
    });
});

describe("payloadToEvaluatedModel", () => {
    it("parses a gzip payload", async () => {
        const model = await payloadToEvaluatedModel(await gzipToBase64('{"packages":[]}'), true);
        expect(model.packages).toEqual([]);
    });

    it("parses a plain JSON payload without inflating it", async () => {
        const model = await payloadToEvaluatedModel('{"packages":[]}', false);
        expect(model.packages).toEqual([]);
    });
});
