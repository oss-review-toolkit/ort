/*
 * Copyright (c) 2018 HERE Europe B.V.
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

const initState = {
    data: {
        loading: {
            state: 'INIT',
            percentage: 0
        },
        report: {}
    },
    view: {
        activeTabKey: 'summary'
    }
};

export default (state = initState, action) => {
    switch (action.type) {
    case 'LOADING_CONVERTING_REPORT_DATA': {
        return {
            ...state,
            data: {
                ...state.data,
                loading: {
                    ...state.data.loading,
                    state: 'LOADING',
                    text: 'processing data for optimal rendering...',
                    percentage: 25
                }
            }
        };
    }
    case 'LOADING_CONVERTING_REPORT_DATA_DONE': {
        return {
            ...state,
            data: {
                ...state.data,
                loading: {
                    ...state.data.loading,
                    state: 'LOADING',
                    text: 'data processing done...',
                    percentage: 100
                },
                report: action.payload
            }
        };
    }
    case 'LOADING_CONVERTING_REPORT_DATA_PROGRESS': {
        const { index, total } = action;
        const { loading } = state.data;
        const { percentage: currentPercentage } = loading;
        const newPercentage = Math.floor(currentPercentage + (99 - currentPercentage) / (total - index));

        return {
            ...state,
            data: {
                ...state.data,
                loading: {
                    ...state.data.loading,
                    state: 'LOADING',
                    text: `processing project (${index}/${total})...`,
                    percentage: newPercentage
                }
            }
        };
    }
    case 'LOADING_DONE': {
        return {
            ...state,
            data: {
                ...state.data,
                loading: {
                    ...state.data.loading,
                    state: 'LOADING',
                    text: 'all done...',
                    percentage: 100
                }
            }
        };
    }
    case 'LOADING_REPORT_DATA': {
        return {
            ...state,
            data: {
                ...state.data,
                loading: {
                    ...state.data.loading,
                    state: 'LOADING',
                    text: 'loading data...',
                    percentage: 1
                }
            }
        };
    }
    case 'LOADING_REPORT_DATA_DONE': {
        return {
            ...state,
            data: {
                ...state.data,
                loading: {
                    ...state.data.loading,
                    text: 'loaded data...',
                    percentage: 20
                },
                report: action.payload
            }
        };
    }
    case 'LOADING_REPORT_DATA_DONE_NO_DATA': {
        return {
            ...state,
            data: {
                ...state.data,
                loading: {
                    ...state.data.loading,
                    state: 'NO_REPORT_DATA'
                }
            }
        };
    }
    case 'VIEW_ONCHANGE_TAB': {
        return {
            ...state,
            view: {
                ...state.view,
                activeTabKey: action.activeTabKey
            }
        };
    }
    case 'VIEW_SHOW_REPORT': {
        return {
            ...state,
            data: {
                ...state.data,
                loading: {
                    ...state.data.loading,
                    state: 'DONE'
                }
            }
        };
    }
    default:
        return state;
    }
};
