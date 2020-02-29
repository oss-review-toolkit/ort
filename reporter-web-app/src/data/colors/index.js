/*
 * Copyright (C) 2017-2020 HERE Europe B.V.
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

import COLORS from './json/colors.json';

export const metadata = {
    packageName: 'unique-colors',
    packageCopyrightText: 'Federico Spini',
    packageComment: 'Expanded version for ORT of unique-colors with additional colors',
    packageDescription: 'Perceptually unique colors generator (up to 92)',
    packageDownloadLocation:
        'git+https://github.com/federicospini/unique-colors.git@e642ae332f1b92b6e204df1cd05b930ae61ee79a',
    packageHomePage: 'https://github.com/federicospini/unique-colors/',
    packageLicenseDeclared: 'MIT',
    packageSupplier: 'Federico Spini',
    packageVersion: 'e642ae332f1b92b6e204df1cd05b930ae61ee79a'
};

export const UNIQUE_COLORS = (() => ({
    ...metadata,
    data: COLORS,
    type: 'unique-colors'
}))();
