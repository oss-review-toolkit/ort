/*
 * Copyright (C) 2017 The ORT Project Authors (see <https://github.com/oss-review-toolkit/ort/blob/main/NOTICE>)
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     https://www.apache.org/licenses/LICENSE-2.0
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

import {
    useMemo,
    useState
} from 'react';

import {
    FileAddOutlined,
    FileExcelOutlined
} from '@ant-design/icons';
import {
    Collapse,
    Drawer,
    Input,
    message,
    Tree
} from 'antd';

import PackageDetails from './PackageDetails';
import PackageFindingsTable from './PackageFindingsTable';
import PackageLicenses from './PackageLicenses';
import PackagePaths from './PackagePaths';
import PathExcludesTable from './PathExcludesTable';
import ScopeExcludesTable from './ScopeExcludesTable';

const { Search } = Input;

const ResultsTree = ({ webAppOrtResult }) => {
    const { dependencyTrees } = webAppOrtResult;

    const [expandedKeys, setExpandedKeys] = useState([]);
    const [selectedKeys, setSelectedKeys] = useState([]);
    const [searchValue, setSearchValue] = useState('');
    const [autoExpandParent, setAutoExpandParent] = useState(true);
    const [selectedWebAppTreeNode, setSelectedWebAppTreeNode] = useState(null);
    const [drawerOpen, setDrawerOpen] = useState(false);
    const handleSearchChange = (e) => {
        e.stopPropagation();

        const { value } = e.target;
        const searchValue = value.trim();
        const searchPackageTreeNodeKeys = (searchValue === '')
            ? []
            : webAppOrtResult.packages
                .reduce((acc, webAppPackage) => {
                    if (webAppPackage.id.indexOf(searchValue) > -1) {
                        const treeNodeKeys = webAppOrtResult.getTreeNodeParentKeysByIndex(webAppPackage.packageIndex);
                        if (treeNodeKeys) {
                            acc.push(treeNodeKeys);
                        }
                    }

                    return acc;
                }, []);

        const newExpandedKeys = Array.from(searchPackageTreeNodeKeys
            .reduce(
                (acc, treeNodeKeys) => new Set([...acc, ...treeNodeKeys.parentKeys]),
                new Set()
            ));
        const newMatchedKeys = Array.from(searchPackageTreeNodeKeys
            .reduce(
                (acc, treeNodeKeys) => new Set([...acc, ...treeNodeKeys.keys]),
                new Set()
            ));

        if (newMatchedKeys.length === 0 && searchValue !== '') {
            message.info(`No search results found for '${searchValue}'`);
        }

        setExpandedKeys(newExpandedKeys);
        setSearchValue(value);
        setAutoExpandParent(true);
        setSelectedWebAppTreeNode(null);
        setDrawerOpen(false);
    };
    const handleSelectTreeNode = (selectedKeys) => {
        if (selectedKeys[0]) {
            const treeNode = webAppOrtResult.getTreeNodeByKey(selectedKeys[0]);
            if (treeNode) {
                setSelectedWebAppTreeNode(treeNode);
                setDrawerOpen(true);
            }
        }
    };
    const handleTreeExpand = (newExpandedKeys) => {
        setExpandedKeys(newExpandedKeys);
        setAutoExpandParent(false);
    };
    const treeData = useMemo(() => {
        const loop = (data) =>
            data.map((item) => {
                const strTitle = item.title;
                const index = strTitle.indexOf(searchValue);
                const beforeStr = strTitle.substring(0, index);
                const afterStr = strTitle.slice(index + searchValue.length);
                const title =
                    index > -1 && searchValue !== ''
                        ? (
                            <span
                                key={item.key}
                                style={{ color: '#1677ff' }}
                            >
                                {beforeStr}
                                <b>{searchValue}</b>
                                {afterStr}
                            </span>
                            )
                        : (
                            <span
                                key={item.key}
                                className={`ort-${item.isExcluded ? 'excluded' : 'included'}`}
                            >
                                {strTitle}
                            </span>
                            );
                if (item.children) {
                    return {
                        title,
                        key: item.key,
                        children: loop(item.children)
                    };
                }
                return {
                    title,
                    key: item.key
                };
            });
        return loop(dependencyTrees);
    }, [searchValue]);

    return (
        <div className="ort-tree">
            <div className="ort-tree-search">
                <Search
                    placeholder="Search package id and press Enter"
                    style={{
                        marginBottom: 8
                    }}
                    onPressEnter={handleSearchChange}
                />
            </div>
            <div className="ort-tree-wrapper">
                <Tree
                    autoExpandParent={autoExpandParent}
                    expandedKeys={expandedKeys}
                    selectedKeys={selectedKeys}
                    showLine={true}
                    treeData={treeData}
                    onExpand={handleTreeExpand}
                    onSelect={handleSelectTreeNode}
                />
            </div>
            <div>
                {
                    !!selectedWebAppTreeNode && <div className="ort-tree-drawer">
                            <Drawer
                                placement="right"
                                closable={true}
                                width="65%"
                                open={drawerOpen}
                                title={
                                    (() => {
                                        const selectedWebAppPackage = selectedWebAppTreeNode.package;

                                        if (webAppOrtResult.hasExcludes()) {
                                            if (selectedWebAppPackage.isExcluded) {
                                                return (
                                                    <span>
                                                        <FileExcelOutlined
                                                            className="ort-excluded"
                                                        />
                                                        {' '}
                                                        {selectedWebAppPackage.id}
                                                    </span>
                                                );
                                            }

                                            return (
                                                <span>
                                                    <FileAddOutlined />
                                                    {' '}
                                                    {selectedWebAppPackage.id}
                                                </span>
                                            );
                                        }

                                        return (
                                            <span>
                                                {selectedWebAppPackage.id}
                                            </span>
                                        );
                                    })()
                                }
                                onClose={ () => {
                                    setDrawerOpen(false);
                                    setSelectedWebAppTreeNode(null);
                                    setSelectedKeys([]);
                                }}
                            >
                                <Collapse
                                    className="ort-package-collapse"
                                    bordered={false}
                                    defaultActiveKey={[0, 1]}
                                    items={(() => {
                                        const selectedWebAppPackage = selectedWebAppTreeNode.package;

                                        const collapseItems = [
                                            {
                                                label: 'Details',
                                                key: 'package-details',
                                                children: (
                                                    <PackageDetails webAppPackage={selectedWebAppPackage} />
                                                )
                                            }
                                        ];

                                        if (selectedWebAppPackage.hasLicenses()) {
                                            collapseItems.push({
                                                label: 'Licenses',
                                                key: 'package-licenses',
                                                children: (
                                                    <PackageLicenses webAppPackage={selectedWebAppPackage} />
                                                )
                                            });
                                        }

                                        if (selectedWebAppPackage.hasPaths()) {
                                            collapseItems.push({
                                                label: 'Paths',
                                                key: 'package-paths',
                                                children: (
                                                    <PackagePaths
                                                        paths={[selectedWebAppTreeNode.webAppPath]}
                                                    />
                                                )
                                            });
                                        }

                                        if (selectedWebAppPackage.hasFindings()) {
                                            collapseItems.push({
                                                label: 'Scan Results',
                                                key: 'package-scan-results',
                                                children: (
                                                    <PackageFindingsTable
                                                        webAppPackage={selectedWebAppPackage}
                                                    />
                                                )
                                            });
                                        }

                                        if (selectedWebAppPackage.hasPathExcludes()) {
                                            collapseItems.push({
                                                label: 'Path Excludes',
                                                key: 'package-path-excludes',
                                                children: (
                                                    <PathExcludesTable
                                                        excludes={selectedWebAppPackage.pathExcludes}
                                                    />
                                                )
                                            });
                                        }

                                        if (selectedWebAppPackage.hasScopeExcludes()) {
                                            collapseItems.push({
                                                label: 'Scope Excludes',
                                                key: 'package-scope-excludes',
                                                children: (
                                                    <ScopeExcludesTable
                                                        excludes={selectedWebAppPackage.scopeExcludes}
                                                    />
                                                )
                                            });
                                        }

                                        return collapseItems;
                                    })()}
                                />
                            </Drawer>
                        </div>
                }
            </div>
        </div>
    );
};

export default ResultsTree;
