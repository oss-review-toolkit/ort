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

import PackageCurationData from "@/models/PackageCurationData";
import type { EvaluatedModelWebAppPackageCuration } from "@/types/evaluatedModelData";
import { randomStringGenerator } from "@/utils";

class WebAppPackageCuration {
    #_id: number | undefined;

    #curations: PackageCurationData | undefined;

    #id: string | undefined;

    key: string;

    constructor(obj?: EvaluatedModelWebAppPackageCuration) {
        if (obj) {
            if (Number.isInteger(obj._id)) {
                this.#_id = obj._id;
            }

            if (obj.curations) {
                this.#curations = new PackageCurationData(obj.curations);
            }

            if (obj.id) {
                this.#id = obj.id;
            }
        }

        this.key = randomStringGenerator(20);
    }

    get _id(): number | undefined {
        return this.#_id;
    }

    get curations(): PackageCurationData | undefined {
        return this.#curations;
    }

    get id(): string | undefined {
        return this.#id;
    }
}

export default WebAppPackageCuration;
