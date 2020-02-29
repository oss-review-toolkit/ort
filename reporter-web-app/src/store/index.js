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

import { applyMiddleware, createStore } from 'redux';
import createSagaMiddleware from 'redux-saga';

import { createLogger } from 'redux-logger';
import reducer from '../reducers';
import rootSaga from '../sagas';

// See for all log options
// https://github.com/evgenyrodionov/redux-logger
const loggerMiddleware = createLogger({
    collapsed: (getState, action, logEntry) => !logEntry.error,
    diff: true,
    /* Disabling Redux Logger diff function for TREE::NODE_SELECT action
     * as logger crashes with https://github.com/LogRocket/redux-logger/issues/243
     * Steps to reproduce crash:
     *  1) Navigate to Tree tab
     *  2) Search for package
     *  3) Select other tree nodes will crash the logger
     *
     * Disable logger also TABLE::CHANGE_PACKAGES_TABLE action e.g.
     * filtering table in TableView
     */
    diffPredicate: (getState, action) => action.type !== 'TREE::NODE_SELECT'
        && action.type !== 'TABLE::CHANGE_PACKAGES_TABLE',
    predicate: () => process.env.NODE_ENV === 'development'
});
const sagaMiddleware = createSagaMiddleware();

const store = createStore(
    reducer,
    applyMiddleware(
        sagaMiddleware,
        loggerMiddleware
    )
);

sagaMiddleware.run(rootSaga);

export default store;
