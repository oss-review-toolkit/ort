/*
 * Copyright (C) 2026 The ORT Project Copyright Holders <https://github.com/oss-review-toolkit/ort/blob/main/NOTICE>
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

/**
 * On-the-wire shape of the EvaluatedModel JSON payload produced by the ORT WebAppReporter and
 * consumed by the domain model classes under `src/models`. Each `EvaluatedModel*` interface mirrors
 * the fields the corresponding model class in `src/models` reads from the payload. The payload may
 * key fields in snake_case (as emitted by the Kotlin reporter) or camelCase, so both variants are
 * declared optional and callers read whichever is present.
 */

// ---------------------------------------------------------------------------------------------
// Primitive / shared building blocks
// ---------------------------------------------------------------------------------------------

export interface EvaluatedModelHash {
    readonly algorithm?: string;
    readonly value?: string;
}

export interface EvaluatedModelRemoteArtifact {
    readonly hash?: EvaluatedModelHash;
    readonly url?: string;
}

export interface EvaluatedModelVcsInfo {
    readonly path?: string;
    readonly resolved_revision?: string;
    readonly resolvedRevision?: string;
    readonly revision?: string;
    readonly type?: string;
    readonly url?: string;
}

export interface EvaluatedModelTextLocation {
    readonly end_line?: number;
    readonly endLine?: number;
    readonly path?: string;
    readonly start_line?: number;
    readonly startLine?: number;
}

export interface EvaluatedModelProvenance {
    readonly source_artifact?: EvaluatedModelRemoteArtifact;
    readonly sourceArtifact?: EvaluatedModelRemoteArtifact;
    readonly vcs_info?: EvaluatedModelVcsInfo;
    readonly vcsInfo?: EvaluatedModelVcsInfo;
}

export interface EvaluatedModelScannerDetails {
    readonly configuration?: string;
    readonly name?: string;
    readonly version?: string;
}

// ---------------------------------------------------------------------------------------------
// Environment / tools metadata
// ---------------------------------------------------------------------------------------------

export interface EvaluatedModelEnvironment {
    readonly build_jdk?: string;
    readonly buildJdk?: string;
    readonly java_version?: string;
    readonly javaVersion?: string;
    readonly max_memory?: number;
    readonly maxMemory?: number;
    readonly ort_version?: string;
    readonly ortVersion?: string;
    readonly os?: string;
    readonly processors?: number;
    readonly variables?: Record<string, string>;
}

export interface EvaluatedModelRun {
    readonly end_time?: string;
    readonly endTime?: string;
    readonly environment?: EvaluatedModelEnvironment;
    readonly start_time?: string;
    readonly startTime?: string;
}

export interface EvaluatedModelToolsMetadata {
    readonly advisor?: EvaluatedModelRun;
    readonly analyzer?: EvaluatedModelRun;
    readonly evaluator?: EvaluatedModelRun;
    readonly scanner?: EvaluatedModelRun;
}

// ---------------------------------------------------------------------------------------------
// Excludes, includes & path/scope helpers
// ---------------------------------------------------------------------------------------------

export interface EvaluatedModelPathExclude {
    readonly _id?: number;
    readonly comment?: string;
    readonly pattern?: string;
    readonly reason?: string;
}

export interface EvaluatedModelPathInclude {
    readonly _id?: number;
    readonly comment?: string;
    readonly pattern?: string;
    readonly reason?: string;
}

export interface EvaluatedModelScopeExclude {
    readonly _id?: number;
    readonly comment?: string;
    readonly name?: string;
    readonly reason?: string;
}

export interface EvaluatedModelExcludes {
    readonly paths?: EvaluatedModelPathExclude[];
    readonly scopes?: EvaluatedModelScopeExclude[];
}

export interface EvaluatedModelIncludes {
    readonly paths?: EvaluatedModelPathInclude[];
}

// ---------------------------------------------------------------------------------------------
// License choices
// ---------------------------------------------------------------------------------------------

export interface EvaluatedModelSpdxLicenseChoice {
    readonly choice?: string;
    readonly given?: string;
}

