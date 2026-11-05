# System brief: Legacy Loan Origination System (LOS)

## Purpose

LOS processes consumer and commercial loan applications from intake through funding. Underwriters and lending operations staff are inferred users from the decision matrix and assigned-underwriter field; a complete user inventory is not provided. [Business requirements](../legacy-docs/loan-origination-brd.md#L9-L11), [underwriting](../legacy-docs/loan-origination-brd.md#L48-L53), [application model](../legacy-docs/loan-origination-brd.md#L65-L77).

**Scope:** This repository supports a Product Analyst demo that turns legacy documents into Confluence pages and Jira stories, rather than supplying the LOS implementation. The BRD labels version 4.2.1 “Production” but was last updated in March 2019; treat this brief as documented legacy state, not confirmation of today's deployment. [Repository purpose](../README.md#L1-L21), [BRD metadata](../legacy-docs/loan-origination-brd.md#L3-L7).

## Architecture

The documented deployment is an IBM z/OS mainframe: a green-screen frontend using 3270 emulation, COBOL batch programs, IBM MQ Series messaging, and Control-M scheduling. It is not described as a web application. Scheduled work includes morning rate imports, evening credit pulls, nightly ledger extracts, weekly compliance reporting, and monthly HMDA regulatory filings. [Architecture](../legacy-docs/loan-origination-brd.md#L13-L18), [batch schedule](../legacy-docs/loan-origination-brd.md#L55-L63).

## Data stores

IBM DB2 z/OS is the documented database. Its key entities are `LOAN_APPLICATION` (borrower, amount, status, decision and underwriter), `BORROWER` (identity, income, credit score and debt-to-income ratio), and `LOAN_PRICING` (rates and lock dates/status). The BRD explicitly marks borrower SSNs as encrypted; it does not establish encryption coverage for all data. FileNet stores and retrieves application, income, property and insurance documents. [Database](../legacy-docs/loan-origination-brd.md#L13-L18), [entities](../legacy-docs/loan-origination-brd.md#L65-L97), [document operations/types](../legacy-docs/system-interfaces-spec.md#L79-L95).

## Integrations

- **FIS core banking:** Bidirectional MQ requests/responses support account creation and funding; messages use fixed-width EBCDIC and COBOL copybook layouts. [Integration map](../legacy-docs/loan-origination-brd.md#L20-L27), [MQ specification](../legacy-docs/system-interfaces-spec.md#L56-L76).
- **Equifax, Experian, TransUnion:** Outbound HTTPS/XML credit pulls; the pricing rate sheet is an inbound Treasury feed at 6:00 AM ET. [Bureaus](../legacy-docs/loan-origination-brd.md#L20-L27), [Treasury feed](../legacy-docs/loan-origination-brd.md#L37-L38).
- **FileNet:** Bidirectional SOAP/WSDL document operations, authenticated with WS-Security UsernameToken. [Integration map](../legacy-docs/loan-origination-brd.md#L20-L27), [API/authentication](../legacy-docs/system-interfaces-spec.md#L79-L84).
- **General ledger and compliance:** Outbound SFTP files, respectively fixed-width ASCII and pipe-delimited CSV. Authentication mechanisms for these transfers, MQ and bureau calls are not specified in the cited interface descriptions; HTTPS/SFTP alone should not be read as an access-control design. [Batch interfaces](../legacy-docs/system-interfaces-spec.md#L97-L119), [MQ](../legacy-docs/system-interfaces-spec.md#L56-L76), [bureau protocol](../legacy-docs/loan-origination-brd.md#L20-L27).

## Top 3 operational risks

Severity and business impacts below are assessments (inferences); mitigations are recommendations, not existing controls.

1. **High — Unaudited underwriting overrides.** The BRD reports no audit trail for manual overrides. Business impact: decisions may be difficult to defend to auditors or resolve in borrower disputes. **Mitigation:** Record immutable override events with actor, reason, timestamp and before/after decision. [Known issue](../legacy-docs/loan-origination-brd.md#L99-L104).
2. **High — Stale credit scores.** The BRD reports a 30-day cache versus a stated 14-day regulatory requirement; that requirement is not independently verified here. Business impact: outdated credit information could undermine lending decisions and compliance. **Mitigation:** Confirm the applicable rule with compliance, shorten cache validity and refresh expired scores before decisions. [Known issue](../legacy-docs/loan-origination-brd.md#L99-L100).
3. **Med — Credit-bureau batch SLA failures.** Experian timeouts reportedly cause frequent `LOS-DAILY-002` overruns against its 11:00 PM completion SLA. Business impact: credit-dependent decisions may be delayed, increasing manual handling and customer wait times. **Mitigation:** Add bounded retries, timeout monitoring and an exception queue with escalation before the deadline. [SLA](../legacy-docs/loan-origination-brd.md#L55-L63), [known issue](../legacy-docs/loan-origination-brd.md#L99-L104).
