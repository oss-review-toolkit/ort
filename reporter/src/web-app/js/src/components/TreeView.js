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
import { Tree, Input, Tooltip } from 'antd';
import { connect } from 'react-redux';

const { TreeNode } = Tree;
const { Search } = Input;

class TreeView extends React.Component {
    static get propTypes() {
        return {
            reportData: PropTypes.any,
        };
    }

    static get defaultProps() {
        return {
            reportData: null,
        };
    }

    constructor(props) {
        super(props);
        this.state = { search: '' };
        if (props.reportData && props.reportData.tree) {
            this.state = {
                ...this.state,
                tree: props.reportData.tree,
                expandedKeys: []
            };
        }
    }

    onExpand = (expandedKeys) => {
        this.setState({
            expandedKeys,
            autoExpandParent: false,
        });
    }

    onSearchChange = (e) => {
        const { tree } = this.state;
        const searchVal = e.target.value;
        const reccurSearch = (arr, parent, search) => arr.reduce((acc, item) => {
            const matches = item.name.indexOf(search) >= 0;
            let keys = [];
            if (item.children) {
                keys = reccurSearch(item.children, item, search);
            }
            if (matches) {
                keys.push(parent.id || parent.name);
            }
            return [...acc, ...keys];
        }, []);
        if (tree) {
            const keysToExpand = searchVal ? reccurSearch(tree, { key: 'root' }, searchVal) : [];
            this.setState({
                expandedKeys: keysToExpand,
                autoExpandParent: true,
                search: searchVal,
            });
        }
    }

    renderTreeNode = (treeLeaf) => {
        const { name } = treeLeaf;
        const { search } = this.state;

        let nameMapped = (
            <span>
                {name}
            </span>
        );

        if (search && name.indexOf(search) >= 0) {
            const index = name.indexOf(search);
            const pre = name.substring(0, index);
            const post = name.substring(index + search.length);
            nameMapped = (
                <span>
                    {pre}
                    <span
                        style={{
                            color: 'red',
                        }}
                    >
                        {search}
                    </span>
                    {post}
                </span>
            );
        }

        const tooltip = (
            treeLeaf.path.length >= 1 && (
                <div className="tooltip-list">
                    <div class-name="tooltip-item">
                        {treeLeaf.path.slice(0).join(' / ')}
                    </div>
                </div>
            )
        );

        const nameWithTooltip = (
            <Tooltip overlayClassName="oss-tree-tooltip" placement="right" title={tooltip}>
                {nameMapped}
            </Tooltip>
        );

        return (
            <TreeNode
                key={treeLeaf.id || treeLeaf.name}
                title={nameWithTooltip}
            >
                {treeLeaf.children.map(childLeaf => this.renderTreeNode(childLeaf))}
            </TreeNode>
        );
    }

    render() {
        const { tree, expandedKeys, autoExpandParent } = this.state;
        return tree && (
            <div>
                <Search style={{ marginBottom: 8 }} placeholder="Search" onChange={this.onSearchChange} />
                <Tree
                    onExpand={this.onExpand}
                    showLine
                    autoExpandParent={autoExpandParent}
                    expandedKeys={expandedKeys}
                >
                    {tree.map(rootLeaf => this.renderTreeNode(rootLeaf))}
                </Tree>
            </div>
        );
    }
}

export default connect(
    state => ({ reportData: state }),
    () => ({}),
)(TreeView);
