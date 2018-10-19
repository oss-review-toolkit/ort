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
    Alert, Icon, Timeline
} from 'antd';
import SummaryViewTableMetadata from './SummaryViewTableMetadata';

// Generates the HTML to display timeline of findings related to scanned project
const SummaryViewTimeline = (props) => {
    const { data } = props;
    const {
        nrDeclaredLicenses,
        nrDetectedLicenses,
        nrErrors,
        levels,
        packages,
        projects,
        scopes
    } = data;

    const nrLevels = levels.total || 'n/a';
    const nrPackages = packages.total || 'n/a';
    const nrProjects = projects.total || 'n/a';
    const nrScopes = scopes.total || 'n/a';
    const renderLicensesText = () => {
        if (nrDetectedLicenses === 0) {
            return (
                <span>
                    {' '}
                    Detected
                    {' '}
                    <b>
                        {nrDeclaredLicenses}
                    </b>
                    {' '}
                    declared licenses
                </span>
            );
        }
        return (
            <span>
                Detected
                {' '}
                <b>
                    {nrDetectedLicenses}
                </b>
                {' '}
                licenses and
                {' '}
                <b>
                    {nrDeclaredLicenses}
                </b>
                {' '}
                declared licenses
            </span>
        );
    };
    const renderCompletedText = () => {
        if (nrErrors !== 0) {
            return (
                <span style={
                    { color: '#f5222d', fontSize: 18, lineHeight: '1.2' }
                }
                >
                    <b>
                        Completed scan with
                        {' '}
                        {nrErrors}
                        {' '}
                        errors
                    </b>
                </span>
            );
        }

        return (
            <span style={
                { color: '#52c41a', fontSize: 18, lineHeight: '1.2' }
            }
            >
                <b>
                    Completed scan successfully
                </b>
            </span>
        );
    };
    let vcs;

    if (data && data.vcs && data.vcs_processed) {
        vcs = {
            type: (data.vcs_processed.type || data.vcs.type || 'n/a'),
            revision: (data.vcs_processed.revision || data.vcs.revision || 'n/a'),
            url: (data.vcs_processed.url || data.vcs.url || 'n/a')
        };

        return (
            <Timeline className="ort-summary-timeline">
                <Timeline.Item>
                    Cloned revision
                    {' '}
                    <b>
                        {vcs.revision}
                    </b>
                    {' '}
                    of
                    {' '}
                    {vcs.type}
                    {' '}
                    repository
                    {' '}
                    <b>
                        {vcs.url}
                    </b>
                    <SummaryViewTableMetadata data={data.metadata} />
                </Timeline.Item>
                <Timeline.Item>
                    Found
                    {' '}
                    <b>
                        {nrProjects}
                    </b>
                    {' '}
                    files defining
                    {' '}
                    <b>
                        {nrPackages}
                    </b>
                    {' '}
                    unique dependencies within
                    {' '}
                    <b>
                        {nrScopes}
                    </b>
                    {' '}
                    scopes and
                    {' '}
                    <b>
                        {nrLevels}
                    </b>
                    {' '}
                    dependency levels
                </Timeline.Item>
                <Timeline.Item>
                    {renderLicensesText()}
                </Timeline.Item>
                <Timeline.Item
                    dot={(
                        <Icon
                            type={
                                (nrErrors !== 0) ? 'exclamation-circle-o' : 'check-circle-o'
                            }
                            style={
                                { fontSize: 16 }
                            }
                        />
                    )}
                    color={(nrErrors !== 0) ? 'red' : 'green'}
                >
                    {renderCompletedText()}
                </Timeline.Item>
            </Timeline>
        );
    }

    return (<Alert message="No repository information available" type="error" />);
};

SummaryViewTimeline.propTypes = {
    data: PropTypes.object.isRequired
};

export default SummaryViewTimeline;
