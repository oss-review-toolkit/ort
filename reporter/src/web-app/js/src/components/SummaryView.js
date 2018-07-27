import React from 'react';
import { connect } from 'react-redux';
import {
    Alert, Col, Icon, Row, Table, Tabs, Timeline
} from 'antd';
import { LicenseChart } from './LicenseChart';
import { UNIQUE_COLORS } from '../data/colors/index';

const COLORS = UNIQUE_COLORS.data;
const TabPane = Tabs.TabPane;

class SummaryView extends React.Component {
    constructor(props) {
        super(props);
        this.state = {};
        this.licenseColors = new Map();

        const assignColorsToLicenses = (licenses) => {
            const nrColors = COLORS.length;

            for (let i = licenses.length - 1; i >= 0; i -= 1) {
                const license = licenses[i];
                if (!this.licenseColors.has(license)) {
                    this.licenseColors.set(license, COLORS[this.licenseColors.size % nrColors]);
                }
            }
        };
        const viewData = {
            charts: {
                declaredLicenses: [],
                detectedLicenses: [],
                totalDeclaredLicenses: 0,
                totalDetectedLicenses: 0
            },
            errors: {
                open: [],
                addressed: [],
                totalOpen: 0,
                totalResolved: 0
            }
        };

        if (props.reportData) {
            this.state = {
                ...this.state,
                data: props.reportData
            };
            const { data } = this.state;

            if (data.licenses
                && data.licenses.data
                && data.licenses.total) {
                if (data.licenses.data.detected
                    && Number.isInteger(data.licenses.total.detected)) {
                    assignColorsToLicenses(Object.keys(data.licenses.data.detected));
                    viewData.charts.detectedLicenses = this.convertLicensesToChartFormat(
                        data.licenses.data.detected
                    );
                    viewData.charts.totalDetectedLicenses = data.licenses.total.detected;
                }

                if (data.licenses.data.declared
                    && Number.isInteger(data.licenses.total.declared)) {
                    assignColorsToLicenses(Object.keys(data.licenses.data.declared));
                    viewData.charts.declaredLicenses = this.convertLicensesToChartFormat(
                        data.licenses.data.declared
                    );
                    viewData.charts.totalDeclaredLicenses = data.licenses.total.declared;
                }
            }

            if (data.errors
                && data.errors.data
                && data.errors.total) {
                if (data.errors.data.open
                    && Number.isInteger(data.errors.total.open)) {
                    viewData.errors.open = this.convertErrorsToTableFormat(data.errors.data.open);
                    viewData.errors.totalOpen = data.errors.total.open;
                }

                if (data.errors.data.addressed
                    && Number.isInteger(data.errors.total.addressed)) {
                    viewData.errors.addressed = this.convertErrorsToTableFormat(
                        data.errors.data.addressed
                    );
                    viewData.errors.totalAddressed = data.errors.total.addressed;
                }
            }
        }

        this.state = {
            ...this.state,
            viewData
        };
    }

    convertErrorsToTableFormat(errors) {
        return Object.values(errors).reduce((accumulator, error) => [...accumulator, ...error], []);
    }

    convertLicensesToChartFormat(licenses) {
        const chartData = Object.entries(licenses).reduce((accumulator, [key, value]) => {
            accumulator[key] = {
                name: key,
                value,
                color: this.licenseColors.get(key)
            };

            return accumulator;
        }, {});

        return Object.keys(chartData).sort().reduce((accumulator, key) => {
            accumulator.push(chartData[key]);

            return accumulator;
        }, []).reverse();
    }

    render() {
        const { data, viewData } = this.state;
        const nrDetectedLicenses = viewData.charts.totalDetectedLicenses;
        const nrDeclaredLicenses = viewData.charts.totalDeclaredLicenses;
        const nrErrors = viewData.errors.totalOpen;
        const SummaryErrors = () => {
            const renderErrorTable = (errors, pageSize) => (
                <Table
                    columns={[{
                        title: 'id',
                        dataIndex: 'id',
                        render: (text, row) => (
                            <div>
                                <dl>
                                    <dt>
                                        {row.package ? row.package.id : row.id}
                                    </dt>
                                    <dd>
                                        Dependency defined in
                                        {''}
                                        {row.file}
                                    </dd>
                                </dl>
                                <dl>
                                    <dd>
                                        {row.message}
                                    </dd>
                                </dl>
                            </div>
                        )
                    }]}
                    dataSource={errors}
                    locale={{
                        emptyText: 'No errors'
                    }}
                    pagination={{
                        hideOnSinglePage: true,
                        pageSize
                    }}
                    rowKey="code"
                    scroll={{
                        y: 300
                    }}
                    showHeader={false}
                />);

            if (viewData.errors.totalOpen !== 0) {
                return (
                    <Tabs tabPosition="top">
                        <TabPane
                            tab={(
                                <span>
                                    Errors (
                                        {viewData.errors.totalOpen}
                                    )
                                </span>
                            )}
                            key="1"
                        >
                            {renderErrorTable(viewData.errors.open, viewData.errors.totalOpen)}
                        </TabPane>
                        <TabPane
                            tab={(
                                <span>
                                    Addressed Errors (
                                        {viewData.errors.totalAddressed}
                                    )
                                </span>
                            )}
                            key="2"
                        >
                            {renderErrorTable(
                                viewData.errors.addressed,
                                viewData.errors.totalAddressed
                            )}
                        </TabPane>
                    </Tabs>
                );
            }

            // If return null to prevent React render error
            return null;
        };
        const SummaryLicenseCharts = () => {
            const renderDetectedLicenseTab = () => {
                if (nrDeclaredLicenses !== 0) {
                    return (
                        <TabPane
                            tab={(
                                <span>
                                    Detected licenses (
                                    {nrDetectedLicenses}
                                    )
                                </span>
                            )}
                            key="1"
                        >
                            <LicenseChart
                                label="Detected licenses"
                                licenses={viewData.charts.detectedLicenses}
                                width={800}
                                height={500}
                            />
                        </TabPane>
                    );
                }

                // If return empty span to prevent React render error
                return null;
            };

            return (
                <Tabs tabPosition="top">
                    {renderDetectedLicenseTab()}
                    <TabPane
                        tab={(
                            <span>
                                Declared licenses (
                                    {nrDeclaredLicenses}
                                )
                            </span>
                        )}
                        key="2"
                    >
                        <LicenseChart
                            label="Declared licenses"
                            licenses={viewData.charts.declaredLicenses}
                            width={800}
                            height={500}
                        />
                    </TabPane>
                </Tabs>
            );
        };
        const SummaryTimeline = () => {
            const nrLevels = data.levels.total || 'n/a';
            const nrPackages = data.packages.total || 'n/a';
            const nrProjects = data.projects.total || 'n/a';
            const nrScopes = data.scopes.total || 'n/a';
            const renderLicensesText = () => {
                if (nrDetectedLicenses === 0) {
                    return (
                        <span>
                            {' '}
                            Detected
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
                            Completed scan
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
                    <Timeline>
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

        return (
            <div className="ort-summary">
                <Row>
                    <Col span={22} offset={1}>
                        <SummaryTimeline />
                    </Col>
                </Row>
                <Row>
                    <Col span={22} offset={1}>
                        <SummaryErrors />
                    </Col>
                </Row>
                <Row>
                    <Col span={22} offset={1}>
                        <SummaryLicenseCharts />
                    </Col>
                </Row>
            </div>
        );
    }
}

export default connect(
    state => ({ reportData: state }),
    () => ({})
)(SummaryView);
