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

// Generates the HTML to display timeline of findings related to scanned project
const SummaryViewTimeline = (props) => {
    const { webAppOrtResult } = props;
    const {
        declaredLicenses,
        detectedLicenses,
        errors,
        levels,
        packagesMap,
        projectsMap,
        repository: { vcsProcessed },
        scopes,
        violations
    } = webAppOrtResult;
    const { revision, type, url } = vcsProcessed;
    const hasErrors = webAppOrtResult.hasErrors();
    const hasViolations = webAppOrtResult.hasViolations();

    if (!revision || !type || !url) {
        return (<Alert message="No repository information available" type="error" />);
    }

    return (
        <Timeline className="ort-summary-timeline">
            <Timeline.Item>
                Cloned revision
                {' '}
                <b>
                    {revision}
                </b>
                {' '}
                of
                {' '}
                {type}
                {' '}
                repository
                {' '}
                <b>
                    {url}
                </b>
            </Timeline.Item>
            <Timeline.Item>
                Found
                {' '}
                <b>
                    {projectsMap.size}
                </b>
                {' '}
                files defining
                {' '}
                <b>
                    {packagesMap.size}
                </b>
                {' '}
                unique dependencies within
                {' '}
                <b>
                    {scopes.length}
                </b>
                {' '}
                scopes and
                {' '}
                <b>
                    {levels.length}
                </b>
                {' '}
                dependency levels
            </Timeline.Item>
            <Timeline.Item>
                {
                    detectedLicenses.length === 0
                    && (
                        <span>
                            {' '}
                            Detected
                            {' '}
                            <b>
                                {declaredLicenses.length}
                            </b>
                            {' '}
                            declared licenses
                        </span>
                    )
                }
                {
                    detectedLicenses.length !== 0
                    && (
                        <span>
                            Detected
                            {' '}
                            <b>
                                {detectedLicenses.length}
                            </b>
                            {' '}
                            licenses and
                            {' '}
                            <b>
                                {declaredLicenses.length}
                            </b>
                            {' '}
                            declared licenses
                        </span>
                    )
                }
            </Timeline.Item>
            <Timeline.Item
                dot={(
                    <Icon
                        type={
                            (hasErrors || hasViolations)
                                ? 'exclamation-circle-o' : 'check-circle-o'
                        }
                        style={
                            { fontSize: 16 }
                        }
                    />
                )}
                color={(hasErrors || hasViolations) ? 'red' : 'green'}
            >
                {
                    hasErrors && !hasViolations
                    && (
                        <span className="ort-error">
                            <b>
                                Completed scan with
                                {' '}
                                {errors.length}
                                {' '}
                                error
                                { errors.length > 1 && 's'}
                            </b>
                        </span>
                    )
                }
                {
                    !hasErrors && hasViolations
                    && (
                        <span className="ort-error">
                            <b>
                                Completed scan with
                                {' '}
                                {violations.length}
                                {' '}
                                policy violation
                                { violations.length > 1 && 's'}
                            </b>
                        </span>
                    )
                }
                {
                    hasErrors && hasViolations
                    && (
                        <span className="ort-error">
                            <b>
                                Completed scan with
                                {' '}
                                {errors.length}
                                {' '}
                                error
                                { errors.length > 1 && 's'}
                                {' '}
                                and
                                {' '}
                                {violations.length}
                                {' '}
                                policy violation
                                { violations.length > 1 && 's'}
                            </b>
                        </span>
                    )
                }
                {
                    !hasErrors && !hasViolations
                    && (
                        <span className="ort-success">
                            <b>
                                Completed scan successfully
                            </b>
                        </span>
                    )
                }
            </Timeline.Item>
        </Timeline>
    );
};

SummaryViewTimeline.propTypes = {
    webAppOrtResult: PropTypes.object.isRequired
};

export default SummaryViewTimeline;
