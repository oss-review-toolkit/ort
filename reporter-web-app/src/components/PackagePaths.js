/*
 * Copyright (C) 2017-2019 HERE Europe B.V.
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
import { List, Steps } from 'antd';
import PropTypes from 'prop-types';
import ExpandablePanel from './ExpandablePanel';
import ExpandablePanelContent from './ExpandablePanelContent';
import ExpandablePanelTitle from './ExpandablePanelTitle';

const { Step } = Steps;

// Generates the HTML for packages errors
const PackagePaths = (props) => {
    const { data, show } = props;
    const pkgObj = data;
    let grid = {
        gutter: 16,
        xs: 1,
        sm: 2,
        md: 2,
        lg: 2,
        xl: 2,
        xxl: 2
    };
    let paths = pkgObj.paths || pkgObj.path;
    let title = 'Package Dependency Path';

    // Do not render anything if no dependency paths
    if (Array.isArray(paths) && paths.length === 0) {
        return null;
    }

    // Transform from pkgObj's path from Tree single to Table's
    // multi paths object so we can use the same render code
    if (pkgObj.path) {
        paths = [{ path: pkgObj.path, scope: pkgObj.scope }];
    }

    if (paths.length > 1) {
        title += 's';
    }

    // Change layout grid to use all available width for single path
    if (paths.length === 1) {
        grid = {
            gutter: 16,
            xs: 1,
            sm: 1,
            md: 1,
            lg: 1,
            xl: 1,
            xxl: 1
        };
    }

    return (
        <ExpandablePanel key="ort-package-paths" show={show}>
            <ExpandablePanelTitle titleElem="h4">{title}</ExpandablePanelTitle>
            <ExpandablePanelContent>
                <List
                    grid={grid}
                    itemLayout="vertical"
                    size="small"
                    pagination={{
                        hideOnSinglePage: true,
                        pageSize: 2,
                        size: 'small'
                    }}
                    dataSource={paths}
                    renderItem={pathsItem => (
                        <List.Item>
                            <Steps progressDot direction="vertical" size="small" current={pathsItem.path.length + 1}>
                                {pathsItem.path.map(
                                    (item, index) => {
                                        if (index === 0) {
                                            const description = (
                                                <div className="ort-metadata-props">
                                                    <table>
                                                        <tbody>
                                                            <tr>
                                                                <th>
                                                                    Defined in:
                                                                </th>
                                                                <td>
                                                                    {pkgObj.definition_file_path}
                                                                </td>
                                                            </tr>
                                                            <tr>
                                                                <th>
                                                                    Scope:
                                                                </th>
                                                                <td>
                                                                    {pathsItem.scope}
                                                                </td>
                                                            </tr>
                                                        </tbody>
                                                    </table>
                                                </div>
                                            );
                                            return (<Step key={item} title={item} description={description} />);
                                        }

                                        return (<Step key={item} title={item} />);
                                    }
                                )}
                                <Step key={pkgObj.id} title={pkgObj.id} />
                            </Steps>
                        </List.Item>
                    )}
                />
            </ExpandablePanelContent>
        </ExpandablePanel>
    );
};

PackagePaths.propTypes = {
    data: PropTypes.object.isRequired,
    show: PropTypes.bool
};

PackagePaths.defaultProps = {
    show: false
};

export default PackagePaths;
