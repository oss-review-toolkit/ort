/*
 * Copyright (C) 2024 The ORT Project Authors (see <https://github.com/oss-review-toolkit/ort/blob/main/NOTICE>)
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

import eslintA11y from 'eslint-plugin-jsx-a11y';
import eslintImport from 'eslint-plugin-import';
import eslintImportConfig from 'eslint-plugin-import/config/recommended.js';
import eslintPlugin from '@eslint/js';
import eslintReact from 'eslint-plugin-react';
import eslintReactConfig from 'eslint-plugin-react/configs/recommended.js';
import globals from 'globals';

const filesToIgnore = [
    // default
    '**/.git',
    '**/node_modules/**',

    // configs
    '**/*.config.js',

    // build artifiacts
    '**/dist/**',
    '**/build/**',
];

const baseRules = {
    "max-len": ["error", { "code": 120 }],
    "no-var": "warn",
    "no-console": "error",
    "object-shorthand": ["warn", "properties"],
    "accessor-pairs": ["error", { "setWithoutGet": true, "enforceForClassMembers": true }],
    "array-bracket-spacing": ["error", "never"],
    "array-callback-return": ["error", {
        "allowImplicit": false,
        "checkForEach": false
    }],
    "arrow-spacing": ["error", { "before": true, "after": true }],
    "block-spacing": ["error", "always"],
    "brace-style": ["error", "1tbs", { "allowSingleLine": true }],
    "camelcase": ["error", {
        "allow": ["^UNSAFE_"],
        "properties": "never",
        "ignoreGlobals": true
    }],
    "comma-dangle": ["error", {
        "arrays": "never",
        "objects": "never",
        "imports": "never",
        "exports": "never",
        "functions": "never"
    }],
    "comma-spacing": ["error", { "before": false, "after": true }],
    "comma-style": ["error", "last"],
    "computed-property-spacing": ["error", "never", { "enforceForClassMembers": true }],
    "constructor-super": "error",
    "curly": ["error", "multi-line"],
    "default-case-last": "error",
    "dot-location": ["error", "property"],
    "dot-notation": ["error", { "allowKeywords": true }],
    "eol-last": "error",
    "eqeqeq": ["error", "always", { "null": "ignore" }],
    "func-call-spacing": ["error", "never"],
    "generator-star-spacing": ["error", { "before": true, "after": true }],
    "indent": ["error", 4, {
        "SwitchCase": 1,
        "VariableDeclarator": 1,
        "outerIIFEBody": 1,
        "MemberExpression": 1,
        "FunctionDeclaration": { "parameters": 1, "body": 1 },
        "FunctionExpression": { "parameters": 1, "body": 1 },
        "CallExpression": { "arguments": 1 },
        "ArrayExpression": 1,
        "ObjectExpression": 1,
        "ImportDeclaration": 1,
        "flatTernaryExpressions": false,
        "ignoreComments": false,
        "ignoredNodes": ["TemplateLiteral *", "JSXElement", "JSXElement > *", "JSXAttribute", "JSXIdentifier", "JSXNamespacedName", "JSXMemberExpression", "JSXSpreadAttribute", "JSXExpressionContainer", "JSXOpeningElement", "JSXClosingElement", "JSXFragment", "JSXOpeningFragment", "JSXClosingFragment", "JSXText", "JSXEmptyExpression", "JSXSpreadChild"],
        "offsetTernaryExpressions": true
    }],
    "key-spacing": ["error", { "beforeColon": false, "afterColon": true }],
    "keyword-spacing": ["error", { "before": true, "after": true }],
    "lines-between-class-members": ["error", "always", { "exceptAfterSingleLine": true }],
    "multiline-ternary": ["error", "always-multiline"],
    "new-cap": ["error", { "newIsCap": true, "capIsNew": false, "properties": true }],
    "new-parens": "error",
    "no-array-constructor": "error",
    "no-async-promise-executor": "error",
    "no-caller": "error",
    "no-case-declarations": "error",
    "no-class-assign": "error",
    "no-compare-neg-zero": "error",
    "no-cond-assign": "error",
    "no-const-assign": "error",
    "no-constant-condition": ["error", { "checkLoops": false }],
    "no-control-regex": "error",
    "no-debugger": "error",
    "no-delete-var": "error",
    "no-dupe-args": "error",
    "no-dupe-class-members": "error",
    "no-dupe-keys": "error",
    "no-duplicate-case": "error",
    "no-useless-backreference": "error",
    "no-empty": ["error", { "allowEmptyCatch": true }],
    "no-empty-character-class": "error",
    "no-empty-pattern": "error",
    "no-eval": "error",
    "no-ex-assign": "error",
    "no-extend-native": "error",
    "no-extra-bind": "error",
    "no-extra-boolean-cast": "error",
    "no-extra-parens": ["error", "functions"],
    "no-fallthrough": "error",
    "no-floating-decimal": "error",
    "no-func-assign": "error",
    "no-global-assign": "error",
    "no-implied-eval": "error",
    "no-import-assign": "error",
    "no-invalid-regexp": "error",
    "no-irregular-whitespace": "error",
    "no-iterator": "error",
    "no-labels": ["error", { "allowLoop": false, "allowSwitch": false }],
    "no-lone-blocks": "error",
    "no-loss-of-precision": "error",
    "no-misleading-character-class": "error",
    "no-prototype-builtins": "error",
    "no-useless-catch": "error",
    "no-mixed-operators": ["error", {
        "groups": [
            ["==", "!=", "===", "!==", ">", ">=", "<", "<="],
            ["&&", "||"],
            ["in", "instanceof"]
        ],
        "allowSamePrecedence": true
    }],
    "no-mixed-spaces-and-tabs": "error",
    "no-multi-spaces": "error",
    "no-multi-str": "error",
    "no-multiple-empty-lines": ["error", { "max": 1, "maxBOF": 0, "maxEOF": 0 }],
    "no-new": "error",
    "no-new-func": "error",
    "no-new-object": "error",
    "no-new-symbol": "error",
    "no-new-wrappers": "error",
    "no-obj-calls": "error",
    "no-octal": "error",
    "no-octal-escape": "error",
    "no-proto": "error",
    "no-redeclare": ["error", { "builtinGlobals": false }],
    "no-regex-spaces": "error",
    "no-return-assign": ["error", "except-parens"],
    "no-self-assign": ["error", { "props": true }],
    "no-self-compare": "error",
    "no-sequences": "error",
    "no-shadow-restricted-names": "error",
    "no-sparse-arrays": "error",
    "no-tabs": "error",
    "no-template-curly-in-string": "error",
    "no-this-before-super": "error",
    "no-throw-literal": "error",
    "no-trailing-spaces": "error",
    "no-undef": "error",
    "no-undef-init": "error",
    "no-unexpected-multiline": "error",
    "no-unmodified-loop-condition": "error",
    "no-unneeded-ternary": ["error", { "defaultAssignment": false }],
    "no-unreachable": "error",
    "no-unreachable-loop": "error",
    "no-unsafe-finally": "error",
    "no-unsafe-negation": "error",
    "no-unused-expressions": ["error", {
        "allowShortCircuit": true,
        "allowTernary": true,
        "allowTaggedTemplates": true
    }],
    "no-unused-vars": ["error", {
        "args": "none",
        "caughtErrors": "none",
        "ignoreRestSiblings": true,
        "vars": "all"
    }],
    "no-use-before-define": ["error", { "functions": false, "classes": false, "variables": false }],
    "no-useless-call": "error",
    "no-useless-computed-key": "error",
    "no-useless-constructor": 0,
    "no-useless-escape": "error",
    "no-useless-rename": "error",
    "no-useless-return": "error",
    "no-void": "error",
    "no-whitespace-before-property": "error",
    "no-with": "error",
    "object-curly-newline": ["error", { "multiline": true, "consistent": true }],
    "object-curly-spacing": ["error", "always"],
    "object-property-newline": ["error", { "allowMultiplePropertiesPerLine": true }],
    "one-var": ["error", { "initialized": "never" }],
    "operator-linebreak": [
        "error", "after",
        {
            "overrides": {
                "?": "before",
                ":": "before",
                "|>": "before",
                "||": "before",
                "&&": "before"
            }
        }
    ],
    "padded-blocks": ["error", { "blocks": "never", "switches": "never", "classes": "never" }],
    "prefer-const": ["error", { "destructuring": "all" }],
    "prefer-promise-reject-errors": "error",
    "prefer-regex-literals": ["error", { "disallowRedundantWrapping": true }],
    "quote-props": ["error", "as-needed"],
    "quotes": ["error", "single", { "avoidEscape": true, "allowTemplateLiterals": false }],
    "rest-spread-spacing": ["error", "never"],
    "semi": 0,
    "semi-spacing": ["error", { "before": false, "after": true }],
    "space-before-blocks": ["error", "always"],
    "space-before-function-paren": ["error", "never"],
    "space-in-parens": ["error", "never"],
    "space-infix-ops": "error",
    "space-unary-ops": ["error", { "words": true, "nonwords": false }],
    "spaced-comment": ["error", "always", {
        "line": { "markers": ["*package", "!", "/", ",", "="] },
        "block": { "balanced": true, "markers": ["*package", "!", ",", ":", "::", "flow-include"], "exceptions": ["*"] }
    }],
    "symbol-description": "error",
    "template-curly-spacing": ["error", "never"],
    "template-tag-spacing": ["error", "never"],
    "unicode-bom": ["error", "never"],
    "use-isnan": ["error", {
        "enforceForSwitchCase": true,
        "enforceForIndexOf": true
    }],
    "valid-typeof": ["error", { "requireStringLiterals": true }],
    "wrap-iife": ["error", "any", { "functionPrototypeMethods": true }],
    "yield-star-spacing": ["error", "both"],
    "yoda": ["error", "never"]
}

