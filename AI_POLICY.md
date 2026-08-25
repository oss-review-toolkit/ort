# ORT AI Usage Policy

## 1. Purpose and General Requirements

The ORT community welcomes contributions drafted with the assistance of Generative AI, provided they comply with our intellectual property, security, and governance standards.
All rules from the standard contribution guidelines apply equally to AI-assisted work, including requirements for atomic commits.

* Contributors MUST review, validate, and verify the technical correctness, security, and licensing of all generated content.
* Contributors MUST NOT submit proprietary project data, credentials, private SSH keys, or API keys to public AI systems.
* Contributors are responsible for ensuring that all AI-assisted content complies with the project's intellectual property and licensing obligations, as AI-generated text alone is generally not eligible for copyright.

## 2. Human Oversight and Content Quality

A human contributor remains fully accountable for all submitted content, regardless of the tools used to create it.
Because it is incredibly easy to generate large volumes of text using AI, all contributions, including comments, issues, and code, must remain concise and optimized for human readability.

* Contributors MUST fully understand the submitted code and be capable of explaining how and why the changes work.
* Automated or agentic systems MUST NOT merge or commit changes without explicit human review and approval.
* AI MUST NOT impersonate a human in any interaction, including instances where a human is copy-pasting an AI's response to another human.
* If AI is used to generate or participate in community discussions, its output MUST be clearly marked as AI-generated.

## 3. Attribution and Disclosure

Transparency regarding material AI assistance is essential for maintaining community trust.
While minor assistance does not require disclosure, substantial generation does.

* Code completion, spelling corrections, and simple formatting suggestions are exempt and do not require disclosure.
* If large portions of a contribution are generated, contributors SHOULD include an `Assisted-by: <Provider> <Model>` trailer in their commit metadata.

## 4. AI Agents and Project Documentation

Project documentation must always be written primarily for humans, not exclusively for AI consumption.
However, we recognize that automated agents need local context to function properly.

* The organization-wide `CONTRIBUTING.md` serves as the single source of truth for all repository rules.
* Any `AGENTS.md` file MUST be kept very brief and explicitly direct the AI to follow the primary contribution guidelines.