export interface EvaluatedModelPackageLicenseChoice {
    readonly license_choices?: EvaluatedModelSpdxLicenseChoice[];
    readonly licenseChoices?: EvaluatedModelSpdxLicenseChoice[];
    readonly package_id?: string;
    readonly packageId?: string;
}

export interface EvaluatedModelWebAppLicenseChoices {
    readonly _id?: number;
    readonly package_license_choices?: EvaluatedModelPackageLicenseChoice[];
    readonly packageLicenseChoices?: EvaluatedModelPackageLicenseChoice[];
    readonly repository_license_choices?: EvaluatedModelSpdxLicenseChoice[];
    readonly repositoryLicenseChoices?: EvaluatedModelSpdxLicenseChoice[];
}

// ---------------------------------------------------------------------------------------------
// Snippet choices
// ---------------------------------------------------------------------------------------------

export interface EvaluatedModelGiven {
    readonly sourceLocation?: EvaluatedModelTextLocation;
}

export interface EvaluatedModelChoice {
    readonly comment?: string;
    readonly purl?: string;
    readonly reason?: string;
}

export interface EvaluatedModelSnippetChoice {
    readonly choice?: EvaluatedModelChoice;
    readonly given?: EvaluatedModelGiven;
}

export interface EvaluatedModelWebAppSnippetChoices {
    readonly _id?: number;
    readonly choices?: EvaluatedModelSnippetChoice[];
    readonly provenance?: EvaluatedModelProvenance;
}

// ---------------------------------------------------------------------------------------------
// Package configurations & curations
// ---------------------------------------------------------------------------------------------

export interface EvaluatedModelVcsMatcher {
    readonly revision?: string;
    readonly type?: string;
    readonly url?: string;
}

export interface EvaluatedModelWebAppPackageConfiguration {
    readonly _id?: number;
    readonly id?: string;
    readonly license_finding_curations?: number[];
    readonly licenseFindingCurations?: number[];
    readonly path_excludes?: number[];
    readonly pathExcludes?: number[];
    readonly source_artifact_url?: string;
    readonly source_code_origin?: "ARTIFACT" | "VCS";
    readonly sourceArtifactUrl?: string;
    readonly sourceCodeOrigin?: "ARTIFACT" | "VCS";
    readonly vcs?: EvaluatedModelVcsMatcher;
}

export interface EvaluatedModelPackageCurationData {
    readonly authors?: string[];
    readonly binary_artifact?: EvaluatedModelRemoteArtifact;
    readonly binaryArtifact?: EvaluatedModelRemoteArtifact;
    readonly comment?: string;
    readonly concluded_license?: string;
    readonly concludedLicense?: string;
    readonly cpe?: string;
    readonly description?: string;
    readonly homepage_url?: string;
    readonly homepageUrl?: string;
    readonly is_metadata_only?: boolean;
    readonly is_modified?: boolean;
    readonly isMetadataOnly?: boolean;
    readonly isModified?: boolean;
    readonly labels?: Record<string, string>;
    readonly purl?: string;
    readonly source_artifact?: EvaluatedModelRemoteArtifact;
    readonly source_code_origins?: ReadonlyArray<"ARTIFACT" | "VCS">;
    readonly sourceArtifact?: EvaluatedModelRemoteArtifact;
    readonly sourceCodeOrigins?: ReadonlyArray<"ARTIFACT" | "VCS">;
    readonly vcs?: EvaluatedModelVcsInfo;
}

export interface EvaluatedModelWebAppPackageCuration {
    readonly _id?: number;
    readonly curations?: EvaluatedModelPackageCurationData;
    readonly id?: string;
}

// ---------------------------------------------------------------------------------------------
// License finding curations & licenses
// ---------------------------------------------------------------------------------------------

export interface EvaluatedModelLicense {
    readonly _id?: number;
    readonly id?: string;
}

