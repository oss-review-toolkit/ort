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

const initState = {
    app: {
        loading: {
            percentage: 0,
            text: ''
        },
        showAboutModal: false,
        showKey: 'ort-loading'
    },
    summary: {
        declaredLicensesChart: [],
        detectedLicensesChart: [],
        declaredLicensesFilter: {
            filteredInfo: {},
            sortedInfo: {}
        },
        detectedLicensesFilter: {
            filteredInfo: {},
            sortedInfo: {}
        },
        issuesFilter: {
            filteredInfo: {},
            sortedInfo: {}
        },
        shouldComponentUpdate: false,
        ruleViolationsFilter: {
            filteredInfo: {},
            sortedInfo: {}
        }
    },
    table: {
        filter: {
            filteredInfo: {},
            sortedInfo: {}
        },
        filterData: [],
        shouldComponentUpdate: false
    },
    tree: {
        autoExpandParent: true,
        expandedKeys: [],
        matchedKeys: [],
        searchValue: '',
        searchIndex: 0,
        selectedWebAppTreeNode: null,
        selectedKeys: [],
        shouldComponentUpdate: false,
        showDrawer: false
    },
    data: {
        ortResult: {},
        ortResultLastUpdate: null
    }
};

export default (state = initState, action) => {
    switch (action.type) {
    case 'APP::LOADING_DONE': {
        return {
            ...state,
            app: {
                ...state.app,
                loading: {
                    ...state.app.loading,
                    text: 'Almost ready to display scan report...',
                    percentage: 100
                }
            }
        };
    }
    case 'APP::LOADING_ORT_RESULT_DATA_START': {
        return {
            ...state,
            app: {
                ...state.app,
                loading: {
                    ...state.app.loading,
                    text: 'Loading result data...',
                    percentage: 1
                }
            }
        };
    }
    case 'APP::LOADING_ORT_RESULT_DATA_DONE': {
        return {
            ...state,
            app: {
                ...state.app,
                loading: {
                    ...state.app.loading,
                    text: 'Loaded result data...',
                    percentage: 20
                }
            },
            data: {
                ...state.data,
                ortResult: action.payload,
                ortResultLastUpdate: Date.now()
            }
        };
    }
    case 'APP::LOADING_PROCESS_ORT_RESULT_DATA_START': {
        return {
            ...state,
            app: {
                ...state.app,
                loading: {
                    ...state.app.loading,
                    text: 'Processing result data...',
                    percentage: 55
                }
            }
        };
    }
    case 'APP::LOADING_PROCESS_ORT_RESULT_DATA_DONE': {
        return {
            ...state,
            app: {
                ...state.app,
                loading: {
                    ...state.app.loading,
                    text: 'Processed report data...',
                    percentage: 95
                }
            },
            data: {
                ...state.data,
                ortResult: action.payload
            }
        };
    }
    case 'APP::SHOW_NO_REPORT': {
        return {
            ...state,
            app: {
                ...state.app,
                showKey: 'ort-no-report-data'
            }
        };
    }
    case 'APP::CHANGE_TAB': {
        const { tree: { showDrawer } } = state;

        const newState = {
            ...state,
            app: {
                ...state.app,
                showKey: action.key
            },
            summary: {
                ...state.summary,
                shouldComponentUpdate: action.key === 'ort-tabs-summary'
            },
            table: {
                ...state.table,
                shouldComponentUpdate: action.key === 'ort-tabs-table'
            },
            tree: {
                ...state.tree,
                shouldComponentUpdate: action.key === 'ort-tabs-tree'
            }
        };

        if (action.key !== 'tree' && showDrawer) {
            newState.tree.showDrawer = false;
        }

        return newState;
    }
    case 'APP::HIDE_ABOUT_MODAL': {
        return {
            ...state,
            app: {
                ...state.app,
                showAboutModal: false
            }
        };
    }
    case 'APP::SHOW_ABOUT_MODAL': {
        return {
            ...state,
            app: {
                ...state.app,
                showAboutModal: true
            }
        };
    }
    case 'APP::SHOW_LOAD_VIEW': {
        return {
            ...state,
            app: {
                ...state.app,
                showKey: 'ort-loading'
            }
        };
    }
    case 'APP::SHOW_TABS': {
        return {
            ...state,
            app: {
                ...state.app,
                showKey: 'ort-tabs-summary'
            },
            summary: {
                ...state.summary,
                shouldComponentUpdate: true
            }
        };
    }
    case 'SUMMARY::CHANGE_DECLARED_LICENSES_TABLE': {
        const {
            declaredLicensesChart,
            declaredLicensesFilter
        } = action.payload;

        return {
            ...state,
            summary: {
                ...state.summary,
                declaredLicensesChart,
                declaredLicensesFilter
            }
        };
    }
    case 'SUMMARY::CHANGE_DETECTED_LICENSES_TABLE': {
        const {
            detectedLicensesChart,
            detectedLicensesFilter
        } = action.payload;

        return {
            ...state,
            summary: {
                ...state.summary,
                detectedLicensesChart,
                detectedLicensesFilter
            }
        };
    }
    case 'SUMMARY::CHANGE_ISSUES_TABLE': {
        const {
            issuesFilter
        } = action.payload;

        return {
            ...state,
            summary: {
                ...state.summary,
                issuesFilter
            }
        };
    }
    case 'SUMMARY::CHANGE_RULE_VIOLATIONS_TABLE': {
        const {
            ruleViolationsFilter
        } = action.payload;

        return {
            ...state,
            summary: {
                ...state.summary,
                ruleViolationsFilter
            }
        };
    }
    case 'TABLE::CHANGE_PACKAGES_TABLE': {
        const { filter, filterData } = action.payload;

        return {
            ...state,
            table: {
                ...state.table,
                filter,
                filterData
            }
        };
    }
    case 'TABLE::CLEAR_FILTERS_TABLE': {
        return {
            ...state,
            table: {
                ...state.table,
                filter: {
                    filteredInfo: {},
                    sortedInfo: {}
                },
                filterData: []
            }
        };
    }
    case 'TREE::DRAWER_CLOSE':
    case 'TREE::DRAWER_OPEN': {
        return {
            ...state,
            tree: {
                ...state.tree,
                showDrawer: !state.tree.showDrawer
            }
        };
    }
    case 'TREE::NODE_EXPAND': {
        return {
            ...state,
            tree: {
                ...state.tree,
                autoExpandParent: false,
                expandedKeys: action.expandedKeys
            }
        };
    }
    case 'TREE::NODE_SELECT': {
        const { selectedKeys, selectedWebAppTreeNode } = action.payload;

        if (selectedWebAppTreeNode) {
            return {
                ...state,
                tree: {
                    ...state.tree,
                    selectedWebAppTreeNode: selectedWebAppTreeNode || state.tree.selectedWebAppTreeNode,
                    selectedKeys,
                    showDrawer: true
                }
            };
        }

        return {
            ...state,
            tree: {
                ...state.tree,
                selectedKeys,
                showDrawer: true
            }
        };
    }
    case 'TREE::SEARCH': {
        const { expandedKeys, matchedKeys, searchValue } = action.payload;

        return {
            ...state,
            tree: {
                ...state.tree,
                autoExpandParent: true,
                expandedKeys,
                matchedKeys,
                searchIndex: 0,
                searchValue,
                selectedWebAppTreeNode: null,
                selectedKeys: matchedKeys.length > 0 ? [matchedKeys[0]] : [],
                showDrawer: false
            }
        };
    }
    case 'TREE::SEARCH_NEXT_MATCH': {
        const { tree: { searchIndex, matchedKeys } } = state;
        const index = searchIndex + 1 > matchedKeys.length - 1 ? 0 : searchIndex + 1;

        return {
            ...state,
            tree: {
                ...state.tree,
                searchIndex: index,
                selectedKeys: [matchedKeys[index]]
            }
        };
    }
    case 'TREE::SEARCH_PREVIOUS_MATCH': {
        const { tree: { searchIndex, matchedKeys } } = state;
        const index = searchIndex - 1 < 0 ? matchedKeys.length - 1 : searchIndex - 1;

        return {
            ...state,
            tree: {
                ...state.tree,
                searchIndex: index,
                selectedKeys: [matchedKeys[index]]
            }
        };
    }
    default:
        return state;
    }
};
