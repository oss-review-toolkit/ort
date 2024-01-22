// @ts-check
// Note: type annotations allow type checking and IDEs autocompletion

const {themes} = require('prism-react-renderer');
const lightCodeTheme = themes.github;
const darkCodeTheme = themes.dracula;

/** @type {import('@docusaurus/types').Config} */
const config = {
  title: 'OSS Review Toolkit',
  tagline: 'A suite of tools to automate software compliance checks.',
  favicon: 'img/favicon.ico',

  // Set the production url of your site here
  url: 'https://oss-review-toolkit.github.io',
  // Set the /<baseUrl>/ pathname under which your site is served
  // For GitHub pages deployment, it is often '/<projectName>/'
  baseUrl: '/ort/',

  // GitHub pages deployment config.
  // If you aren't using GitHub pages, you don't need these.
  organizationName: 'oss-review-toolkit', // Usually your GitHub org/user name.
  projectName: 'ort', // Usually your repo name.
  trailingSlash: false,

  onBrokenLinks: 'throw',
  onBrokenMarkdownLinks: 'throw',

  // Even if you don't use internalization, you can use this field to set useful
  // metadata like html lang. For example, if your site is Chinese, you may want
  // to replace "en" with "zh-Hans".
  i18n: {
    defaultLocale: 'en',
    locales: ['en'],
  },

  presets: [
    [
      'classic',
      /** @type {import('@docusaurus/preset-classic').Options} */
      ({
        docs: {
          routeBasePath: '/docs/',
          sidebarPath: require.resolve('./sidebars.js'),
          // Please change this to your repo.
          // Remove this to remove the "edit this page" links.
          editUrl:
            'https://github.com/oss-review-toolkit/ort/tree/main/website/',
        },
        blog: false,
        theme: {
          customCss: require.resolve('./src/css/custom.css'),
        },
      }),
    ],
  ],

  themeConfig:
    /** @type {import('@docusaurus/preset-classic').ThemeConfig} */
    ({
      // Replace with your project's social card
      image: 'img/ort.png',
      navbar: {
        title: 'OSS Review Toolkit',
        logo: {
          alt: 'OSS Review Toolkit',
          src: 'img/logo.svg',
        },
        items: [
          {
            type: 'docSidebar',
            sidebarId: 'docsSidebar',
            position: 'left',
            label: 'Docs',
          },
          {
            to: '/docs/getting-started/tutorial',
            label: 'Tutorial',
            position: 'left',
            activeBaseRegex: `/docs/`,
          },
          {
            href: 'https://github.com/oss-review-toolkit/ort',
            label: 'GitHub',
            position: 'right',
          },
        ],
      },
      footer: {
        style: 'dark',
        links: [
          {
            title: 'Docs',
            items: [
              {
                label: 'Docs',
                to: '/docs/intro',
              },
              {
                label: 'Tutorial',
                to: '/docs/getting-started/tutorial',
              },
              {
                label: 'Search',
                to: '/search',
              },
            ],
          },
          {
            title: 'Community',
            items: [
              {
                label: 'LinkedIn',
                href: 'https://www.linkedin.com/company/oss-review-toolkit',
              },
              {
                label: 'Slack',
                href: 'http://slack.oss-review-toolkit.org',
              },
            ],
          },
          {
            title: 'More',
            items: [
              {
                label: 'GitHub',
                href: 'https://github.com/oss-review-toolkit/ort',
              },
            ],
          },
        ],
        copyright: `Copyright © ${new Date().getFullYear()} The ORT Project Authors. Built with Docusaurus.`,
      },
      prism: {
        theme: lightCodeTheme,
        darkTheme: darkCodeTheme,
        additionalLanguages: ['bash', 'batch'],
      },
      algolia: {
        // The application ID provided by Algolia.
        appId: 'GBVAB0K8M8',

        // The public API key, safe to commit.
        apiKey: '4f04967a5a1f4b277d59d741a077affd',

        indexName: 'oss-review-toolkit',
        contextualSearch: true,
        searchPagePath: 'search',
      },
    }),
};

module.exports = config;