export interface EvaluatedModelLicenseFindingCuration {
    readonly _id?: number;
    readonly comment?: string;
    readonly concluded_license?: string;
    readonly concludedLicense?: string;
    readonly detected_license?: string;
    readonly detectedLicense?: string;
    readonly line_count?: number;
    readonly lineCount?: number;
    readonly path?: string;
    readonly reason?: string;
    readonly start_lines?: number[];
    readonly startLines?: number[];
}

// ---------------------------------------------------------------------------------------------
// Copyrights, findings, scopes
// ---------------------------------------------------------------------------------------------

export interface EvaluatedModelCopyright {
    readonly _id?: number;
    readonly statement?: string;
}

export interface EvaluatedModelFinding {
    readonly copyright?: number;
    readonly end_line?: number;
    readonly endLine?: number;
    readonly license?: number;
    readonly path?: string;
    readonly path_excludes?: number[];
    readonly pathExcludes?: number[];
    readonly scan_result?: number;
    readonly scanResult?: number;
    readonly start_line?: number;
    readonly startLine?: number;
    readonly type?: string;
}

export interface EvaluatedModelScope {
    readonly _id?: number;
    readonly excludes?: number[];
    readonly name?: string;
}

// ---------------------------------------------------------------------------------------------
// Resolutions
// ---------------------------------------------------------------------------------------------

export interface EvaluatedModelResolution {
    readonly _id?: number;
    readonly comment?: string;
    readonly message?: string;
    readonly reason?: string;
}

export interface EvaluatedModelVulnerabilityResolution {
    readonly _id?: number;
    readonly comment?: string;
    readonly id?: string;
    readonly reason?: string;
}

// ---------------------------------------------------------------------------------------------
// Issues, rule violations, vulnerabilities
// ---------------------------------------------------------------------------------------------

export interface EvaluatedModelIssue {
    readonly _id?: number;
    readonly how_to_fix?: string;
    readonly howToFix?: string;
    readonly is_excluded?: boolean;
    readonly isExcluded?: boolean;
    readonly message?: string;
    readonly path?: string;
    readonly pkg?: number;
    readonly resolutions?: number[];
    readonly scan_result?: number;
    readonly scanResult?: number;
    readonly severity?: string;
    readonly source?: string;
    readonly timestamp?: string;
    readonly type?: string;
}

export interface EvaluatedModelRuleViolation {
    readonly _id?: number;
    readonly how_to_fix?: string;
    readonly howToFix?: string;
    readonly license?: number;
    readonly license_source?: string;
    readonly licenseSource?: string;
    readonly message?: string;
    readonly pkg?: number;
    readonly resolutions?: number[];
    readonly rule?: string;
    readonly severity?: string;
}

export interface EvaluatedModelVulnerabilityReference {
    readonly cscoringSystem?: string;
    readonly score?: number;
    readonly scoring_system?: string;
    readonly scoringSystem?: string;
    readonly severity?: string;
    readonly url?: string;
    readonly vector?: string;
}

export interface EvaluatedModelVulnerability {
    readonly _id?: number;
    readonly description?: string;
    readonly id?: string;
    readonly pkg?: number;
    readonly references?: EvaluatedModelVulnerabilityReference[];
    readonly resolutions?: number[];
    readonly summary?: string;
}

// ---------------------------------------------------------------------------------------------
// Scan results
// ---------------------------------------------------------------------------------------------

export interface EvaluatedModelScanResult {
    readonly _id?: number;
    readonly end_time?: string;
    readonly endTime?: string;
    readonly issues?: EvaluatedModelIssue[];
    readonly package_verification_code?: string;
    readonly packageVerificationCode?: string;
    readonly provenance?: EvaluatedModelProvenance;
    readonly scanner?: EvaluatedModelScannerDetails;
    readonly start_time?: string;
    readonly startTime?: string;
}

// ---------------------------------------------------------------------------------------------
// Packages
// ---------------------------------------------------------------------------------------------

export interface EvaluatedModelDeclaredLicensesProcessed {
    readonly mapped_licenses?: number[];
    readonly mappedLicenses?: number[];
    readonly spdx_expression?: string;
    readonly spdxExpression?: string;
    readonly unmapped_licenses?: number[];
    readonly unmappedLicenses?: number[];
}

