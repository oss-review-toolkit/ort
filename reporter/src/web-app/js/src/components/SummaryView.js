import React from 'react';
import { connect } from 'react-redux';
import { Col, Row, Tabs } from 'antd';
import { LicenseChart } from './LicenseChart';
import { UNIQUE_COLORS } from '../data/colors/index';

const TabPane = Tabs.TabPane;
const COLORS = UNIQUE_COLORS.data

window.COLORS = UNIQUE_COLORS;

const data = [
    {name: 'Group A', value: 400, color: '#0088FE'},
    {name: 'Group B', value: 300, color: '#00C49F'},
    {name: 'Group C', value: 300, color: '#FFBB28'},
    {name: 'Group D', value: 200, color: '#FF8042'}
];

window.test = data;

class SummaryView extends React.Component {

    constructor(props) {
        super(props);
        this.state = {};

        if (props.reportData) {
            this.state = {
                ...this.state,
                data: props.reportData
            };

            if (this.state.data.levels && this.state.data.declaredLicenses) {
                this.state = {
                    ...this.state,
                    nrOfDeclaredLicenses: this.calculateNrOfDeclaredLicenses(this.state.data.declaredLicenses, this.state.data.levels)
                };
            }

            if (this.state.data.levels && this.state.data.detectedLicenses) {
                this.state = {
                    ...this.state,
                    nrOfDetectedLicenses: this.calculateNrOfDetectedLicenses(this.state.data.detectedLicenses, this.state.data.levels)
                };
            }
        }
    }

    calculateNrOfDeclaredLicenses(declaredLicenses = {}, projectsLevels = {}) {
        let licenses = {};

        for (let project in projectsLevels) {
            if (declaredLicenses.hasOwnProperty(project)) {
                for (let license in declaredLicenses[project]) {
                    if (!licenses[license]) {
                        licenses[license] = declaredLicenses[project][license].length;
                    } else {
                        licenses[license] = licenses[license] + declaredLicenses[project][license].length;
                    }
                }
            }
        }

        return Object.keys(licenses).map((licenseName, index) => { 
            return {
                name: licenseName,
                value: licenses[licenseName],
                color: COLORS[index]
            }
        });
    }

    calculateNrOfDetectedLicenses(detectedLicenses = {}, projectsLevels = {}) {
        let licenses = {};

        for (let project in projectsLevels) {
            if (detectedLicenses.hasOwnProperty(project)) {
                for (let license in detectedLicenses[project]) {
                    if (!licenses[license]) {
                        licenses[license] = detectedLicenses[project][license].length;
                    } else {
                        licenses[license] = licenses[license] + detectedLicenses[project][license].length;
                    }
                }
            }
        }

        return Object.keys(licenses).map((licenseName, index) => { 
            return {
                name: licenseName,
                value: licenses[licenseName],
                color: COLORS[index]
            }
        });
    }

    render() {
        const { data, nrOfDeclaredLicenses, nrOfDetectedLicenses } = this.state;
        return (
            <div className="oss-summary-view">
                <Row>
                  <Col span={22} offset={1}>
                    <h2>License Distribution</h2>
                        <Tabs tabPosition="top">
                            <TabPane tab={<span>Detected licenses ({nrOfDetectedLicenses.length})</span>} key="1">
                                <LicenseChart
                                    label="Detected licenses"
                                    licenses={nrOfDetectedLicenses}
                                    width={800}
                                    height={370}
                                />
                            </TabPane>
                            <TabPane tab={<span>Declared licenses ({nrOfDeclaredLicenses.length})</span>} key="2">
                                <LicenseChart
                                    label="Declared licenses"
                                    licenses={nrOfDeclaredLicenses}
                                    width={800}
                                    height={370}
                                />
                            </TabPane>
                        </Tabs>
                  </Col>
                </Row>
            </div>
        );
    }
}

export default connect(
    (state) => ({reportData: state}),
    () => ({})
)(SummaryView); 