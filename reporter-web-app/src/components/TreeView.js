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

import React from 'react';
import PropTypes from 'prop-types';
import {
    Tree, Input, Button, Row
} from 'antd';
import { connect } from 'react-redux';
import scrollIntoView from 'scroll-into-view-if-needed';

const { TreeNode } = Tree;
const { Search } = Input;

const getParentKey = (key, tree) => {
    let parentKey;
    for (let i = 0; i < tree.length; i++) {
        const node = tree[i];
        if (node.children) {
            if (node.children.some(item => item.key === key)) {
                parentKey = node.key;
            } else if (getParentKey(key, node.children)) {
                parentKey = getParentKey(key, node.children);
            }
        }
    }
    return parentKey;
};

class TreeView extends React.Component {
    constructor(props) {
        super(props);

        this.state = {
            data: {
                tree: this.createSingleTreeFromProjects(props.reportData),
                treeNodesList: this.createSingleTreeNodesListFromProjects(props.reportData)
            },
            view: {
                autoExpandParent: true,
                expandedKeys: [],
                matchedKeys: [],
                searchValue: '',
                selectedIndex: 0
            }
        };
    }

    componentDidMount() {
        this.scrollIntoView();
    }

    componentDidUpdate() {
        this.scrollIntoView();
    }

    onExpand = (expandedKeys) => {
        this.setState((prevState) => {
            const state = { ...prevState };

            state.view.autoExpandParent = false;
            state.view.expandedKeys = expandedKeys;


            return state;
        });
    };

    onSearchChange = (e) => {
        e.stopPropagation();

        const { value } = e.target;
        const { data: { tree, treeNodesList } } = this.state;
        const expandedKeys = (value === '') ? [] : treeNodesList
            .map((item) => {
                if (item.id.indexOf(value) > -1) {
                    return getParentKey(item.key, tree);
                }
                return null;
            })
            .filter((item, i, self) => item && self.indexOf(item) === i);
        const matchedKeys = (value === '') ? [] : treeNodesList
            .filter(item => item.id.indexOf(value) > -1)
            .sort((a, b) => {
                const keyA = a.key; // ignore upper and lowercase
                const keyB = b.key; // ignore upper and lowercase
                if (keyA < keyB) {
                    return -1;
                }
                if (keyA > keyB) {
                    return 1;
                }

                return 0;
            });

        this.setState((prevState) => {
            const state = { ...prevState };

            state.view = {
                autoExpandParent: true,
                expandedKeys,
                matchedKeys,
                searchValue: value,
                selectedIndex: 0
            };

            return state;
        });
    };

    onSearchPreviousClick = (e) => {
        e.stopPropagation();

        const { view: { selectedIndex, expandedKeys } } = this.state;
        const index = selectedIndex - 1 < 0 ? expandedKeys.length - 1 : selectedIndex - 1;

        this.scrollIntoView();
        this.setState((prevState) => {
            const state = { ...prevState };

            state.view.selectedIndex = index;

            return state;
        });
    }

    onSearchNextClick = (e) => {
        e.stopPropagation();

        const { view: { selectedIndex, expandedKeys } } = this.state;
        const index = selectedIndex + 1 > expandedKeys.length - 1 ? 0 : selectedIndex + 1;

        this.scrollIntoView();

        this.setState((prevState) => {
            const state = { ...prevState };

            state.view.selectedIndex = index;

            return state;
        });
    }

    createSingleTreeFromProjects = (reportData) => {
        if (reportData && reportData.projects && reportData.projects.data) {
            return Object.values(reportData.projects.data).reduce((accumulator, project) => {
                if (project.packages && project.packages.tree) {
                    accumulator.push(project.packages.tree);
                }

                return accumulator;
            }, []);
        }

        return [];
    }

    createSingleTreeNodesListFromProjects = (reportData) => {
        if (reportData && reportData.projects && reportData.projects.data) {
            return Object.values(reportData.projects.data).reduce((accumulator, project) => {
                if (project.packages && project.packages.treeNodesList) {
                    return [...accumulator, ...project.packages.treeNodesList];
                }

                return accumulator;
            }, []);
        }

        return [];
    }

    scrollIntoView = () => {
        const { view: { expandedKeys, selectedIndex } } = this.state;

        if (expandedKeys.length === 0) {
            return;
        }

        const selectedElemId = `ort-tree-node-${expandedKeys[selectedIndex]}`;
        const selectedElem = document.querySelector(`#${selectedElemId}`);

        if (selectedElem) {
            scrollIntoView(selectedElem, {
                scrollMode: 'if-needed',
                boundary: document.querySelector('.ort-tree-wrapper')
            });
        }
    }

    renderTreeNode = data => data.map((item) => {
        const { view } = this.state;
        const {
            matchedKeys, searchValue, selectedIndex
        } = view;

        const index = item.id.indexOf(searchValue);
        const beforeSearchValueStr = item.id.substr(0, index);
        const afterSearchValueStr = item.id.substr(index + searchValue.length);
        let title;
        let selectedSearchValueClass = '';

        if (index > -1) {
            if (matchedKeys[selectedIndex] && item.key === matchedKeys[selectedIndex].key) {
                selectedSearchValueClass = 'selected';
            }

            title = (
                <span id={`ort-tree-node-${item.key}`}>
                    {beforeSearchValueStr}
                    <span className={`ort-tree-search-match ${selectedSearchValueClass}`}>
                        {searchValue}
                    </span>
                    {afterSearchValueStr}
                </span>
            );
        } else {
            title = (
                <span id={`ort-tree-node-${item.key}`}>{item.id}</span>
            );
        }
        if (item.children) {
            return (
                <TreeNode key={item.key} title={title}>
                    {this.renderTreeNode(item.children)}
                </TreeNode>
            );
        }
        return (
            <TreeNode
                key={item.key}
                title={title}
            />
        );
    });


    render() {
        const { data: { tree }, view: { autoExpandParent, expandedKeys, selectedIndex } } = this.state;

        return (
            <div className="ort-tree">
                <div className="ort-tree-search">
                    <Search placeholder="Input search text and press Enter" onPressEnter={this.onSearchChange} />
                    {
                        expandedKeys.length ? (
                            <Row
                                type="flex"
                                align="middle"
                                className="ort-tree-navigation"
                            >
                                <Button onClick={this.onSearchNextClick} icon="arrow-down" />
                                <Button onClick={this.onSearchPreviousClick} icon="arrow-up" />
                                <span className="ort-tree-navigation-counter">
                                    {selectedIndex + 1}
                                    {' '}
                                    /
                                    {expandedKeys.length}
                                </span>
                            </Row>
                        ) : null
                    }
                </div>
                <div className="ort-tree-wrapper">
                    <Tree
                        autoExpandParent={autoExpandParent}
                        expandedKeys={expandedKeys}
                        onExpand={this.onExpand}
                        showLine
                    >
                        {this.renderTreeNode(tree)}
                    </Tree>
                </div>
            </div>
        );
    }
}

TreeView.propTypes = {
    reportData: PropTypes.object.isRequired
};

export default connect(
    state => ({ reportData: state }),
    () => ({}),
)(TreeView);
