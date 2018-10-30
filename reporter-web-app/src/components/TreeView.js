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
    Alert, Drawer, Tree, Input, Button, Row
} from 'antd';
import { connect } from 'react-redux';
import scrollIntoView from 'scroll-into-view-if-needed';
import PackageDetails from './PackageDetails';
import PackageErrors from './PackageErrors';
import PackageLicenses from './PackageLicenses';
import PackagePaths from './PackagePaths';
import PackageScansSummary from './PackageScansSummary';

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

        const { data, view } = props;
        const { report: reportData } = data;

        this.state = {
            data: {
                tree: this.createSingleTreeFromProjects(reportData),
                treeNodesList: this.createSingleTreeNodesListFromProjects(reportData)
            },
            view: {
                autoExpandParent: true,
                expandedKeys: [],
                matchedKeys: [],
                searchValue: '',
                searchIndex: 0,
                selectedPackage: null,
                selectedKeys: [],
                showDrawer: false,
                visible: view.activeTabKey === 'tree'
            }
        };
    }

    componentDidMount() {
        this.scrollIntoView();
    }

    componentWillReceiveProps(props) {
        const { data: propsData, view: propsView } = props;
        const { view: stateView } = this.state;

        if (propsView.activeTabKey === 'tree' || stateView.visible === true) {
            this.setState((prevState) => {
                const { report: reportData } = propsData;

                return {
                    ...prevState,
                    data: {
                        tree: this.createSingleTreeFromProjects(reportData),
                        treeNodesList: this.createSingleTreeNodesListFromProjects(reportData)
                    },
                    view: {
                        autoExpandParent: true,
                        expandedKeys: [],
                        matchedKeys: [],
                        searchIndex: 0,
                        searchValue: '',
                        selectedPackage: null,
                        selectedKeys: [],
                        showDrawer: false,
                        visible: propsView.activeTabKey === 'tree'
                    }
                };
            });
        }
    }

    shouldComponentUpdate() {
        const { view: stateView } = this.state;

        return stateView.visible === true;
    }

    componentDidUpdate() {
        this.scrollIntoView();
    }

    onChangeSearch = (e) => {
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
            .filter(item => item.id.indexOf(value) > -1);

        this.setState((prevState) => {
            const state = { ...prevState };

            state.view = {
                autoExpandParent: true,
                expandedKeys,
                matchedKeys,
                searchIndex: 0,
                searchValue: value,
                selectedPackage: null,
                selectedKeys: matchedKeys.length > 0 ? [matchedKeys[0].key] : [],
                showDrawer: false,
                visible: true
            };

            return state;
        });
    };

    onClickTreeNode = (selectedKeys, e) => {
        const { node: { props: { dataRef } } } = e;

        this.setState((prevState) => {
            const state = { ...prevState };

            state.view.selectedPackage = dataRef;
            state.view.selectedKeys = selectedKeys;
            state.view.showDrawer = true;

            return state;
        });
    }

    onClickExpandTreeNode = (expandedKeys) => {
        this.setState((prevState) => {
            const state = { ...prevState };

            state.view.autoExpandParent = false;
            state.view.expandedKeys = expandedKeys;

            return state;
        });
    };

    onPreviousClickSearch = (e) => {
        e.stopPropagation();

        const { view: { searchIndex, matchedKeys } } = this.state;
        const index = searchIndex - 1 < 0 ? matchedKeys.length - 1 : searchIndex - 1;

        this.scrollIntoView();
        this.setState((prevState) => {
            const state = { ...prevState };

            state.view.searchIndex = index;
            state.view.selectedKeys = [matchedKeys[index].key];

            return state;
        });
    }

    onNextClickSearch = (e) => {
        e.stopPropagation();

        const { view: { searchIndex, matchedKeys } } = this.state;
        const index = searchIndex + 1 > matchedKeys.length - 1 ? 0 : searchIndex + 1;

        this.scrollIntoView();

        this.setState((prevState) => {
            const state = { ...prevState };

            state.view.searchIndex = index;
            state.view.selectedKeys = [matchedKeys[index].key];

            return state;
        });
    }

    onDrawerClose = (e) => {
        e.stopPropagation();

        this.setState((prevState) => {
            const state = { ...prevState };

            state.view.showDrawer = false;

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
        const { view: { selectedKeys } } = this.state;

        if (selectedKeys.length === 0) {
            return;
        }

        const selectedElemId = `ort-tree-node-${selectedKeys[0]}`;
        const selectedElem = document.querySelector(`#${selectedElemId}`);

        if (selectedElem) {
            scrollIntoView(selectedElem, {
                scrollMode: 'if-needed',
                boundary: document.querySelector('.ort-tree-wrapper')
            });
        }
    }

    renderDrawer = () => {
        const {
            view: {
                selectedPackage,
                showDrawer
            }
        } = this.state;

        if (!showDrawer) {
            return null;
        }

        return (
            <Drawer
                title={selectedPackage.id}
                placement="right"
                closable
                onClose={this.onDrawerClose}
                mask={false}
                visible={showDrawer}
                width="60%"
            >
                <div>
                    <PackageDetails data={selectedPackage} show={false} />
                    <PackageErrors data={selectedPackage} show />
                    <PackageLicenses data={selectedPackage} show />
                    <PackagePaths data={selectedPackage} show />
                    <PackageScansSummary data={selectedPackage} show={false} />
                </div>
            </Drawer>
        );
    }

    renderTreeNode = data => data.map((item) => {
        const { view } = this.state;
        const { searchValue } = view;

        const index = item.id.indexOf(searchValue);
        const beforeSearchValueStr = item.id.substr(0, index);
        const afterSearchValueStr = item.id.substr(index + searchValue.length);
        let title;

        if (index > -1) {
            title = (
                <span id={`ort-tree-node-${item.key}`}>
                    {beforeSearchValueStr}
                    <span className="ort-tree-search-match">
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
                <TreeNode
                    className={item.key}
                    dataRef={item}
                    key={item.key}
                    title={title}
                >
                    {this.renderTreeNode(item.children)}
                </TreeNode>
            );
        }

        return (
            <TreeNode
                className={item.key}
                dataRef={item}
                key={item.key}
                title={title}
            />
        );
    });


    render() {
        const {
            data: { tree },
            view: {
                autoExpandParent,
                expandedKeys,
                matchedKeys,
                searchIndex,
                searchValue,
                selectedKeys
            }
        } = this.state;

        return (
            <div className="ort-tree">
                <div className="ort-tree-search">
                    <Search placeholder="Input search text and press Enter" onPressEnter={this.onChangeSearch} />
                    {
                        matchedKeys.length ? (
                            <Row
                                type="flex"
                                align="middle"
                                className="ort-tree-navigation"
                            >
                                <Button onClick={this.onNextClickSearch} icon="arrow-down" />
                                <Button onClick={this.onPreviousClickSearch} icon="arrow-up" />
                                <span className="ort-tree-navigation-counter">
                                    {searchIndex + 1}
                                    {' '}
                                    /
                                    {matchedKeys.length}
                                </span>
                            </Row>
                        ) : null
                    }
                </div>
                {
                    matchedKeys.length === 0 && searchValue !== '' && (
                        <Alert
                            message={`No packages found which name include '${searchValue}'`}
                            type="info"
                        />
                    )
                }
                <div className="ort-tree-wrapper">
                    <Tree
                        autoExpandParent={autoExpandParent}
                        expandedKeys={expandedKeys}
                        onExpand={this.onClickExpandTreeNode}
                        onSelect={this.onClickTreeNode}
                        showLine
                        selectedKeys={selectedKeys}
                    >
                        {this.renderTreeNode(tree)}
                    </Tree>
                </div>
                <div className="ort-tree-drawer">
                    {this.renderDrawer()}
                </div>
            </div>
        );
    }
}

TreeView.propTypes = {
    data: PropTypes.object.isRequired,
    view: PropTypes.object.isRequired
};

export default connect(
    state => state,
    () => ({}),
)(TreeView);
