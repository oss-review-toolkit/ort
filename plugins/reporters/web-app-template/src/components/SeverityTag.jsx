/*
 * Copyright (C) 2025 The ORT Project Authors (see <https://github.com/oss-review-toolkit/ort/blob/main/NOTICE>)
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
    Tag,
    Tooltip
} from 'antd';

const SeverityTag = ({ severity, isResolved = false, tooltipText = '' }) => {
    const severityColors = {
        error: '#e53e3e',
        warning: '#f59e0b',
        hint: '#84b6eb'
    };

    return isResolved
        ? (
            <Tooltip
                    placement="right"
                    title={tooltipText}
            >
                <Tag
                    color="#b0c4de"
                >
                    <s>
                        {`${severity.charAt(0).toUpperCase()}${severity.slice(1)}`}
                    </s>
                </Tag>
            </Tooltip>
            )
        : (

            <Tag
                color={severityColors[severity]}
            >
                {`${severity.charAt(0).toUpperCase()}${severity.slice(1)}`}
            </Tag>
            );
};

export default SeverityTag;