const importRules = {
    'import/named': 0,
    'import/default': 0,
    'import/no-duplicates': [
        2,
        {
            'prefer-inline': true,
            considerQueryString: true,
        },
    ],
    'import/namespace': 0,
    'import/no-cycle': 2,
    'import/no-empty-named-blocks': 2,
    'import/no-extraneous-dependencies': [
        2,
        {
            devDependencies: true,
        },
    ],
    "import/no-named-as-default": 0,
    'import/no-named-as-default-member': 0,
    'import/no-namespace': 2,
    'import/no-unresolved': 0,
    'import/no-useless-path-segments': [
        2,
        {
            noUselessIndex: true,
        },
    ],
    'import/order': [
        2,
        {
            groups: [
                'builtin',
                'external',
                'internal',
                'parent',
                'sibling',
                'index',
                'object',
                'type',
                'unknown'
            ],
            pathGroups: [
                {
                    pattern: 'react',
                    group: 'builtin',
                    position: 'before'
                },
                {
                    pattern: '**eslint-plugin-react**',
                    group: 'builtin',
                    position: 'after'
                },
                {
                    pattern: '**eslint-plugin-react**/**/*',
                    group: 'builtin',
                    position: 'after'
                },
                {
                    pattern: '**react**',
                    group: 'external',
                    position: 'after'
                },
                {
                    pattern: '@**/**react**',
                    group: 'external',
                    position: 'after'
                },
                {
                    pattern: '@/components/**/*',
                    group: 'internal',
                    position: 'after'
                }
            ],
            distinctGroup: true,
            pathGroupsExcludedImportTypes: ['react'],
            'newlines-between': 'always',
            alphabetize: {
                order: 'asc',
                orderImportKind: 'asc',
                caseInsensitive: false,
            }
        }
    ],
    'import/prefer-default-export': 0
};

