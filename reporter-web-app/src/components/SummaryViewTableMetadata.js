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
import PropTypes from 'prop-types';
import ExpandablePanel from './ExpandablePanel';
import ExpandablePanelContent from './ExpandablePanelContent';
import ExpandablePanelTitle from './ExpandablePanelTitle';

// Generates the HTML to display metadata related to scanned project
const SummaryViewTableMetadata = (props) => {
    const { data } = props;

    if (Object.keys(data).length === 0) {
        return null;
    }

    return (
        <div className="ort-metadata-props">
            <ExpandablePanel key="ort-metadata-props">
                <ExpandablePanelTitle>Metadata</ExpandablePanelTitle>
                <ExpandablePanelContent>
                    <table>
                        <tbody>
                            {Object.entries(data).map(([key, value]) => {
                                if (value.length > 0) {
                                    if (value.startsWith('http')) {
                                        return (
                                            <tr key={`ort-metadata-${key}`}>
                                                <th>
                                                    {`${key}:`}
                                                </th>
                                                <td>
                                                    <a
                                                        href={value}
                                                        rel="noopener noreferrer"
                                                        target="_blank"
                                                    >
                                                        {value}
                                                    </a>
                                                </td>
                                            </tr>
                                        );
                                    }

                                    return (
                                        <tr key={`ort-metadata-${key}`}>
                                            <th>
                                                {`${key}:`}
                                            </th>
                                            <td>
                                                {value}
                                            </td>
                                        </tr>
                                    );
                                }

                                return null;
                            })}
                        </tbody>
                    </table>
                </ExpandablePanelContent>
            </ExpandablePanel>
        </div>
    );
};

SummaryViewTableMetadata.propTypes = {
    data: PropTypes.object.isRequired
};

export default SummaryViewTableMetadata;
