# Security Policy

## Scope

This repository contains documentation artifacts for the loan origination
modernization analysis: legacy system descriptions (`legacy-docs/`), generated
Confluence pages (`confluence-output/`), generated Jira stories
(`jira-stories/`), reusable templates (`templates/`) and a small documentation
validation test suite (`tests/`). It does not ship deployable application code.

Relevant security concerns include, for example:

- Real customer, borrower or account data (PII, SSNs, account numbers, credit
  scores) accidentally committed in legacy documents or generated output.
- Credentials, endpoints, hostnames or connection strings for real mainframe,
  DB2, IBM MQ, FileNet, credit bureau or FIS core banking systems.
- Vulnerabilities in the CI workflow or its dependencies.

## Supported Versions

Only the latest state of the `main` branch is supported. There are no released
versions; fixes are applied to `main` only.

| Version            | Supported |
| ------------------ | --------- |
| `main` (latest)    | Yes       |
| Any other branch   | No        |

## Reporting a Vulnerability

**Do not open a public issue or pull request for security problems.**

Report vulnerabilities privately through GitHub Security Advisories:

1. Go to the repository's **Security** tab.
2. Click **Report a vulnerability** (private vulnerability reporting), or use
   <https://github.com/Cognition-Partner-Workshops/uc-product-analysis-loan-modernization/security/advisories/new>.
3. Describe the issue, the affected files or commits, and how to reproduce it.

If private vulnerability reporting is unavailable, contact a repository
maintainer listed in `.github/CODEOWNERS` directly and privately.

If you find sensitive data (PII or credentials) in the repository, report it
privately as above and do not copy the data into the report.

## Response Process

- We aim to acknowledge reports within 3 business days.
- We will assess the report, keep you informed of progress, and coordinate
  disclosure through the GitHub advisory once a fix is merged to `main`.
- Leaked secrets will be revoked and rotated, and sensitive data removed from
  the repository history where required.
