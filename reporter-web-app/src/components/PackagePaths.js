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

const { Step } = Steps;

// Generates the HTML for packages errors
const PackagePaths = (props) => {
    const { pkg, webAppOrtResult } = props;
    const { id, paths } = pkg;
    let grid;

    // Do not render anything if no dependency paths
    if (!pkg.hasPaths()) {
        return (
            <span>No paths for this package.</span>
        );
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
    } else {
        grid = {
            gutter: 16,
            xs: 1,
            sm: 2,
            md: 2,
            lg: 2,
            xl: 2,
            xxl: 2
        };
    }

    return (
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
            renderItem={path => (
                <List.Item>
                    <Steps progressDot direction="vertical" size="small" current={path.length}>
                        {
                            path.map(
                                (item, index) => {
                                    if (index === 0) {
                                        const description = (
                                            <div className="ort-step-props">
                                                <table>
                                                    <tbody>
                                                        <tr>
                                                            <th>
                                                                Defined in:
                                                            </th>
                                                            <td>
                                                                {
                                                                    (() => {
                                                                        const prj = webAppOrtResult
                                                                            .getProjectById(item.pkg);
                                                                        if (prj && prj.definitionFilePath) {
                                                                            return prj.definitionFilePath;
                                                                        }

                                                                        return 'unknown path';
                                                                    })()
                                                                }
                                                            </td>
                                                        </tr>
                                                        <tr>
                                                            <th>
                                                                Scope:
                                                            </th>
                                                            <td>
                                                                {item.scope}
                                                            </td>
                                                        </tr>
                                                    </tbody>
                                                </table>
                                            </div>
                                        );
                                        return (
                                            <Step
                                                key={item.pkg}
                                                title={item.pkg}
                                                description={description}
                                            />
                                        );
                                    }

                                    return (
                                        <Step
                                            key={item.pkg}
                                            title={item.pkg}
                                        />
                                    );
                                }
                            )
                        }
                        <Step key={id} title={id} />
                    </Steps>
                </List.Item>
            )}
        />
    );
};

PackagePaths.propTypes = {
    webAppOrtResult: PropTypes.object.isRequired,
    pkg: PropTypes.object.isRequired
};

export default PackagePaths;
