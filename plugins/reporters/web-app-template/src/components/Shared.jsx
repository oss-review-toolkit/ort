/*
 * Copyright (C) 2021 The ORT Project Authors (see <https://github.com/oss-review-toolkit/ort/blob/main/NOTICE>)
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
    SearchOutlined
} from '@ant-design/icons';
import {
    Button,
    Input,
    Space
} from 'antd';

// Implements a customized Ant Design table column search
const getColumnSearchProps = (dataIndex, filteredInfo, that) => ({
    filterDropdown: ({
        setSelectedKeys, selectedKeys, confirm, clearFilters
    }) => (
        <div style={{ padding: 8 }}>
            <Input
                placeholder="Search..."
                value={selectedKeys[0]}
                style={{ width: 188, marginBottom: 8, display: 'block' }}
                ref={(node) => {
                    that.searchInput = node;
                }}
                onChange={(e) => setSelectedKeys(e.target.value ? [e.target.value] : [])}
                onPressEnter={() => confirm()}
            />
            <Space>
                <Button
                    size="small"
                    style={{ width: 90 }}
                    onClick={() => clearFilters()}
                >
                    Reset
                </Button>
                <Button
                    icon={<SearchOutlined />}
                    size="small"
                    style={{ width: 90 }}
                    type="primary"
                    onClick={() => confirm()}
                >
                    Search
                </Button>
            </Space>
        </div>
    ),
    filterIcon: (filtered) => (<SearchOutlined style={{ color: filtered ? '#1890ff' : undefined }} />),
    filteredValue: filteredInfo[dataIndex] || null,
    onFilter: (value, record) => (record[dataIndex]
        ? record[dataIndex].toString().toLowerCase().includes(value.toLowerCase())
        : false),
    onFilterDropdownOpenChange: (visible) => {
        if (visible) {
            setTimeout(() => that.searchInput.select());
        }
    }
});

export { getColumnSearchProps };
