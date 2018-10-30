import React from 'react';
import { connect } from 'react-redux';
import PropTypes from 'prop-types';
import {
    Col, Row
} from 'antd';
import SummaryViewLicenses from './SummaryViewLicenses';
import SummaryViewTableErrors from './SummaryViewTableErrors';
import SummaryViewTimeline from './SummaryViewTimeline';
import { UNIQUE_COLORS } from '../data/colors/index';

const COLORS = UNIQUE_COLORS.data;

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
        const view = {
            errors: {
                open: [],
                addressed: [],
                totalOpen: 0,
                totalResolved: 0
            },
            licenses: {
                declared: [],
                declaredChart: [],
                detected: [],
                detectedChart: [],
                declaredFilter: {},
                detectedFilter: {},
                totalDeclared: 0,
                totalDetected: 0
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
                    view.licenses.detected = this.convertLicensesToRenderFormat(
                        data.licenses.data.detected
                    );
                    view.licenses.detectedChart = view.licenses.detected;
                    view.licenses.totalDetected = data.licenses.total.detected;
                }

                if (data.licenses.data.declared
                    && Number.isInteger(data.licenses.total.declared)) {
                    assignColorsToLicenses(Object.keys(data.licenses.data.declared));
                    view.licenses.declared = this.convertLicensesToRenderFormat(
                        data.licenses.data.declared
                    );
                    view.licenses.declaredChart = view.licenses.declared;
                    view.licenses.totalDeclared = data.licenses.total.declared;
                }
            }

            if (data.errors
                && data.errors.data
                && data.errors.total) {
                if (data.errors.data.open
                    && Number.isInteger(data.errors.total.open)) {
                    view.errors.open = data.errors.data.open;
                    view.errors.totalOpen = data.errors.total.open;
                }

                if (data.errors.data.addressed
                    && Number.isInteger(data.errors.total.addressed)) {
                    view.errors.addressed = data.errors.data.addressed;
                    view.errors.totalAddressed = data.errors.total.addressed;
                }
            }
        }

        this.state = {
            ...this.state,
            view
        };

        this.onChangeDeclaredLicensesTable = this.onChangeDeclaredLicensesTable.bind(this);
        this.onChangeDetectedLicensesTable = this.onChangeDetectedLicensesTable.bind(this);
    }

    onChangeDeclaredLicensesTable(pagination, filters, sorter, extra) {
        this.setState((prevState) => {
            const state = { ...prevState };

            state.view.licenses.declaredChart = extra.currentDataSource;
            state.view.licenses.declaredFilter = {
                filteredInfo: filters,
                sortedInfo: sorter
            };

            return state;
        });
    }

    onChangeDetectedLicensesTable(pagination, filters, sorter, extra) {
        this.setState((prevState) => {
            const state = { ...prevState };

            state.view.licenses.detectedChart = extra.currentDataSource;
            state.view.licenses.detectedFilter = {
                filteredInfo: filters,
                sortedInfo: sorter
            };

            return state;
        });
    }

    convertLicensesToRenderFormat(licenses) {
        return Object.entries(licenses).reduce((accumulator, [key, value]) => {
            accumulator.push({
                name: key,
                value,
                color: this.licenseColors.get(key)
            });

            return accumulator;
        }, []);
    }

    render() {
        const { data, view } = this.state;

        return (
            <div className="ort-summary">
                <Row>
                    <Col span={22} offset={1}>
                        <SummaryViewTimeline data={{
                            ...data,
                            nrDetectedLicenses: view.licenses.totalDetected,
                            nrDeclaredLicenses: view.licenses.totalDeclared,
                            nrErrors: view.errors.totalOpen
                        }}
                        />
                    </Col>
                </Row>
                <Row>
                    <Col span={22} offset={1}>
                        <SummaryViewTableErrors data={view.errors} />
                    </Col>
                </Row>
                <Row>
                    <Col span={22} offset={1}>
                        <SummaryViewLicenses
                            data={view.licenses}
                            onChangeDeclaredLicensesTable={this.onChangeDeclaredLicensesTable}
                            onChangeDetectedLicensesTable={this.onChangeDetectedLicensesTable}
                        />
                    </Col>
                </Row>
            </div>
        );
    }
}

SummaryView.propTypes = {
    reportData: PropTypes.object.isRequired
};

export default connect(
    state => ({ reportData: state.data.report }),
    () => ({})
)(SummaryView);
