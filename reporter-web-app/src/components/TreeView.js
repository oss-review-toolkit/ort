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
import {
    getSingleTree,
    getSingleTreeNodes,
    getTreeView,
    getTreeViewShouldComponentUpdate
} from '../reducers/selectors';
import store from '../store';

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
    componentDidMount() {
        this.scrollIntoView();
    }

    shouldComponentUpdate() {
        const { shouldComponentUpdate } = this.props;
        return shouldComponentUpdate;
    }

    componentDidUpdate() {
        this.scrollIntoView();
    }

    onChangeSearch = (e) => {
        e.stopPropagation();

        const { value } = e.target;
        const { tree, treeNodes } = this.props;
        const expandedKeys = (value === '') ? [] : treeNodes
            .map((item) => {
                if (item.id.indexOf(value) > -1) {
                    return getParentKey(item.key, tree);
                }
                return null;
            })
            .filter((item, i, self) => item && self.indexOf(item) === i);
        const matchedKeys = (value === '') ? [] : treeNodes
            .filter(item => item.id.indexOf(value) > -1);

        store.dispatch({
            type: 'TREE::SEARCH',
            payload: {
                expandedKeys,
                matchedKeys,
                searchValue: value
            }
        });
    };

    onExpandTreeNode = (expandedKeys) => {
        store.dispatch({ type: 'TREE::NODE_EXPAND', expandedKeys });
    };

    onSelectTreeNode = (selectedKeys, e) => {
        const { node: { props: { dataRef } } } = e;

        store.dispatch({
            type: 'TREE::NODE_SELECT',
            payload: {
                selectedPackage: dataRef,
                selectedKeys
            }
        });
    }

    onClickPreviousSearchMatch = (e) => {
        e.stopPropagation();
        this.scrollIntoView();
        store.dispatch({ type: 'TREE::SEARCH_PREVIOUS_MATCH' });
    }

    onClickNextSearchMatch = (e) => {
        e.stopPropagation();
        this.scrollIntoView();
        store.dispatch({ type: 'TREE::SEARCH_NEXT_MATCH' });
    }

    onCloseDrawer = (e) => {
        e.stopPropagation();
        store.dispatch({ type: 'TREE::DRAWER_CLOSE' });
    }

    scrollIntoView = () => {
        const {
            treeView: {
                selectedKeys
            }
        } = this.props;

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
            treeView: {
                selectedPackage,
                showDrawer
            }
        } = this.props;

        if (!showDrawer) {
            return null;
        }

        return (
            <Drawer
                title={selectedPackage.id}
                placement="right"
                closable
                onClose={this.onCloseDrawer}
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
        const {
            treeView: {
                searchValue
            }
        } = this.props;

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
            tree,
            treeView: {
                autoExpandParent,
                expandedKeys,
                matchedKeys,
                searchIndex,
                searchValue,
                selectedKeys
            }
        } = this.props;

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
                                <Button onClick={this.onClickNextSearchMatch} icon="arrow-down" />
                                <Button onClick={this.onClickPreviousSearchMatch} icon="arrow-up" />
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
                            message={`No package names found which include '${searchValue}'`}
                            type="info"
                        />
                    )
                }
                <div className="ort-tree-wrapper">
                    <Tree
                        autoExpandParent={autoExpandParent}
                        expandedKeys={expandedKeys}
                        onExpand={this.onExpandTreeNode}
                        onSelect={this.onSelectTreeNode}
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
    shouldComponentUpdate: PropTypes.bool.isRequired,
    tree: PropTypes.array.isRequired,
    treeNodes: PropTypes.array.isRequired,
    treeView: PropTypes.object.isRequired
};

const mapStateToProps = state => ({
    shouldComponentUpdate: getTreeViewShouldComponentUpdate(state),
    tree: getSingleTree(state),
    treeNodes: getSingleTreeNodes(state),
    treeView: getTreeView(state)
});

export default connect(
    mapStateToProps,
    () => ({}),
)(TreeView);
