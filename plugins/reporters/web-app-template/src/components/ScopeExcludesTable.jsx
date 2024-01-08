/*
 * Copyright (C) 2020 The ORT Project Authors (see <https://github.com/oss-review-toolkit/ort/blob/main/NOTICE>)
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

import { Table } from 'antd';
import PropTypes from 'prop-types';

// Generates the HTML to display webAppScopeExclude(s) as a Table
const ScopeExcludesTable = ({ excludes }) => {
    const columns = [
        {
            dataIndex: 'reason',
            key: 'reason',
            title: 'Reason'
        },
        {
            dataIndex: 'name',
            key: 'name',
            title: 'Name'
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
            rowKey="key"
            size="small"
            locale={{
                emptyText: 'No path excludes'
            }}
            pagination={
                {
                    defaultPageSize: 50,
                    hideOnSinglePage: true,
                    pageSizeOptions: ['50', '100', '250', '500', '1000', '5000'],
                    position: 'bottom',
                    showQuickJumper: true,
                    showSizeChanger: true,
                    showTotal: (total, range) => `${range[0]}-${range[1]} of ${total} results`
                }
            }
        />
    );
};

ScopeExcludesTable.propTypes = {
    excludes: PropTypes.array.isRequired
};

export default ScopeExcludesTable;
