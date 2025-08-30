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
import Link from '@docusaurus/Link';
import useDocusaurusContext from '@docusaurus/useDocusaurusContext';
import Layout from '@theme/Layout';
import CommandBox from '@site/src/components/CommandBox';
import Features from '@site/src/components/Features';
import Toolchain from '@site/src/components/Toolchain';
import styles from './index.module.css';

function HomepageHeader() {
  const { siteConfig } = useDocusaurusContext();
  return (
    <header className={clsx('hero hero--primary', styles.heroBanner)}>
      <div className="container">
        <p className="text--center">
          <img src="img/ort.png" alt="ORT Logo" className={styles.heroLogo} />
        </p>
        <p className="hero__subtitle">
          {siteConfig.tagline}
          <br />
          Also available as a{' '}
          <Link to="https://eclipse-apoapsis.github.io/ort-server/">
            server
          </Link>
          .
        </p>
        <iframe
          src="https://ghbtns.com/github-btn.html?user=oss-review-toolkit&repo=ort&type=star&count=true&size=large"
          frameBorder="0"
          scrolling="0"
          width="170"
          height="30"
          title="GitHub"
        />
        <div className={styles.buttons}>
          <Link
            className="button button--secondary button--lg"
            to="/docs/intro"
          >
            Introduction
          </Link>
          <Link
            className="button button--secondary button--lg"
            to="/docs/getting-started/installation"
          >
            Getting Started
          </Link>
        </div>
        <CommandBox command="docker run ghcr.io/oss-review-toolkit/ort --help" />
      </div>
    </header>
  );
}

export default function Home() {
  const { siteConfig } = useDocusaurusContext();
  return (
    <Layout title={`${siteConfig.title}`} description={`${siteConfig.tagline}`}>
      <HomepageHeader />
      <main>
        <Toolchain />
        <Features />
      </main>
    </Layout>
  );
}
