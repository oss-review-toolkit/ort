/*
 * Copyright (C) 2019-2020 HERE Europe B.V.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
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

class ScannerDetails {
    constructor(obj) {
        this.name = '';
        this.version = '';
        this.configuration = '';

        if (obj instanceof Object) {
            if (obj.name) {
                this.name = obj.name;
            }

            if (obj.version) {
                this.version = obj.version;
            }

            if (obj.configuration) {
                this.configuration = obj.configuration;
            }
        }
    }
}

export default ScannerDetails;
