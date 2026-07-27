# ORT Web App Report Template

A React 19 + TypeScript single-page application, bundled into one self-contained HTML file, that renders
the results of an [OSS Review Toolkit](https://oss-review-toolkit.org/) run. This directory holds the
**frontend template** for ORT's Web App report; the finished report is produced by the sibling
`WebAppReporter` (see [How it fits together](#how-it-fits-together) at the bottom for the details).

## Getting started

```bash
npm ci        # Install dependencies
npm run dev   # Start the Vite dev server (renders the sample data in index.html)
```

## Scripts

| Command                             | What it does                                                                                          |
| ----------------------------------- | ---------------------------------------------------------------------------------------------------- |
| `npm run dev`                       | Vite dev server (loads sample data from `index.html`).                                               |
| `npm run build`                     | Produces `build/index.html` and `build/scan-report-template.html`.                                   |
| `npm run preview`                   | Serves the built single-file output.                                                                 |
| `npm run typecheck`                 | Type-checks the code with TypeScript (`tsgo`, the native TS compiler, falling back to `tsc`) — reports type errors, emits nothing.                                                       |
| `npm run test`                      | Runs the Vitest unit and component suite once.                                                        |
| `npm run test:watch`                | Runs Vitest in watch mode.                                                                            |
| `npm run lint`                      | Checks lint + formatting with [Biome](https://biomejs.dev/) (`biome check .`); reports issues without changing files.                                                                                       |
| `npm run lint:fix`                  | Auto-fixes the Biome lint + formatting issues in place (`biome check --write .`).                                                                               |
| `npm run format`                    | Reformats every file with Biome (`biome format --write .`).                                                                              |
| `npm run import-ort-file -- <file>` | Embed an ORT `EvaluatedModel` JSON `<file>` into `index.html` (replaces the `#ort-report-data` script). |

### Rendering your own report data

To preview a specific ORT result file in a dev WebApp report, embed its `EvaluatedModel` JSON into `index.html`:

```bash
npm run import-ort-file -- src/test/evaluated-model.json
```

This validates that the file is JSON and replaces the contents of the
`<script type="application/json" id="ort-report-data">` element with it. It only touches your local
`index.html`, which is also the single source of truth the Vitest fixtures read.

**Note:** The `evaluated-model.json` must be smaller than Node's maximum string length of `0x1fffffe8` bytes (~512 MiB).

## Building via Gradle

The npm scripts above are for frontend-only work. To rebuild the template the way the ORT build does — so
`web-app` picks up your change — run these from the repository root (Gradle downloads a pinned Node.js and
runs the matching npm script for you):

| Command                                                 | What it does                                                                                                    |
| ------------------------------------------------------- | -------------------------------------------------------------------------------------------------------------- |
| `./gradlew :plugins:reporters:web-app:build`            | Runs the Vite build (`:web-app-template:npmBuild`), copies `scan-report-template.html` into the reporter's resources, and compiles the reporter (incl. its unit tests). |
| `./gradlew :plugins:reporters:web-app:funTest`          | Renders a full report from the freshly built template (the reporter's functional test).                         |
| `./gradlew :plugins:reporters:web-app-template:npmTest` | Runs the frontend Vitest suite (`npm run test`).                                                                |
| `./gradlew :plugins:reporters:web-app-template:check`   | Frontend Biome lint + TypeScript type-check (`npmLint` + `npmTypecheck`).                                        |

Note: the frontend Vitest tests are **not** part of `:web-app-template:build`/`check` — run `npmTest`
explicitly (or `npm run test`) for them.

## Before opening a pull request

For any change under `web-app-template/` (or `web-app/`), confirm all of the following pass.

Frontend checks, from this directory:

```bash
npm run lint        # Biome lint + format (use npm run lint:fix to autofix)
npm run typecheck   # TypeScript type-check
npm run test        # Vitest unit + component suite
npm run build       # confirms the single-file template still builds
```

Integration check, from the repository root — verify the Kotlin reporter still builds and can render a
report end-to-end from the new template (this also catches any break of the report-data placeholder
contract):

```bash
./gradlew :plugins:reporters:web-app:build :plugins:reporters:web-app:funTest
```

For UI changes, also run `npm run dev` and eyeball the affected views (the dev server renders the sample
report embedded in `index.html`).

---

## How it fits together

This module contains only the frontend. The Vite build (`vite-plugin-singlefile`) bundles everything —
JS, CSS, and assets — into a single `scan-report-template.html`, in which the report data is left as the
placeholder token `ORT_REPORT_DATA_PLACEHOLDER`.

The sibling [`:plugins:reporters:web-app`](../web-app) module is the Kotlin `WebAppReporter`. Its Gradle
build pulls the freshly built `scan-report-template.html` from this module (through the
`webAppTemplateConfiguration` artifact) into its resources. At report time the reporter reads that
template and replaces the placeholder with the run's gzip + Base64-encoded `EvaluatedModel`, producing a
final, fully offline HTML report.

In short: **edit the UI here → rebuild → `web-app` picks up the new template.**

> **Build contract:** the build must keep producing `scan-report-template.html` whose report-data element
> is exactly `<script id="ort-report-data" type="application/gzip">ORT_REPORT_DATA_PLACEHOLDER</script>`.
> The Kotlin reporter locates the report data by that `id`/`type` and swaps the token for the run's data —
> changing any of them breaks report generation. (`vite.config.ts` rewrites the element during the build.)

## Architecture

The app is a thin, typed pipeline from embedded data to rendered views:

1. **Embedded data** — `index.html` carries the report in a `<script id="ort-report-data">` element:
   `application/json` for the dev sample, or `application/gzip` (the placeholder the reporter fills in)
   in production.
2. **Load & decode** (`src/App.tsx`) — gzip data is Base64-decoded and inflated with the Web-standard
   `DecompressionStream`; JSON data is parsed directly.
3. **Raw types** (`src/types/evaluatedModelData.ts`) — plain `interface`s describing the on-the-wire
   `EvaluatedModel` JSON, used only at this parsing boundary.
4. **Domain model** (`src/models/`, e.g. `WebAppEvaluatedModel`) — typed classes that wrap the raw payload
   behind `has*`/`get*` getters, using `#private` fields and lazily-built indexes and lookups.
5. **Views** — `src/pages/AppPage` is the tab shell; feature components under `src/components/` render each
   view, composing shadcn/ui primitives (`src/components/ui/`) and TanStack Table grids
   (`src/components/data-table/`).

Because the whole report is one offline HTML file (no server, no network), the app uses React and the
TanStack libraries directly rather than a full framework such as TanStack Start.

## Stack

- **React 19** — no SSR, no router; a single offline HTML page.
- **TypeScript** in `strict` mode plus `noUncheckedIndexedAccess`, `exactOptionalPropertyTypes`, and
  `verbatimModuleSyntax`. Type-checked with TypeScript 7 (`@typescript/native-preview`, binary `tsgo`),
  falling back to stock `tsc`.
- **TanStack Table v8** for every grid (`src/components/data-table/`).
- **shadcn/ui** (Radix + Tailwind v4) for the remaining UI primitives (`src/components/ui/`).
- **recharts** for charts; the Web Standards `DecompressionStream` API for gzip; **markdown-to-jsx** and
  **react-syntax-highlighter** for rendering documentation and YAML/config.
- **Vite** (`vite-plugin-singlefile`) for bundling into one HTML file.
- **Biome** for lint + format (no ESLint), **Vitest** + **Testing Library** (jsdom) for tests.

## Project layout

| Path                          | Contents                                                              |
| ----------------------------- | -------------------------------------------------------------------- |
| `src/components/`             | Report views (summary, tables, tree, charts).                        |
| `src/components/data-table/`  | The shared TanStack Table wrapper and column helpers.                |
| `src/components/ui/`          | shadcn/ui primitives.                                                |
| `src/types/`                  | Raw on-the-wire `EvaluatedModel` JSON interfaces (parsing boundary). |
| `src/models/`                 | Typed classes wrapping the `EvaluatedModel` payload.                 |
| `src/pages/`                  | The app shell and top-level pages.                                   |
| `src/lib/`, `src/utils/`      | Framework-agnostic helpers (SPDX, CVSS, markdown, colours).          |
| `src/test/`                   | Vitest setup and fixtures.                                           |