export interface EvaluatedModelPackage {
    readonly _id?: number;
    readonly authors?: string[];
    readonly binary_artifact?: EvaluatedModelRemoteArtifact;
    readonly binaryArtifact?: EvaluatedModelRemoteArtifact;
    readonly concluded_license?: string;
    readonly concludedLicense?: string;
    readonly curations?: number[];
    readonly declared_licenses?: number[];
    readonly declared_licenses_processed?: EvaluatedModelDeclaredLicensesProcessed;
    readonly declaredLicenses?: number[];
    readonly declaredLicensesProcessed?: EvaluatedModelDeclaredLicensesProcessed;
    readonly definition_file_path?: string;
    readonly definitionFilePath?: string;
    readonly description?: string;
    readonly detected_excluded_licenses?: number[];
    readonly detected_licenses?: number[];
    readonly detectedExcludedLicenses?: number[];
    readonly detectedLicenses?: number[];
    readonly effective_license?: string;
    readonly effectiveLicense?: string;
    readonly findings?: EvaluatedModelFinding[];
    readonly homepage_url?: string;
    readonly homepageUrl?: string;
    readonly id?: string;
    readonly is_excluded?: boolean;
    readonly is_project?: boolean;
    readonly isExcluded?: boolean;
    readonly isProject?: boolean;
    readonly labels?: Record<string, string>;
    readonly levels?: number[];
    readonly package_configurations?: number[];
    readonly packageConfigurations?: number[];
    readonly path_excludes?: number[];
    readonly pathExcludes?: number[];
    readonly paths?: number[];
    readonly purl?: string;
    readonly scan_results?: number[];
    readonly scanResults?: number[];
    readonly scope_excludes?: number[];
    readonly scopeExcludes?: number[];
    readonly scopes?: number[];
    readonly source_artifact?: EvaluatedModelRemoteArtifact;
    readonly sourceArtifact?: EvaluatedModelRemoteArtifact;
    readonly vcs?: EvaluatedModelVcsInfo;
    readonly vcs_processed?: EvaluatedModelVcsInfo;
    readonly vcsProcessed?: EvaluatedModelVcsInfo;
}

// ---------------------------------------------------------------------------------------------
// Paths
// ---------------------------------------------------------------------------------------------

export interface EvaluatedModelPath {
    readonly _id?: number;
    readonly path?: number[];
    readonly pkg?: number;
    readonly project?: number;
    readonly scope?: number;
}

// ---------------------------------------------------------------------------------------------
// Dependency tree
// ---------------------------------------------------------------------------------------------

export interface EvaluatedModelTreeNode {
    readonly children?: EvaluatedModelTreeNode[];
    readonly key?: number;
    readonly path_excludes?: number[];
    readonly pathExcludes?: number[];
    readonly pkg?: number;
    readonly scope?: number;
    readonly scope_excludes?: number[];
    readonly scope_index?: number;
    readonly scopeExcludes?: number[];
    readonly scopeIndex?: number;
    readonly title?: string;
}

// ---------------------------------------------------------------------------------------------
// Statistics
// ---------------------------------------------------------------------------------------------

export interface EvaluatedModelIssueStatistics {
    readonly errors?: number;
    readonly hints?: number;
    readonly warnings?: number;
}

export interface EvaluatedModelLicenseStatistics {
    readonly declared?: Record<string, number>;
    readonly detected?: Record<string, number>;
    readonly effective?: Record<string, number>;
}

export interface EvaluatedModelDependencyTreeStatistics {
    readonly excluded_packages?: number;
    readonly excluded_projects?: number;
    readonly excluded_scopes?: string[];
    readonly excludedPackages?: number;
    readonly excludedProjects?: number;
    readonly excludedScopes?: string[];
    readonly included_packages?: number;
    readonly included_projects?: number;
    readonly included_scopes?: string[];
    readonly included_tree_depth?: number;
    readonly includedPackages?: number;
    readonly includedProjects?: number;
    readonly includedScopes?: string[];
    readonly includedTreeDepth?: number;
    readonly total_tree_depth?: number;
    readonly totalTreeDepth?: number;
}

