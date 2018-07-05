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
    Tree, Input, Tooltip, Button, Row,
} from 'antd';
import classNames from 'classnames';
import { connect } from 'react-redux';

const { TreeNode } = Tree;
const { Search } = Input;

class TreeView extends React.Component {
    static get propTypes() {
        return {
            reportData: PropTypes.any,
        };
    }

  treeNodes = {};

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
              expandedKeys: [],
              selectedIndex: 0,
              sorted: [],
          };
      }
  }

  onExpand = (expandedKeys) => {
      this.setState({
          expandedKeys,
          autoExpandParent: false,
      });
  }

  componentDidUpdate(_, prevState) {
      const { search } = this.state;

      if (prevState.search !== search) {
          this.calcSorted();
      }
  }

  calcSorted = () => {
      const { search } = this.state;
      const selectedNodes = search ? Object.keys(this.treeNodes).filter(node => this.treeNodes[node].name.indexOf(search) >= 0) : [];
      const sorted = selectedNodes.sort((a, b) => {
          const aIndexes = a.split('-');
          const bIndexes = b.split('-');
          let diff = 0;
          const maxIndex = Math.min(aIndexes.length, bIndexes.length);
          for (let i = 0; i < maxIndex; i++) {
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
              sorted,
          });
      }
  }

  scrollTo = (index) => {
      if (index && this.treeNodes[index].ref) {
          let el = ReactDOM.findDOMNode(this.treeNodes[index].ref);
          let top = el.offsetHeight / 2;
          while (el && el !== this.tree) {
              top += el.offsetTop;
              el = el.offsetParent;
          }
          top -= Math.round(this.tree.clientHeight / 2);
          window.setTimeout(() => {
              this.tree.scrollTop = top;
          }, 0);
      }
  }

  onSearchChange = (e) => {
      const { tree } = this.state;
      const searchVal = e.target.value;
      const reccurSearch = (arr, parent, search, index = '') => arr.reduce((acc, item, childIndex) => {
          const matches = item.name.indexOf(search) >= 0;
          let keys = [];
          if (item.children) {
              const prefix = index ? `${index}-${childIndex}` : childIndex;
              keys = reccurSearch(item.children, item, search, prefix);
          }
          if (matches) {
              keys.push(`${index}-${parent.id || parent.name}`);
          }
          return [...acc, ...keys];
      }, []);

      if (tree) {
          const keysToExpand = searchVal ? reccurSearch(tree, { key: 'root' }, searchVal) : [];
          this.setState({
              selectedIndex: 0,
              expandedKeys: keysToExpand,
              autoExpandParent: true,
              search: searchVal,
          });
      }
  }

  renderTreeNode = (treeLeaf, indexElem) => {
      const { name } = treeLeaf;
      const { search, sorted, selectedIndex } = this.state;

      let nameMapped = (
          <span>
              {name}
          </span>
      );

      if (search && name.indexOf(search) >= 0) {
          const index = name.indexOf(search);
          const pre = name.substring(0, index);
          const post = name.substring(index + search.length);
          const selected = `${indexElem}` === sorted[selectedIndex];
          nameMapped = (
              <span>
                  {pre}
                  <span
                      className={classNames({
                          'searched-item': !selected,
                          'selected-item': selected,
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
              key={`${indexElem}-${treeLeaf.id || treeLeaf.name}`}
              title={nameWithTooltip}
              ref={ref => this.treeNodes[`${indexElem}`] = {
                  ref,
                  name,
              }}
          >
              {treeLeaf.children.map((childLeaf, nodeIndex) => this.renderTreeNode(childLeaf, `${indexElem}-${nodeIndex}`))}
          </TreeNode>
      );
  }

  prev = () => {
      const { selectedIndex, sorted } = this.state;
      const index = selectedIndex - 1 < 0 ? sorted.length - 1 : selectedIndex - 1;
      this.scrollTo(sorted[index]);
      this.setState({
          selectedIndex: index,
      });
  }

  next = () => {
      const { selectedIndex, sorted } = this.state;
      const index = selectedIndex + 1 > sorted.length - 1 ? 0 : selectedIndex + 1;
      this.scrollTo(sorted[index]);
      this.setState({
          selectedIndex: index,
      });
  }

  render() {
      const {
          tree, expandedKeys, autoExpandParent, selectedIndex, sorted,
      } = this.state;
      return tree && (
          <div style={{ display: 'flex', flexDirection: 'column', overflow: 'hidden' }}>
              <div style={{ display: 'flex', marginBottom: 8 }}>
                  <Search placeholder="Search" onChange={this.onSearchChange} />
                  {
                      sorted.length ? (
                          <Row type="flex" align="middle" style={{ marginLeft: 8, flexWrap: 'nowrap' }}>
                              <Button onClick={this.next} icon="arrow-down" />
                              <Button onClick={this.prev} icon="arrow-up" />
                              <span style={{ marginLeft: 8 }}>
                                  {selectedIndex + 1}
/
                                  {sorted.length}
                              </span>
                          </Row>
                      ) : null
                  }
              </div>
              <div
                  ref={(treeContainer) => {
                      this.tree = treeContainer;
                      return treeContainer;
                  }}
                  style={{ overflow: 'auto', flex: 'auto', height: 'calc(100vh - 150px)' }}
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
