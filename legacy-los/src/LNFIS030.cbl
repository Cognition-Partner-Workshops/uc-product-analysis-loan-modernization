       IDENTIFICATION DIVISION.
       PROGRAM-ID. LNFIS030.
      *----------------------------------------------------------------*
      * LOS NIGHTLY STEP030  CORE BANKING ACCOUNT CREATE REQUESTS      *
      * ONE FISACCT MESSAGE PER AUTO-APPROVED LOAN. FUNDING IS SET 30  *
      * DAYS AFTER THE RUN DATE, FIRST PAYMENT ON THE 1ST OF THE       *
      * SECOND MONTH AFTER FUNDING. LOAN NUMBER IS BRANCH 04 + APP ID. *
      *----------------------------------------------------------------*
       ENVIRONMENT DIVISION.
       INPUT-OUTPUT SECTION.
       FILE-CONTROL.
           SELECT RUNDATE-FILE  ASSIGN TO RUNDATE
                  ORGANIZATION IS LINE SEQUENTIAL.
           SELECT PRICED-FILE   ASSIGN TO PRICED
                  ORGANIZATION IS LINE SEQUENTIAL.
           SELECT DECISION-FILE ASSIGN TO DECISION
                  ORGANIZATION IS LINE SEQUENTIAL.
           SELECT FISOUT-FILE   ASSIGN TO FISOUT
                  ORGANIZATION IS LINE SEQUENTIAL.
       DATA DIVISION.
       FILE SECTION.
       FD  RUNDATE-FILE.
       01  RUNDATE-REC.
           05  RD-DATE                 PIC 9(8).
           05  FILLER                  PIC X(72).
       FD  PRICED-FILE.
       COPY LNPRCREC.
       FD  DECISION-FILE.
       COPY LNDECREC.
       FD  FISOUT-FILE.
       COPY FISACCT.
       WORKING-STORAGE SECTION.
       COPY LNAPPREC.
       01  WS-FLAGS.
           05  WS-EOF                  PIC X     VALUE 'N'.
               88  DEC-EOF             VALUE 'Y'.
       01  WS-WORK.
           05  WS-RUN-DATE             PIC 9(8).
           05  WS-FUND-DATE            PIC 9(8).
           05  WS-FUND-R REDEFINES WS-FUND-DATE.
               10  WS-FUND-YYYY        PIC 9(4).
               10  WS-FUND-MM          PIC 9(2).
               10  WS-FUND-DD          PIC 9(2).
           05  WS-PMT-DATE             PIC 9(8).
           05  WS-PMT-R REDEFINES WS-PMT-DATE.
               10  WS-PMT-YYYY         PIC 9(4).
               10  WS-PMT-MM           PIC 9(2).
               10  WS-PMT-DD           PIC 9(2).
           05  WS-MM-WORK              PIC 9(2).
       01  WS-COUNTS.
           05  WS-WRITTEN              PIC 9(7)  VALUE 0.
       PROCEDURE DIVISION.
       0000-MAIN.
           OPEN INPUT RUNDATE-FILE
           READ RUNDATE-FILE
           MOVE RD-DATE TO WS-RUN-DATE
           CLOSE RUNDATE-FILE
           COMPUTE WS-FUND-DATE = FUNCTION DATE-OF-INTEGER (
               FUNCTION INTEGER-OF-DATE (WS-RUN-DATE) + 30)
           MOVE WS-FUND-YYYY TO WS-PMT-YYYY
           COMPUTE WS-MM-WORK = WS-FUND-MM + 2
           IF WS-MM-WORK > 12
               SUBTRACT 12 FROM WS-MM-WORK
               ADD 1 TO WS-PMT-YYYY
           END-IF
           MOVE WS-MM-WORK TO WS-PMT-MM
           MOVE 01 TO WS-PMT-DD
           OPEN INPUT PRICED-FILE DECISION-FILE
                OUTPUT FISOUT-FILE
           PERFORM UNTIL DEC-EOF
               READ DECISION-FILE
                   AT END SET DEC-EOF TO TRUE
                   NOT AT END
                       READ PRICED-FILE
                       IF DEC-CODE = 'A'
                           PERFORM 2000-BUILD-MSG
                       END-IF
               END-READ
           END-PERFORM
           CLOSE PRICED-FILE DECISION-FILE FISOUT-FILE
           DISPLAY 'LNFIS030 WRITTEN  ' WS-WRITTEN
           STOP RUN.

       2000-BUILD-MSG.
           MOVE PRC-APP TO LN-APP-REC
           MOVE SPACES TO FIS-ACCT-CREATE-REQ
           MOVE 'ACCT' TO MSG-TYPE
           COMPUTE LOAN-NUMBER = 040000000000 + DEC-APP-ID
           MOVE BORR-TIN        TO BORROWER-SSN
           MOVE DEC-LOAN-TYPE   TO FIS-LOAN-TYPE
           MOVE DEC-LOAN-AMT    TO FIS-LOAN-AMOUNT
           MOVE DEC-FINAL-RATE  TO INTEREST-RATE
           MOVE TERM-MONTHS     TO FIS-TERM-MONTHS
           MOVE WS-PMT-DATE     TO FIRST-PMT-DATE
           MOVE WS-FUND-DATE    TO FUNDING-DATE
           ADD 1 TO WS-WRITTEN
           WRITE FIS-ACCT-CREATE-REQ.
