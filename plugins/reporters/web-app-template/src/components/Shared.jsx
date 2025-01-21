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

const getColumnSearchProps = (dataIndex, filteredValue, setFilteredValue) => ({
    filteredValue,
    filterDropdown: ({ setSelectedKeys, selectedKeys, confirm, clearFilters }) => (
        <div style={{ padding: 8 }}>
            <Input
                placeholder={`Search ${dataIndex}`}
                value={selectedKeys[0]}
                style={{ marginBottom: 8, display: 'block' }}
                onChange={(e) => setSelectedKeys(e.target.value ? [e.target.value] : [])}
                onPressEnter={() => confirm()}
            />
            <Space>
                <Button
                    type="primary"
                    icon={<SearchOutlined />}
                    size="small"
                    onClick={() => {
                        setFilteredValue(selectedKeys[0] ? [selectedKeys[0]] : []);
                        confirm();
                    }}
                >
                    Search
                </Button>
                <Button
                    size="small"
                    onClick={() => {
                        clearFilters && clearFilters();
                        setFilteredValue([]);
                    }}
                >
                    Reset
                </Button>
            </Space>
        </div>
    ),
    filterIcon: (filtered) => <SearchOutlined style={{ color: filtered ? '#1890ff' : undefined }} />,
    onFilter: (searchStr, record) => {
        const searchStrValues = searchStr.split(/[\s,]+/).map(item => item.trim().toLowerCase());
        let recordValue;

        if (Number.isInteger(record[dataIndex]) || Array.isArray(record[dataIndex])) {
            recordValue = record[dataIndex].toString().toLowerCase();
        } else if (record[dataIndex] instanceof Set) {
            recordValue = Array.from(record[dataIndex]).toString().toLowerCase();
        } else {
            recordValue = record[dataIndex].toLowerCase();
        }

        return !searchStrValues.every(value => !recordValue.includes(value));
    },
    render: (text) => text
});

export { getColumnSearchProps };