export interface EvaluatedModelRepositoryConfigurationStatistics {
    readonly issue_resolutions?: number;
    readonly issueResolutions?: number;
    readonly license_choices?: number;
    readonly license_finding_curations?: number;
    readonly licenseChoices?: number;
    readonly licenseFindingCurations?: number;
    readonly package_configurations?: number;
    readonly package_curations?: number;
    readonly packageConfigurations?: number;
    readonly packageCurations?: number;
    readonly path_excludes?: number;
    readonly pathExcludes?: number;
    readonly rule_violation_resolutions?: number;
    readonly ruleViolationResolutions?: number;
    readonly scope_excludes?: number;
    readonly scopeExcludes?: number;
    readonly vulnerability_resolutions?: number;
    readonly vulnerabilityResolutions?: number;
}

export interface EvaluatedModelResolvedConfigurationStatistics {
    readonly issue_resolutions?: number;
    readonly issueResolutions?: number;
    readonly license_choices?: number;
    readonly licenseChoices?: number;
    readonly package_configurations?: number;
    readonly package_curations?: number;
    readonly packageConfigurations?: number;
    readonly packageCurations?: number;
    readonly rule_violation_resolutions?: number;
    readonly ruleViolationResolutions?: number;
    readonly vulnerability_resolutions?: number;
    readonly vulnerabilityResolutions?: number;
}

export interface EvaluatedModelStatistics {
    readonly dependency_tree?: EvaluatedModelDependencyTreeStatistics;
    readonly dependencyTree?: EvaluatedModelDependencyTreeStatistics;
    readonly execution_duration_in_seconds?: number;
    readonly executionDurationInSeconds?: number;
    readonly licenses?: EvaluatedModelLicenseStatistics;
    readonly open_issues?: EvaluatedModelIssueStatistics;
    readonly open_rule_violations?: EvaluatedModelIssueStatistics;
    readonly open_vulnerabilities?: number;
    readonly openIssues?: EvaluatedModelIssueStatistics;
    readonly openRuleViolations?: EvaluatedModelIssueStatistics;
    readonly openVulnerabilities?: number;
    readonly repository_configuration?: EvaluatedModelRepositoryConfigurationStatistics;
    readonly repositoryConfiguration?: EvaluatedModelRepositoryConfigurationStatistics;
    readonly resolved_configuration?: EvaluatedModelResolvedConfigurationStatistics;
    readonly resolvedConfiguration?: EvaluatedModelResolvedConfigurationStatistics;
}

// ---------------------------------------------------------------------------------------------
// Repository configuration
// ---------------------------------------------------------------------------------------------

export interface EvaluatedModelPackageManagerConfiguration {
    readonly must_run_after?: string[];
    readonly mustRunAfter?: string[];
    readonly options?: Record<string, string>;
}

export interface EvaluatedModelRepositoryAnalyzerConfiguration {
    readonly allow_dynamic_versions?: boolean;
    readonly allowDynamicVersions?: boolean;
    readonly disabled_package_managers?: string[];
    readonly disabledPackageManagers?: string[];
    readonly enabled_package_managers?: string[];
    readonly enabledPackageManagers?: string[];
    readonly package_managers?: Record<string, EvaluatedModelPackageManagerConfiguration>;
    readonly packageManagers?: Record<string, EvaluatedModelPackageManagerConfiguration>;
    readonly skip_excluded?: boolean;
    readonly skipExcluded?: boolean;
}

export interface EvaluatedModelRepositoryConfiguration {
    readonly analyzer?: EvaluatedModelRepositoryAnalyzerConfiguration;
    readonly curations?: EvaluatedModelWebAppPackageCuration[];
    readonly excludes?: EvaluatedModelExcludes;
    readonly includes?: EvaluatedModelIncludes;
    readonly license_choices?: EvaluatedModelWebAppLicenseChoices;
    readonly licenseChoices?: EvaluatedModelWebAppLicenseChoices;
    readonly package_configurations?: EvaluatedModelWebAppPackageConfiguration[];
    readonly packageConfigurations?: EvaluatedModelWebAppPackageConfiguration[];
    readonly resolutions?: EvaluatedModelResolution[];
    readonly snippet_choices?: EvaluatedModelWebAppSnippetChoices[];
    readonly snippetChoices?: EvaluatedModelWebAppSnippetChoices[];
}

