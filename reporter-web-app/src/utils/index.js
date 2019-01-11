/*
 * Copyright (C) 2017-2019 HERE Europe B.V.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *         http://www.apache.org/licenses/LICENSE-2.0
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

// SPDX-License-Identifier: CC-BY-3.0
// Author KimKha
// https://stackoverflow.com/questions/194846/is-there-any-kind-of-hash-code-function-in-javascript#8076436
export function hashCode(str) {
    let hash = 0;

    for (let i = 0; i < str.length; i++) {
        const character = str.charCodeAt(i);
        hash = ((hash << 5) - hash) + character; // eslint-disable-line
        // Convert to 32bit integer
        hash &= hash; // eslint-disable-line
    }
    return hash;
}

// Utility boolean function to determine if input is a number
export function isNumeric(n) {
    return !Number.isNaN(parseFloat(n)) && Number.isFinite(n);
}

// Utility function to remove duplicates from Array
// https://codehandbook.org/how-to-remove-duplicates-from-javascript-array/
export function removeDuplicatesInArray(arr) {
    return Array.from(new Set(arr));
}
