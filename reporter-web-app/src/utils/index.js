/*
 * Copyright (C) 2017-2021 HERE Europe B.V.
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

/* eslint import/prefer-default-export: 0 */

/* Utility function to generate random numbers and letters string
 * Based on vjt@openssl.it public domain code, see
 * https://gist.github.com/vjt/2239787
 */
const randomStringGenerator = (length = (Math.floor(Math.random() * 501) + 20)) => {
    const rand = (str) => str[Math.floor(Math.random() * str.length)];
    const get = (source, len, a) => {
        for (let i = 0; i < len; i++) {
            a.push(rand(source));
        }

        return a;
    };
    const alpha = (len, a) => get('A1BCD2EFG3HIJ4KLM5NOP6QRS7TUV8WXY9Z', len, a);
    const symbol = (len, a) => get('-:;_$!', len, a);
    const l = Math.floor(length / 2);
    const r = Math.ceil(length / 2);

    return alpha(l, symbol(1, alpha(r, []))).join('');
};

// Utility function to generate color for given license string 
const licenseToHslColor = (license) => {
    let hash = 0;
    let random = Math.floor(Math.random() * 20 + 5);

    for (let i = 0; i < license.length; i++) {
        hash = license.charCodeAt(i) + ((hash << 5) - hash);
        hash |= 0;
    }

    return 'hsl(' + ((Math.abs(hash) % 320) + random) + ',' +
        (25 + 70 * Math.random()) + '%,' + 
        (55 + 10 * Math.random()) + '%)';
}

export { randomStringGenerator, licenseToHslColor };