const reactRules = {
    'react/prop-types': 0,
    'react/display-name': 0,
    'react/jsx-uses-react': 0,
    'react/react-in-jsx-scope': 0,
    'react/require-default-props': 0,
    'react/jsx-props-no-spreading': 0,
    'react/jsx-fragments': 2,
    'react/jsx-pascal-case': 2,
    'react/no-array-index-key': 2,
    'react/jsx-boolean-value': [2, 'always'],
    'react/hook-use-state': [
        2,
        {
            allowDestructuredState: true,
        },
    ],
    'react/jsx-sort-props': [
        2,
        {
            callbacksLast: true,
            ignoreCase: true,
            noSortAlphabetically: true,
            multiline: 'last',
            reservedFirst: false,
        },
    ],
    'react/jsx-no-duplicate-props': [
        2,
        {
            ignoreCase: true,
        },
    ],
    'react/no-multi-comp': [
        2,
        {
            ignoreStateless: true,
        },
    ],
    'react/destructuring-assignment': [
        2,
        'always',
        {
            ignoreClassFields: true,
            destructureInSignature: 'always',
        },
    ],
    'react/jsx-no-leaked-render': [
        2,
        {
            validStrategies: ['coerce', 'ternary'],
        },
    ],
    'react/no-unstable-nested-components': [
        2,
        {
            allowAsProps: true,
        },
    ],
    'react/jsx-no-useless-fragment': [
        2,
        {
            allowExpressions: true,
        },
    ],
    'react/jsx-filename-extension': [
        2,
        {
            extensions: ['.jsx'],
            allow: 'as-needed',
        },
    ],
    'react/function-component-definition': [
        2,
        {
            namedComponents: 'arrow-function',
        },
    ]
};

const config = [
    {
        files: ['**/*.{mjs,js,jsx}'],
        ignores: filesToIgnore,
        languageOptions: {
            parserOptions: {
                ecmaVersion: 'latest',
                sourceType: 'module',
                ecmaFeatures: {
                    jsx: true,
                },
            },
            ...eslintReactConfig.languageOptions,
            globals: {
                ...globals.browser,
                ...globals.node,
                ...globals.es2021
            }
        },
        linterOptions: {
            noInlineConfig: true,
            reportUnusedDisableDirectives: true,
        },
        plugins: {
            react: eslintReact,
            import: eslintImport,
            'jsx-a11y': eslintA11y
        },
        settings: {
            'import/extensions': ['.mjs', '.js', '.jsx'],
            'import/external-module-folders': ['node_modules'],
            react: {
                version: 'detect',
            },
        },
        rules: {
            // Recommended rules
            ...eslintPlugin.configs.recommended.rules,
            ...eslintReact.configs.recommended.rules,
            ...eslintA11y.configs.recommended.rules,
            ...eslintImportConfig.rules,
            ...baseRules,
            ...importRules,
            ...reactRules
        },
    },
];

export default config;
