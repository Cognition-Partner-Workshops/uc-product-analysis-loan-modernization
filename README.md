# Loan Modernization Analysis

This repository contains input artifacts and output templates for the **Product Analyst** demo use case: extracting legacy documentation into structured Confluence pages and Jira stories.

## Directory Structure

```
legacy-docs/          # Input: legacy system documentation (PDFs, screenshots, CSVs, specs)
confluence-output/    # Output: generated Confluence-style markdown pages
jira-stories/         # Output: generated Jira story templates (epics, stories, acceptance criteria)
templates/            # Reusable templates for story generation and page formatting
```

## Use Case

Demonstrate how Devin can:
1. Ingest legacy loan system documentation (BRDs, technical specs, screen captures)
2. Analyze and extract business rules, data flows, and system dependencies
3. Generate structured Confluence documentation pages
4. Create well-formed Jira stories with acceptance criteria
5. Map legacy functionality to modernized architecture recommendations

## Demo Scenario

A financial institution is modernizing a legacy loan origination system. The legacy system has:
- COBOL mainframe backend with batch processing
- Manual underwriting workflows documented in Word/PDF
- Complex business rules embedded in legacy code
- Multiple integration points with credit bureaus, core banking, and document management

The Product Analyst uses Devin to accelerate the analysis phase, converting months of manual document review into structured, actionable deliverables.

## Legacy loan origination batch

`legacy-los/` holds the nightly origination chain the documents above
describe: pricing, underwriting, the core banking account feed, the GL
extract and the HMDA extract, as GnuCOBOL programs with one recorded night of
inputs and the output that night produced. `make golden-check` in that folder
rebuilds and reruns the chain and compares it with `golden/`;
`make parity CANDIDATE=<dir>` does the same for any new implementation. See
`.agents/skills/legacy-los-parity/SKILL.md`.
