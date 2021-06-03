/*
 * Copyright (C) 2021 HERE Europe B.V.
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

import {
    Button,
    Input,
    Space,
} from 'antd';
import {
    SearchOutlined
} from '@ant-design/icons';

/* eslint import/prefer-default-export: 0 */

// Implements a customized Ant Design table column search
const getColumnSearchProps = (dataIndex, filteredInfo, that) => ({
    filterDropdown: ({
        setSelectedKeys, selectedKeys, confirm, clearFilters
    }) => (
        <div style={{ padding: 8 }}>
            <Input
                ref={(node) => {
                    that.searchInput = node;
                }}
                placeholder="Search..."
                value={selectedKeys[0]}
                onChange={(e) => setSelectedKeys(e.target.value ? [e.target.value] : [])}
                onPressEnter={() => confirm()}
                style={{ width: 188, marginBottom: 8, display: 'block' }}
            />
            <Space>
                <Button
                    onClick={() => clearFilters()}
                    size="small"
                    style={{ width: 90 }}
                >
                    Reset
                </Button>
                <Button
                    icon={<SearchOutlined />}
                    onClick={() => confirm()}
                    size="small"
                    style={{ width: 90 }}
                    type="primary"
                >
                    Search
                </Button>
            </Space>
        </div>
    ),
    filterIcon: (filtered) => <SearchOutlined style={{ color: filtered ? '#1890ff' : undefined }} />,
    filteredValue: filteredInfo ? filteredInfo[dataIndex] : '',
    onFilter: (value, record) => (record[dataIndex]
        ? record[dataIndex].toString().toLowerCase().includes(value.toLowerCase())
        : false),
    onFilterDropdownVisibleChange: (visible) => {
        if (visible) {
            setTimeout(() => that.searchInput.select());
        }
    }
});

export { getColumnSearchProps };
