/*
 * Copyright (C) 2020 The ORT Project Copyright Holders <https://github.com/oss-review-toolkit/ort/blob/main/NOTICE>
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

import type { EvaluatedModelLicense } from "@/types/evaluatedModelData";
import { licenseToHslColor } from "@/utils";

class WebAppLicense {
    #_id: number | undefined;

    #id: string | undefined;

    #color: string | undefined;

    constructor(obj?: EvaluatedModelLicense) {
        if (obj) {
            if (Number.isInteger(obj._id)) {
                this.#_id = obj._id;
            }

            if (obj.id) {
                this.#id = obj.id;
            }

            this.#color = licenseToHslColor(this.#id ?? "");
        }
    }

    get _id(): number | undefined {
        return this.#_id;
    }

    get id(): string | undefined {
        return this.#id;
    }

    get color(): string | undefined {
        return this.#color;
    }
}

export default WebAppLicense;
