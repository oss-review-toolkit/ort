/**
 * Creating a sidebar enables you to:
 - create an ordered group of docs
 - render a sidebar for each doc of that group
 - provide next/previous navigation

 The sidebars can be generated from the filesystem, or explicitly defined here.

 Create as many sidebars as you want.
 */

// @ts-check

/** @type {import('@docusaurus/plugin-content-docs').SidebarsConfig} */
const sidebars = {
  docsSidebar: [
    'introduction',
    {
      type: 'category',
      label: 'Getting Started',
      collapsible: true,
      collapsed: false,
      items: [
        'getting-started/installation',
        'getting-started/docker',
        'getting-started/ci-integrations',
        'getting-started/usage',
        'getting-started/development',
      ],
    },
    {
      type: 'category',
      label: 'Tutorials',
      items: [
        {
          type: 'category',
          label: 'Using ORT on your first project',
          link: {
            type: 'doc',
            id: 'tutorials/walkthrough/index',
          },
          items: [
            'tutorials/walkthrough/analyzing-a-project-for-dependencies',
            'tutorials/walkthrough/visualizing-results',
            'tutorials/walkthrough/scanning-for-copyrights-and-licenses',
            'tutorials/walkthrough/checking-for-vulnerabilities',
            'tutorials/walkthrough/running-policy-checks',
            'tutorials/walkthrough/generating-sboms',
          ],
        },
        'tutorials/adresssing-webapp-report-findings',
        'tutorials/automating-policy-checks',
      ],
    },
    {
      type: 'category',
      label: 'How-to guides',
      items: [
        'how-to-guides/how-to-exclude-dirs-files-or-scopes',
        'how-to-guides/how-to-include-dirs-and-files',
        'how-to-guides/how-to-generate-sboms',
        'how-to-guides/how-to-address-tool-issues',
        'how-to-guides/how-to-correct-licenses',
        'how-to-guides/how-to-correct-copyrights',
        'how-to-guides/how-to-make-a-license-choice',
        'how-to-guides/how-to-make-snippet-choices',
        'how-to-guides/how-to-address-a-license-policy-violation',
        'how-to-guides/how-to-define-package-sources',
        'how-to-guides/how-to-download-sources-for-projects-and-dependencies',
        'how-to-guides/how-to-handle-dependencies-without-sources',
        'how-to-guides/how-to-add-non-detected-or-supported-packages',
        'how-to-guides/how-to-classify-licenses',
        'how-to-guides/how-to-define-a-license',
        'how-to-guides/how-to-pass-external-information-to-ort',
        'how-to-guides/how-to-check-and-remediate-vulnerabilities-in-dependencies',
        'how-to-guides/how-to-authenticate-with-private-repositories',
      ],
    },
    {
      type: 'category',
      label: 'Reference',
      items: [
        {
          type: 'category',
          label: 'CLI Reference',
          link: {
            type: 'doc',
            id: 'reference/cli/index',
          },
          items: [
            {
              type: 'doc',
              id: 'reference/cli/analyzer',
              label: 'Analyzer',
            },
            {
              type: 'doc',
              id: 'reference/cli/advisor',
              label: 'Advisor',
            },
            {
              type: 'doc',
              id: 'reference/cli/downloader',
              label: 'Downloader',
            },
            {
              type: 'doc',
              id: 'reference/cli/scanner',
              label: 'Scanner',
            },
            {
              type: 'doc',
              id: 'reference/cli/evaluator',
              label: 'Evaluator',
            },
            {
              type: 'doc',
              id: 'reference/cli/reporter',
              label: 'Reporter',
            },
            {
              type: 'doc',
              id: 'reference/cli/notifier',
              label: 'Notifier',
            },
            {
              type: 'doc',
              id: 'reference/cli/orth',
              label: 'ORT Helper',
            },
          ],
        },
        {
          type: 'category',
          label: 'Configuration',
          link: {
            type: 'doc',
            id: 'reference/configuration/index',
          },
          items: [
            'reference/configuration/copyright-garbage',
            'reference/configuration/evaluator-rules',
            'reference/configuration/how-to-fix-text-provider',
            'reference/configuration/license-classifications',
            'reference/configuration/license-texts-inclusion',
            'reference/configuration/ort-yml',
            'reference/configuration/package-configurations',
            'reference/configuration/package-curations',
            'reference/configuration/reporter-templates',
            'reference/configuration/resolutions',
            'reference/configuration/snippet-choices',
          ],
        },
        {
          type: 'category',
          label: 'Plugins',
          items: [
            {
              type: 'category',
              label: 'Advisors',
              items: [
                {
                  type: 'autogenerated',
                  dirName: 'reference/plugins/advisors',
                },
              ],
            },
            {
              type: 'category',
              label: 'License Fact Providers',
              items: [
                {
                  type: 'autogenerated',
                  dirName: 'reference/plugins/license-fact-providers',
                },
              ],
            },
            {
              type: 'category',
              label: 'Package Configurtion Providers',
              items: [
                {
                  type: 'autogenerated',
                  dirName: 'reference/plugins/package-configuration-providers',
                },
              ],
            },
            {
              type: 'category',
              label: 'Package Curation Providers',
              items: [
                {
                  type: 'autogenerated',
                  dirName: 'reference/plugins/package-curation-providers',
                },
              ],
            },
            {
              type: 'category',
              label: 'Reporters',
              items: [
                {
                  type: 'autogenerated',
                  dirName: 'reference/plugins/reporters',
                },
              ],
            },
            {
              type: 'category',
              label: 'Scanners',
              items: [
                {
                  type: 'autogenerated',
                  dirName: 'reference/plugins/scanners',
                },
              ],
            },
          ],
        },
      ],
    },
    {
      type: 'category',
      label: 'Explanation',
      items: [
        'explanation/types-of-licenses',
        'explanation/license-clearance-strategies',
        'explanation/documentation-system',
      ],
    },
    {
      type: 'category',
      label: 'About the project',
      items: [
        {
          type: 'link',
          label: 'Contributing',
          href: 'https://github.com/oss-review-toolkit/.github/blob/main/CONTRIBUTING.md',
        },
        {
          type: 'link',
          label: 'Code of Conduct',
          href: 'https://github.com/oss-review-toolkit/.github/blob/main/CODE_OF_CONDUCT.md',
        },
        {
          type: 'link',
          label: 'Project Governance',
          href: 'https://github.com/oss-review-toolkit/ort-governance',
        },
        {
          type: 'link',
          label: 'Project Roadmap',
          href: 'https://github.com/orgs/oss-review-toolkit/projects/3/views/1',
        },
        {
          type: 'link',
          label: 'License',
          href: 'https://github.com/oss-review-toolkit/ort/blob/main/LICENSE',
        },
        'about/related-tools',
      ],
    },
  ],
};

module.exports = sidebars;
