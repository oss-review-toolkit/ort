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

import { ListChecks, type LucideIcon, Tags } from "lucide-react";
import type { JSX, ReactNode } from "react";
import { useMemo, useState } from "react";
import {
    CopyToClipboard,
    OrtYmlFileIcon,
    PackageConfigurationIcon,
    PackageCurationIcon,
    SyntaxHighlight,
    ToolsIcon,
    Url,
} from "@/components/Shared";
import { ToolsMetadataCards } from "@/components/ToolsMetadataCards";
import { Tabs, TabsContent, TabsList, TabsTrigger } from "@/components/ui/tabs";
import type WebAppEvaluatedModel from "@/models/WebAppEvaluatedModel";

export interface RunDetailsProps {
    // When set to one of the inner tab keys (e.g. "package-curations"), that tab opens first instead of
    // the default; used by the Summary stat cards to deep-link into a specific section.
    focusTab?: string | null;
    webAppEvaluatedModel: WebAppEvaluatedModel;
}

interface ConfigTab {
    content: ReactNode;
    icon: LucideIcon;
    key: string;
    label: string;
}

// The Run Details view: tabbed run configuration (.ort.yml, labels, package configurations/curations,
// resolutions, tools).
function RunDetails({ focusTab, webAppEvaluatedModel }: RunDetailsProps): JSX.Element {
    const tabs = useMemo<ConfigTab[]>(() => {
        const items: ConfigTab[] = [];

        if (webAppEvaluatedModel.hasRepositoryConfiguration()) {
            items.push({
                key: "ort-yml",
                label: ".ort.yml",
                icon: OrtYmlFileIcon,
                content: (
                    <div className="relative">
                        <CopyToClipboard
                            className="absolute top-2 right-2 z-10 border bg-background shadow-sm"
                            label="Copy the .ort.yml file contents"
                            value={webAppEvaluatedModel.repositoryConfiguration ?? ""}
                        />
                        <SyntaxHighlight language="yaml" showLineNumbers>
                            {webAppEvaluatedModel.repositoryConfiguration ?? ""}
                        </SyntaxHighlight>
                    </div>
                ),
            });
        }

        if (webAppEvaluatedModel.hasLabels()) {
            const labels = webAppEvaluatedModel.labels;
            items.push({
                key: "labels",
                label: "Labels",
                icon: Tags,
                content: (
                    <dl className="grid grid-cols-[max-content_1fr] gap-x-4 gap-y-2 rounded-md border bg-muted/30 p-4 text-sm">
                        {Object.entries(labels).map(([key, value]) => (
                            <div className="contents" key={key}>
                                <dt className="font-medium text-muted-foreground">{key}</dt>
                                <dd className="break-all">{value.startsWith("http") ? <Url href={value} /> : value}</dd>
                            </div>
                        ))}
                    </dl>
                ),
            });
        }

        if (webAppEvaluatedModel.hasPackageConfigurations()) {
            const configurationsYaml = webAppEvaluatedModel.getPackageConfigurationsAsYaml();
            items.push({
                key: "package-configurations",
                label: "Package Configurations",
                icon: PackageConfigurationIcon,
                content: (
                    <div className="relative">
                        <CopyToClipboard
                            className="absolute top-2 right-2 z-10 border bg-background shadow-sm"
                            label="Copy the package configurations"
                            value={configurationsYaml}
                        />
                        <SyntaxHighlight language="yaml" showLineNumbers>
                            {configurationsYaml}
                        </SyntaxHighlight>
                    </div>
                ),
            });
        }

        if (webAppEvaluatedModel.hasPackageCurations()) {
            const curationsYaml = webAppEvaluatedModel.getPackageCurationsAsYaml();
            items.push({
                key: "package-curations",
                label: "Package Curations",
                icon: PackageCurationIcon,
                content: (
                    <div className="relative">
                        <CopyToClipboard
                            className="absolute top-2 right-2 z-10 border bg-background shadow-sm"
                            label="Copy the package curations"
                            value={curationsYaml}
                        />
                        <SyntaxHighlight language="yaml" showLineNumbers>
                            {curationsYaml}
                        </SyntaxHighlight>
                    </div>
                ),
            });
        }

        if (webAppEvaluatedModel.hasResolutions()) {
            const resolutionsYaml = webAppEvaluatedModel.getResolutionsAsYaml();
            items.push({
                key: "resolutions",
                label: "Resolutions",
                icon: ListChecks,
                content: (
                    <div className="relative">
                        <CopyToClipboard
                            className="absolute top-2 right-2 z-10 border bg-background shadow-sm"
                            label="Copy the resolutions"
                            value={resolutionsYaml}
                        />
                        <SyntaxHighlight language="yaml" showLineNumbers>
                            {resolutionsYaml}
                        </SyntaxHighlight>
                    </div>
                ),
            });
        }

        items.push({
            content: <ToolsMetadataCards metadata={webAppEvaluatedModel.toolsMetadata} />,
            icon: ToolsIcon,
            key: "tools",
            label: "Tools",
        });

        return items;
    }, [webAppEvaluatedModel]);

    const defaultValue = tabs[0]?.key ?? "";
    // Open `focusTab` first when the Summary deep-links to a section that exists, otherwise the first
    // available tab; the user can still switch tabs freely afterwards.
    const [activeTab, setActiveTab] = useState(() =>
        focusTab && tabs.some((tab) => tab.key === focusTab) ? focusTab : defaultValue,
    );

    if (tabs.length === 0) {
        return <p className="text-muted-foreground text-sm">No run configuration available.</p>;
    }

    return (
        <Tabs onValueChange={setActiveTab} value={activeTab}>
            <TabsList className="flex h-auto flex-wrap">
                {tabs.map(({ icon: Icon, key, label }) => (
                    <TabsTrigger key={key} value={key}>
                        <Icon aria-hidden="true" className="mr-2 size-4" />
                        {label}
                    </TabsTrigger>
                ))}
            </TabsList>
            {tabs.map(({ content, key }) => (
                <TabsContent className="mt-4" key={key} value={key}>
                    {content}
                </TabsContent>
            ))}
        </Tabs>
    );
}

export { RunDetails };
export default RunDetails;
