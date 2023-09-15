/*
 * Copyright (C) 2023 The ORT Project Authors (see <https://github.com/oss-review-toolkit/ort/blob/main/NOTICE>)
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

import React from 'react';
import clsx from 'clsx';
import styles from './styles.module.css';
import Link from "@docusaurus/Link";

const FeatureList = [
    {
        title: 'Analyzer',
        Svg: require('@site/static/img/analyzer.svg').default,
        link: 'docs/tools/analyzer',
        description: (
            <>
                Get the dependencies of your projects, supporting over a dozen different package managers.
            </>
        ),
    },
    {
        title: 'Downloader',
        Svg: require('@site/static/img/downloader.svg').default,
        link: 'docs/tools/downloader',
        description: (
            <>
                Download the source code of your dependencies from version control systems or source artifacts.
            </>
        ),
    },
    {
        title: 'Scanner',
        Svg: require('@site/static/img/scanner.svg').default,
        link: 'docs/tools/scanner',
        description: (
            <>
                Scan the source code using the supported license, copyright, and snippet scanners.
            </>
        ),
    },
    {
        title: 'Advisor',
        Svg: require('@site/static/img/advisor.svg').default,
        link: 'docs/tools/advisor',
        description: (
            <>
                Get the vulnerabilities of your dependencies from different providers.
            </>
        ),
    },
    {
        title: 'Evaluator',
        Svg: require('@site/static/img/evaluator.svg').default,
        link: 'docs/tools/evaluator',
        description: (
            <>
                Apply custom policy rules against the gathered data using Kotlin scripting.
            </>
        ),
    },
    {
        title: 'Reporter',
        Svg: require('@site/static/img/reporter.svg').default,
        link: 'docs/tools/reporter',
        description: (
            <>
                Generate visual reports, open source notices, SBOMs, and more.
            </>
        ),
    },
];

function Feature({Svg, title, link, description}) {
  return (
    <div className={clsx('col col--4')}>
      <div className="text--center">
        <Link to={link}>
          <Svg className={styles.featureSvg} role="img" />
        </Link>
      </div>
      <div className="text--center padding-horiz--md">
        <h3>{title}</h3>
        <p>{description}</p>
      </div>
    </div>
  );
}

export default function HomepageFeatures() {
  return (
    <section className={styles.features}>
      <div className="container">
        <div className="row">
          {FeatureList.map((props, idx) => (
            <Feature key={idx} {...props} />
          ))}
        </div>
      </div>
    </section>
  );
}
