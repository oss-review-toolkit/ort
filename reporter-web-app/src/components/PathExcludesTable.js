/*
 * Copyright (C) 2020 HERE Europe B.V.
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
import { Table } from 'antd';

// Generates the HTML to display webAppPathExclude(s) as a Table
const PathExcludesTable = (props) => {
    const { excludes } = props;
    const columns = [
        {
            dataIndex: 'reason',
            key: 'reason',
            title: 'Reason'
        },
        {
            dataIndex: 'pattern',
            key: 'pattern',
            title: 'Pattern'
        },
        {
            dataIndex: 'comment',
            key: 'comment',
            textWrap: 'word-break',
            title: 'Comment',
            width: '50%'
        }
    ];

    return (
        <Table
            columns={columns}
            dataSource={excludes}
            locale={{
                emptyText: 'No path excludes'
            }}
            pagination={
                {
                    defaultPageSize: 50,
                    hideOnSinglePage: true,
                    pageSizeOptions: ['50', '100', '250', '500'],
                    position: 'bottom',
                    showQuickJumper: true,
                    showSizeChanger: true,
                    showTotal: (total, range) => `${range[0]}-${range[1]} of ${total} results`
                }
            }
            rowKey="key"
            size="small"
        />
    );
};

PathExcludesTable.propTypes = {
    excludes: PropTypes.array.isRequired
};

export default PathExcludesTable;
