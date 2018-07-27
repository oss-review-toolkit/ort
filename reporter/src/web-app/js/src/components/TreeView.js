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
import ReactDOM from 'react-dom';
import {
    Tree, Input, Tooltip, Button, Row
} from 'antd';
import classNames from 'classnames';
import { connect } from 'react-redux';

const { TreeNode } = Tree;
const { Search } = Input;

class TreeView extends React.Component {
    static get propTypes() {
        return {
            reportData: PropTypes.any
        };
    }

    static get defaultProps() {
        return {
            reportData: null
        };
    }

    treeNodes = {};

    constructor(props) {
        super(props);
        this.state = { search: '' };

        if (props.reportData) {
            this.state = {
                ...this.state,
                tree: this.createSingleTreeFromProjects(props.reportData),
                expandedKeys: [],
                selectedIndex: 0,
                sorted: []
            };
        }
    }

    componentDidUpdate(_, prevState) {
        const { search } = this.state;

        if (prevState.search !== search) {
            this.onSearchCalculateSorted();
        }
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

    onExpand = (expandedKeys) => {
        this.setState({
            expandedKeys,
            autoExpandParent: false
        });
    }

    onSearchChange = (e) => {
        const { tree } = this.state;
        const searchVal = e.target.value;
        const reccurSearch = (arr, parent, search, index = '') => arr.reduce((accumulator, item, childIndex) => {
            const matches = item.id.indexOf(search) >= 0;
            let keys = [];

            if (item.children) {
                keys = reccurSearch(
                    item.children,
                    item,
                    search,
                    index ? `${index}-${childIndex}` : childIndex
                );
            }

            if (matches) {
                keys.push(`${index}-${parent.id}`);
            }

            return [...accumulator, ...keys];
        }, []);

        if (tree) {
            this.setState({
                selectedIndex: 0,
                expandedKeys: searchVal ? reccurSearch(tree, { key: 'root' }, searchVal) : [],
                autoExpandParent: true,
                search: searchVal
            });
        }
    }

    onSearchPreviousClick = () => {
        const { selectedIndex, sorted } = this.state;
        const index = selectedIndex - 1 < 0 ? sorted.length - 1 : selectedIndex - 1;

        this.scrollTo(sorted[index]);
        this.setState({
            selectedIndex: index
        });
    }

    onSearchNextClick = () => {
        const { selectedIndex, sorted } = this.state;
        const index = selectedIndex + 1 > sorted.length - 1 ? 0 : selectedIndex + 1;

        this.scrollTo(sorted[index]);
        this.setState({
            selectedIndex: index
        });
    }

    onSearchCalculateSorted = () => {
        const { search } = this.state;
        const selectedNodes = search ? Object.keys(this.treeNodes).filter(
            node => this.treeNodes[node].id.indexOf(search) >= 0
        ) : [];
        const sorted = selectedNodes.sort((a, b) => {
            const aIndexes = a.split('-');
            const bIndexes = b.split('-');
            const maxIndex = Math.min(aIndexes.length, bIndexes.length);

            let diff = 0;

            for (let i = 0; i < maxIndex; i += 1) {
                diff += parseInt(aIndexes[i], 10) - parseInt(bIndexes[i], 10);
                if (diff !== 0) {
                    return diff;
                }
            }

            return aIndexes.length > bIndexes.length ? 1 : -1;
        });

        if (sorted) {
            this.scrollTo(sorted[0]);
            this.setState({
                sorted
            });
        }
    }

    scrollTo = (index) => {
        if (index && this.treeNodes[index].ref) {
            let elem = ReactDOM.findDOMNode(this.treeNodes[index].ref);
            let top = elem.offsetHeight / 2;

            while (elem && elem !== this.tree) {
                top += elem.offsetTop;
                elem = elem.offsetParent;
            }

            top -= Math.round(this.tree.clientHeight / 2);

            window.setTimeout(() => {
                this.tree.scrollTop = top;
            }, 0);
        }
    }

    renderTreeNode = (treeLeaf, indexElem) => {
        const { id } = treeLeaf;
        const { search, sorted, selectedIndex } = this.state;

        let idMapped = (
            <span>
                {id}
            </span>
        );

        if (search && id.indexOf(search) >= 0) {
            const index = id.indexOf(search);
            const pre = id.substring(0, index);
            const post = id.substring(index + search.length);
            const selected = `${indexElem}` === sorted[selectedIndex];

            idMapped = (
                <span>
                    {pre}
                    <span
                        className={classNames({
                            'ort-tree-searched-item': !selected,
                            'ort-tree-selected-item': selected
                        })}
                    >
                        {search}
                    </span>
                    {post}
                </span>
            );
        }

        const tooltip = (
            treeLeaf.path.length >= 1 && (
                <div className="ort-tooltip-list">
                    <div>
                        {treeLeaf.path.slice(0).join(' / ')}
                    </div>
                </div>
            )
        );

        const idWithTooltip = (
            <Tooltip overlayClassName="ort-tree-tooltip" placement="right" title={tooltip}>
                {idMapped}
            </Tooltip>
        );

        return (
            <TreeNode
                key={`${indexElem}-${treeLeaf.id}`}
                title={idWithTooltip}
                ref={ref => this.treeNodes[`${indexElem}`] = {
                    ref,
                    id
                }}
            >
                {treeLeaf.children.map((childLeaf, nodeIndex) => this.renderTreeNode(childLeaf, `${indexElem}-${nodeIndex}`))}
            </TreeNode>
        );
    }

    render() {
        const {
            tree, expandedKeys, autoExpandParent, selectedIndex, sorted
        } = this.state;

        return tree && (
            <div className="ort-tree-view">
                <div className="ort-tree-search">
                    <Search placeholder="Search" onChange={this.onSearchChange} />
                    {
                        sorted.length ? (
                            <Row type="flex" align="middle" style={{ marginLeft: 8, flexWrap: 'nowrap' }}>
                                <Button onClick={this.onSearchNextClick} icon="arrow-down" />
                                <Button onClick={this.onSearchPreviousClick} icon="arrow-up" />
                                <span className="ort-tree-navigation-counter">
                                    {selectedIndex + 1}
                                    {' '}
/
                                    {sorted.length}
                                </span>
                            </Row>
                        ) : null
                    }
                </div>
                <div
                    ref={treeContainer => this.tree = treeContainer}
                    className="ort-tree-wrapper"
                >
                    <Tree
                        onExpand={this.onExpand}
                        showLine
                        autoExpandParent={autoExpandParent}
                        expandedKeys={expandedKeys}
                    >
                        {tree.map((rootLeaf, index) => this.renderTreeNode(rootLeaf, index))}
                    </Tree>
                </div>
            </div>
        );
    }
}

export default connect(
    state => ({ reportData: state }),
    () => ({}),
)(TreeView);
