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

import { all, put, takeEvery } from 'redux-saga/effects';
import convertReportData from './convertReportData';

export function* loadReportData() {
    yield put({ type: 'APP::SHOW_LOADING' });
    yield put({ type: 'APP::LOADING_REPORT_START' });

    // Parse JSON report data embedded in HTML page
    const reportDataNode = document.querySelector('script[id="ort-report-data"]');
    const reportDataText = reportDataNode ? reportDataNode.textContent : undefined;

    if (!reportDataText || reportDataText.trim().length === 0) {
        yield put({ type: 'APP::SHOW_NO_REPORT' });
    } else {
        const reportData = JSON.parse(reportDataText);
        yield put({ type: 'APP::LOADING_REPORT_DONE', payload: reportData });
        yield put({ type: 'APP::LOADING_CONVERTING_REPORT_START' });
    }
}

export function* watchConvertReportData() {
    yield takeEvery('APP::LOADING_CONVERTING_REPORT_START', convertReportData);
}

export function* watchLoadReportData() {
    yield takeEvery('APP::LOADING_START', loadReportData);
}

// single entry point to start all Sagas at once
export default function* rootSaga() {
    yield all([
        watchLoadReportData(),
        watchConvertReportData()
    ]);
}