// ---------------------------------------------------------------------------------------------
// Repository
// ---------------------------------------------------------------------------------------------

export interface EvaluatedModelRepository {
    readonly config?: EvaluatedModelRepositoryConfiguration;
    readonly nested_repositories?: Record<string, EvaluatedModelVcsInfo>;
    readonly nestedRepositories?: Record<string, EvaluatedModelVcsInfo>;
    readonly vcs?: EvaluatedModelVcsInfo;
    readonly vcs_processed?: EvaluatedModelVcsInfo;
    readonly vcsProcessed?: EvaluatedModelVcsInfo;
}

// ---------------------------------------------------------------------------------------------
// Top-level evaluated model
// ---------------------------------------------------------------------------------------------

export interface EvaluatedModel {
    readonly copyrights?: EvaluatedModelCopyright[];
    readonly custom_data?: Record<string, unknown>;
    readonly customData?: Record<string, unknown>;
    readonly dependency_trees?: EvaluatedModelTreeNode[];
    readonly dependencyTrees?: EvaluatedModelTreeNode[];
    readonly issue_resolutions?: EvaluatedModelResolution[];
    readonly issueResolutions?: EvaluatedModelResolution[];
    readonly issues?: EvaluatedModelIssue[];
    readonly labels?: Record<string, string>;
    readonly license_finding_curations?: EvaluatedModelLicenseFindingCuration[];
    readonly licenseFindingCurations?: EvaluatedModelLicenseFindingCuration[];
    readonly licenses?: EvaluatedModelLicense[];
    readonly package_configurations?: EvaluatedModelWebAppPackageConfiguration[];
    readonly package_curations?: EvaluatedModelWebAppPackageCuration[];
    readonly packageConfigurations?: EvaluatedModelWebAppPackageConfiguration[];
    readonly packageCurations?: EvaluatedModelWebAppPackageCuration[];
    readonly packages?: EvaluatedModelPackage[];
    readonly path_excludes?: EvaluatedModelPathExclude[];
    readonly path_includes?: EvaluatedModelPathInclude[];
    readonly pathExcludes?: EvaluatedModelPathExclude[];
    readonly pathIncludes?: EvaluatedModelPathInclude[];
    readonly paths?: EvaluatedModelPath[];
    readonly repository?: EvaluatedModelRepository;
    readonly repository_configuration?: string;
    readonly repositoryConfiguration?: string;
    readonly resolutions?: EvaluatedModelResolution[];
    readonly rule_violation_resolutions?: EvaluatedModelResolution[];
    readonly rule_violations?: EvaluatedModelRuleViolation[];
    readonly ruleViolationResolutions?: EvaluatedModelResolution[];
    readonly ruleViolations?: EvaluatedModelRuleViolation[];
    readonly scan_results?: EvaluatedModelScanResult[];
    readonly scanResults?: EvaluatedModelScanResult[];
    readonly scope_excludes?: EvaluatedModelScopeExclude[];
    readonly scopeExcludes?: EvaluatedModelScopeExclude[];
    readonly scopes?: EvaluatedModelScope[];
    readonly severe_issue_threshold?: string;
    readonly severe_rule_violation_threshold?: string;
    readonly severeIssueThreshold?: string;
    readonly severeRuleViolationThreshold?: string;
    readonly statistics?: EvaluatedModelStatistics;
    readonly tools_metadata?: EvaluatedModelToolsMetadata;
    readonly toolsMetadata?: EvaluatedModelToolsMetadata;
    readonly vulnerabilities?: EvaluatedModelVulnerability[];
    readonly vulnerabilities_resolutions?: EvaluatedModelVulnerabilityResolution[];
    readonly vulnerabilitiesResolutions?: EvaluatedModelVulnerabilityResolution[];
}
