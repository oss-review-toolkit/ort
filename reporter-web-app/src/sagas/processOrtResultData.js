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

import { delay, put, select } from 'redux-saga/effects';
import { getOrtResult } from '../reducers/selectors';
import WebAppOrtResult from '../models/WebAppOrtResult';

function* processOrtResultData() {
    const ortResultData = yield select(getOrtResult);
    yield delay(200);

    const webAppOrtResult = new WebAppOrtResult(ortResultData);

    // Make webAppOrtResult inspectable via Browser's console
    window.ORT = webAppOrtResult;

    yield delay(100);
    yield put({ type: 'APP::LOADING_PROCESS_ORT_RESULT_DATA_DONE', payload: webAppOrtResult });
    yield delay(50);
    yield put({ type: 'APP::LOADING_DONE' });
    yield delay(50);
    yield put({ type: 'APP::SHOW_TABS' });

    return webAppOrtResult;
}

export default processOrtResultData;
