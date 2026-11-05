package los.record;

/** The record layouts in legacy-los/copybooks, field for field, with the lengths each copybook states. */
public final class Copybooks {
    private Copybooks() {
    }

    /** LNAPPREC: loan application intake record (APPSIN), 150 bytes. */
    public static final Layout LNAPPREC = Layout.builder("LN-APP-REC")
            .field("APP-ID", "9(10)")
            .field("BORR-ID", "9(9)")
            .field("BORR-TIN", "9(9)")
            .field("BORR-LAST-NAME", "X(15)")
            .field("BORR-FIRST-NAME", "X(12)")
            .field("LOAN-TYPE", "X(3)")
            .field("LOAN-PURPOSE", "X(1)")
            .field("LOAN-AMT", "9(9)V99")
            .field("TERM-MONTHS", "9(3)")
            .field("PROP-STATE", "X(2)")
            .field("PROP-TYPE", "X(1)")
            .field("ANNUAL-INCOME", "9(9)V99")
            .field("MONTHLY-DEBT", "9(7)V99")
            .field("CREDIT-SCORE", "9(3)")
            .field("SCORE-DATE", "9(8)")
            .field("EMP-YEARS", "9(2)V9")
            .field("LOCK-DAYS", "9(3)")
            .field("LOCK-EXTS", "9(2)")
            .field("APP-DATE", "9(8)")
            .field("APPL-SEX", "X(1)")
            .field("APPL-RACE", "X(1)")
            .filler("X(25)")
            .build(150);

    /** LNRATE: daily rate sheet record (RATESIN), 40 bytes. */
    public static final Layout LNRATE = Layout.builder("LN-RATE-REC")
            .field("RATE-PRODUCT", "X(5)")
            .field("RATE-LOCK-DAYS", "9(3)")
            .field("RATE-BASE", "9(2)V9999")
            .field("RATE-EFF-DATE", "9(8)")
            .filler("X(18)")
            .build(40);

    /** LNPRCREC: priced application (PRICED), 200 bytes; PRC-APP holds an LNAPPREC image. */
    public static final Layout LNPRCREC = Layout.builder("LN-PRC-REC")
            .field("PRC-APP", "X(150)")
            .field("PRC-PRODUCT", "X(5)")
            .field("PRC-BASE-RATE", "9(2)V9999")
            .field("PRC-RISK-ADJ", "S9(1)V9999", Sign.LEADING_SEPARATE)
            .field("PRC-FINAL-RATE", "9(2)V9999")
            .field("PRC-MONTHLY-PMT", "9(7)V99")
            .field("PRC-EXT-FEE-PCT", "9(1)V999")
            .field("PRC-EXT-FEE-AMT", "9(7)V99")
            .field("PRC-STATUS", "X(2)")
            .filler("X(3)")
            .build(200);

    /** LNDECREC: underwriting decision (DECISION), 80 bytes. */
    public static final Layout LNDECREC = Layout.builder("LN-DEC-REC")
            .field("DEC-APP-ID", "9(10)")
            .field("DEC-CODE", "X(1)")
            .field("DEC-REASON", "X(5)")
            .field("DEC-LOAN-TYPE", "X(3)")
            .field("DEC-PRODUCT", "X(5)")
            .field("DEC-LOAN-AMT", "9(9)V99")
            .field("DEC-FINAL-RATE", "9(2)V9999")
            .field("DEC-MONTHLY-PMT", "9(7)V99")
            .field("DEC-DTI", "9(3)V99")
            .field("DEC-EXT-FEE-AMT", "9(7)V99")
            .field("DEC-DATE", "9(8)")
            .filler("X(8)")
            .build(80);

    /** LNGLREC: general ledger posting line (GLOUT), 85 bytes. */
    public static final Layout LNGLREC = Layout.builder("LN-GL-REC")
            .field("GL-POST-DATE", "9(8)")
            .field("GL-ACCOUNT", "9(12)")
            .field("GL-DRCR", "X(2)")
            .field("GL-AMOUNT", "9(13)V99")
            .field("GL-LOAN-NUMBER", "9(13)")
            .field("GL-DESC", "X(30)")
            .field("GL-COST-CENTER", "9(5)")
            .build(85);

    /** FISACCT: core banking account create request (FISOUT), 114 bytes. */
    public static final Layout FISACCT = Layout.builder("FIS-ACCT-CREATE-REQ")
            .field("MSG-TYPE", "X(4)")
            .field("LOAN-NUMBER", "9(12)")
            .field("BORROWER-SSN", "9(9)")
            .field("FIS-LOAN-TYPE", "X(3)")
            .field("FIS-LOAN-AMOUNT", "9(9)V99")
            .field("INTEREST-RATE", "9(2)V9999")
            .field("FIS-TERM-MONTHS", "9(3)")
            .field("FIRST-PMT-DATE", "9(8)")
            .field("FUNDING-DATE", "9(8)")
            .filler("X(50)")
            .build(114);
}
