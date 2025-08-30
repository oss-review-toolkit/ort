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
import Link from '@docusaurus/Link';
import CommandBox from '@site/src/components/CommandBox';

const FeatureList = [
  {
    title: 'Analyzer',
    Svg: require('@site/static/img/analyzer.svg').default,
    link: 'docs/tools/analyzer',
    command: 'analyze',
    description: (
      <>
        <p>
          The <b>Analyzer</b> is a Software Composition Analysis (SCA) tool that
          identifies the dependencies of your projects, and gathers information
          about them, such as licenses, copyrights, and source code locations.
        </p>
        <ul>
          <li>
            Support for more than 20 package managers, including Bazel, Cargo,
            Gradle, Maven, npm, PIP, pnpm, Yarn, and{' '}
            <Link to="docs/tools/analyzer">many more</Link>.
          </li>
          <li>
            Works out-of-the-box with most project setups, no need for
            configuration changes or custom plugins.
          </li>
          <li>
            Support for package metadata curations, either{' '}
            <Link to="docs/configuration/package-curations">self-written</Link>{' '}
            or sourced from public repositories like{' '}
            <Link to="https://clearlydefined.io/">ClearlyDefined</Link>.
          </li>
          <li>
            Dependencies are identified by scope to easily separate build, test,
            and runtime dependencies.
          </li>
        </ul>
        <p>
          The Analyzer is the first step in the ORT toolchain, and its output is
          used by all other tools.
        </p>
      </>
    ),
  },
  {
    title: 'Downloader',
    Svg: require('@site/static/img/downloader.svg').default,
    link: 'docs/tools/downloader',
    command: 'download',
    description: (
      <>
        <p>
          The <b>Downloader</b> fetches the source code of your dependencies, so
          that it can be scanned for licenses, copyrights, and snippets.
        </p>
        <ul>
          <li>
            Supports fetching source code from various sources, including Git,
            Mercurial, SVN, and Git-Repo repositories, and source code
            artifacts.
          </li>
          <li>
            Can be used to build source code bundles to archive the source code
            of your dependencies.
          </li>
          <li>Supports recursive cloning of Git submodules.</li>
        </ul>
      </>
    ),
  },
  {
    title: 'Scanner',
    Svg: require('@site/static/img/scanner.svg').default,
    link: 'docs/tools/scanner',
    command: 'scan',
    description: (
      <>
        <p>
          The <b>Scanner</b> integrates third-party{' '}
          <Link to="docs/category/scanners">source code scanners</Link> to
          gather information about licenses, copyrights, and snippets in the
          source code of your projects and their dependencies.
        </p>
        <ul>
          <li>
            Automatically downloads the required source code, no need to run the{' '}
            <b>Downloader</b> manually.
          </li>
          <li>
            Scan results can be stored for later reuse to avoid re-scanning the
            same source code.
          </li>
          <li>
            Built-in and configurable mapping of arbitrary licenses to SPDX
            license IDs.
          </li>
        </ul>
      </>
    ),
  },
  {
    title: 'Advisor',
    Svg: require('@site/static/img/advisor.svg').default,
    link: 'docs/tools/advisor',
    command: 'advise',
    description: (
      <>
        <p>
          The <b>Advisor</b> integrates various vulnerability providers to
          gather information about known vulnerabilities in your dependencies.
        </p>
        <ul>
          <li>
            Support for several{' '}
            <Link to="docs/category/advisors">vulnerability providers</Link>,
            including OSV and VulnerableCode.
          </li>
          <li>
            Found vulnerabilities can be resolved if they do not apply to your
            project, to not clutter the vulnerability report.
          </li>
        </ul>
      </>
    ),
  },
  {
    title: 'Evaluator',
    Svg: require('@site/static/img/evaluator.svg').default,
    link: 'docs/tools/evaluator',
    command: 'evaluate',
    description: (
      <>
        <p>
          The <b>Evaluator</b> provides a scriptable rule engine to evaluate the
          gathered data against custom policy rules.
        </p>
        <ul>
          <li>
            Policy rules can use any data gathered by the ORT, including
            license, copyright, and vulnerability information.
          </li>
          <li>
            Provides an in-built rule set based on the{' '}
            <Link to="https://www.osadl.org/OSADL-Open-Source-License-Checklists.oss-compliance-lists.0.html">
              OSADL License Compatibility Matrix
            </Link>
            .
          </li>
          <li>Rule sets are implemented in Kotlin.</li>
        </ul>
      </>
    ),
  },
  {
    title: 'Reporter',
    Svg: require('@site/static/img/reporter.svg').default,
    link: 'docs/tools/reporter',
    command: 'report',
    description: (
      <>
        <p>
          The <b>Reporter</b> generates{' '}
          <Link to="docs/category/reporters">various reports</Link> based on the
          data gathered by the ORT toolchain.
        </p>
        <ul>
          <li>
            Generates CycloneDX and SPDX Software Bill of Materials (SBOM).
          </li>
          <li>
            Provides a template reporter based on{' '}
            <Link to="https://freemarker.apache.org/">Freemarker</Link> to
            generate custom reports in various formats, including HTML,
            Markdown, or PDF. Templates for notice files, disclosure documents,
            and vulnerability reports are included.
          </li>
          <li>
            Can build a{' '}
            <Link to="docs/plugins/reporters/WebApp">web application</Link> in a
            single HTML file to visualize the gathered data.
          </li>
        </ul>
      </>
    ),
  },
];

function Feature({ Svg, title, link, command, description, reverse }) {
  // Create an ID based on the tool title for linking.
  const toolId = title.toLowerCase();

  return (
    <div
      id={toolId}
      className={clsx(styles.feature, reverse && styles.featureReverse)}
    >
      <div className={styles.featureSvgContainer}>
        <Link to={link}>
          <Svg className={styles.featureSvg} role="img" />
        </Link>
      </div>
      <div className={styles.featureContent}>
        <h1>{title}</h1>
        {description}
        <CommandBox
          command={`docker run ghcr.io/oss-review-toolkit/ort ${command} --help`}
        />
      </div>
    </div>
  );
}

export default function Features() {
  return (
    <section className={styles.features}>
      <div className="row">
        {FeatureList.map((props, idx) => (
          <Feature key={idx} {...props} reverse={idx % 2 !== 0} />
        ))}
      </div>
    </section>
  );
}
