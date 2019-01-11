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
import {
    Alert, Icon, Timeline
} from 'antd';
import SummaryViewTableMetadata from './SummaryViewTableMetadata';

// Generates the HTML to display timeline of findings related to scanned project
const SummaryViewTimeline = (props) => {
    const {
        issues,
        levelsTotal = 'n/a',
        licenses,
        metadata,
        packagesTotal = 'n/a',
        projectsTotal = 'n/a',
        scopesTotal = 'n/a',
        repository
    } = props;

    const { errors, violations } = issues;

    if (repository && repository.vcs && repository.vcs_processed) {
        return (<Alert message="No repository information available" type="error" />);
    }

    const { openTotal: errorsOpenTotal } = errors;
    const { openTotal: violationsOpenTotal } = violations;
    const { declaredTotal: licensesDeclaredTotal, detectedTotal: licensesDetectedTotal } = licenses;
    const renderLicensesText = () => {
        if (licensesDetectedTotal === 0) {
            return (
                <span>
                    {' '}
                    Detected
                    {' '}
                    <b>
                        {licensesDeclaredTotal}
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
                    {licensesDetectedTotal}
                </b>
                {' '}
                licenses and
                {' '}
                <b>
                    {licensesDeclaredTotal}
                </b>
                {' '}
                declared licenses
            </span>
        );
    };
    const renderCompletedText = () => {
        if (errorsOpenTotal !== 0 && violationsOpenTotal === 0) {
            return (
                <span className="ort-issues-msg">
                    <b>
                        Completed scan with
                        {' '}
                        {errorsOpenTotal}
                        {' '}
                        error(s)
                    </b>
                </span>
            );
        }

        if (errorsOpenTotal === 0 && violationsOpenTotal !== 0) {
            return (
                <span className="ort-issues-msg">
                    <b>
                        Completed scan with
                        {' '}
                        {violationsOpenTotal}
                        {' '}
                        policy violation(s)
                    </b>
                </span>
            );
        }

        if (errorsOpenTotal !== 0 && violationsOpenTotal !== 0) {
            return (
                <span className="ort-issues-msg">
                    <b>
                        Completed scan with
                        {' '}
                        {errorsOpenTotal}
                        {' '}
                        error(s)
                        {' '}
                        and
                        {' '}
                        {violationsOpenTotal}
                        {' '}
                        policy violation(s)
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

    return (
        <Timeline className="ort-summary-timeline">
            <Timeline.Item>
                Cloned revision
                {' '}
                <b>
                    {repository.revision}
                </b>
                {' '}
                of
                {' '}
                {repository.type}
                {' '}
                repository
                {' '}
                <b>
                    {repository.url}
                </b>
                <SummaryViewTableMetadata data={metadata} />
            </Timeline.Item>
            <Timeline.Item>
                Found
                {' '}
                <b>
                    {projectsTotal}
                </b>
                {' '}
                files defining
                {' '}
                <b>
                    {packagesTotal}
                </b>
                {' '}
                unique dependencies within
                {' '}
                <b>
                    {scopesTotal}
                </b>
                {' '}
                scopes and
                {' '}
                <b>
                    {levelsTotal}
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
                            (errorsOpenTotal !== 0 || violationsOpenTotal !== 0)
                                ? 'exclamation-circle-o' : 'check-circle-o'
                        }
                        style={
                            { fontSize: 16 }
                        }
                    />
                )}
                color={(errorsOpenTotal !== 0 || violationsOpenTotal !== 0) ? 'red' : 'green'}
            >
                {renderCompletedText()}
            </Timeline.Item>
        </Timeline>
    );
};

SummaryViewTimeline.propTypes = {
    issues: PropTypes.object.isRequired,
    levelsTotal: PropTypes.number.isRequired,
    licenses: PropTypes.object.isRequired,
    metadata: PropTypes.object.isRequired,
    packagesTotal: PropTypes.number.isRequired,
    projectsTotal: PropTypes.number.isRequired,
    scopesTotal: PropTypes.number.isRequired,
    repository: PropTypes.object.isRequired
};

export default SummaryViewTimeline;
