       IDENTIFICATION DIVISION.
       PROGRAM-ID. LNHMD050.
      *----------------------------------------------------------------*
      * LOS NIGHTLY STEP050  HMDA COMPLIANCE EXTRACT (PIPE DELIMITED)  *
      * ONE ROW PER APPLICATION WITH FINAL ACTION (APPROVED OR         *
      * DECLINED). MANUAL REVIEW AND RE-PULL ARE STILL OPEN AND ARE    *
      * LEFT FOR A LATER RUN. AMOUNT IN THOUSANDS ROUNDED, INCOME IN   *
      * THOUSANDS TRUNCATED, RATE SPREAD = FINAL RATE - APOR.          *
      *----------------------------------------------------------------*
       ENVIRONMENT DIVISION.
       INPUT-OUTPUT SECTION.
       FILE-CONTROL.
           SELECT RATES-FILE    ASSIGN TO RATESIN
                  ORGANIZATION IS LINE SEQUENTIAL.
           SELECT PRICED-FILE   ASSIGN TO PRICED
                  ORGANIZATION IS LINE SEQUENTIAL.
           SELECT DECISION-FILE ASSIGN TO DECISION
                  ORGANIZATION IS LINE SEQUENTIAL.
           SELECT HMDAOUT-FILE  ASSIGN TO HMDAOUT
                  ORGANIZATION IS LINE SEQUENTIAL.
       DATA DIVISION.
       FILE SECTION.
       FD  RATES-FILE.
       COPY LNRATE.
       FD  PRICED-FILE.
       COPY LNPRCREC.
       FD  DECISION-FILE.
       COPY LNDECREC.
       FD  HMDAOUT-FILE.
       01  HMDA-LINE                   PIC X(160).
       WORKING-STORAGE SECTION.
       COPY LNAPPREC.
       01  WS-FLAGS.
           05  WS-EOF                  PIC X     VALUE 'N'.
               88  DEC-EOF             VALUE 'Y'.
           05  WS-RATES-EOF            PIC X     VALUE 'N'.
               88  RATES-EOF           VALUE 'Y'.
       01  WS-WORK.
           05  WS-APOR                 PIC 9(2)V9999 VALUE 0.
           05  WS-ACTION               PIC X(1).
           05  WS-ACTION-DATE          PIC X(10).
           05  WS-TYPE                 PIC X(1).
           05  WS-PURPOSE              PIC X(2).
           05  WS-AMT-K                PIC 9(7).
           05  WS-AMT-K-E              PIC Z(6)9.
           05  WS-INC-K                PIC 9(7).
           05  WS-INC-K-E              PIC Z(6)9.
           05  WS-SPREAD               PIC S9(2)V999.
           05  WS-SPREAD-E             PIC -(2)9.999.
           05  WS-SPREAD-X             PIC X(8).
           05  WS-PTR                  PIC 9(3).
       01  WS-COUNTS.
           05  WS-ROWS                 PIC 9(7)  VALUE 0.
       PROCEDURE DIVISION.
       0000-MAIN.
           OPEN INPUT RATES-FILE
           PERFORM UNTIL RATES-EOF
               READ RATES-FILE
                   AT END SET RATES-EOF TO TRUE
                   NOT AT END
                       IF RATE-PRODUCT = 'APOR '
                           MOVE RATE-BASE TO WS-APOR
                       END-IF
               END-READ
           END-PERFORM
           CLOSE RATES-FILE
           OPEN INPUT PRICED-FILE DECISION-FILE
                OUTPUT HMDAOUT-FILE
           MOVE SPACES TO HMDA-LINE
           STRING 'ApplicationID|ActionTaken|ActionDate|LoanType|'
                  'LoanPurpose|LoanAmount|PropertyType|PropertyState|'
                  'ApplicantRace|ApplicantSex|ApplicantIncome|'
                  'RateSpread'
                  DELIMITED BY SIZE INTO HMDA-LINE
           WRITE HMDA-LINE
           PERFORM UNTIL DEC-EOF
               READ DECISION-FILE
                   AT END SET DEC-EOF TO TRUE
                   NOT AT END
                       READ PRICED-FILE
                       IF DEC-CODE = 'A' OR DEC-CODE = 'D'
                           PERFORM 2000-BUILD-ROW
                       END-IF
               END-READ
           END-PERFORM
           CLOSE PRICED-FILE DECISION-FILE HMDAOUT-FILE
           DISPLAY 'LNHMD050 ROWS     ' WS-ROWS
           STOP RUN.

       2000-BUILD-ROW.
           MOVE PRC-APP TO LN-APP-REC
           IF DEC-CODE = 'A'
               MOVE '1' TO WS-ACTION
           ELSE
               MOVE '3' TO WS-ACTION
           END-IF
           STRING DEC-DATE (1:4) '-' DEC-DATE (5:2) '-'
                  DEC-DATE (7:2) DELIMITED BY SIZE
                  INTO WS-ACTION-DATE
           IF LOAN-IS-FHA
               MOVE '2' TO WS-TYPE
           ELSE
               MOVE '1' TO WS-TYPE
           END-IF
           IF LOAN-PURPOSE = 'R'
               MOVE '31' TO WS-PURPOSE
           ELSE
               MOVE '1 ' TO WS-PURPOSE
           END-IF
           COMPUTE WS-AMT-K ROUNDED = LOAN-AMT / 1000
           COMPUTE WS-INC-K = ANNUAL-INCOME / 1000
           MOVE WS-AMT-K TO WS-AMT-K-E
           MOVE WS-INC-K TO WS-INC-K-E
           IF DEC-CODE = 'A'
               COMPUTE WS-SPREAD ROUNDED = DEC-FINAL-RATE - WS-APOR
               MOVE WS-SPREAD TO WS-SPREAD-E
               MOVE FUNCTION TRIM (WS-SPREAD-E) TO WS-SPREAD-X
           ELSE
               MOVE 'NA' TO WS-SPREAD-X
           END-IF
           MOVE SPACES TO HMDA-LINE
           MOVE 1 TO WS-PTR
           STRING DEC-APP-ID                   DELIMITED BY SIZE
                  '|' WS-ACTION                DELIMITED BY SIZE
                  '|' WS-ACTION-DATE           DELIMITED BY SIZE
                  '|' WS-TYPE                  DELIMITED BY SIZE
                  '|' FUNCTION TRIM (WS-PURPOSE) DELIMITED BY SIZE
                  '|' FUNCTION TRIM (WS-AMT-K-E) DELIMITED BY SIZE
                  '|' PROP-TYPE                DELIMITED BY SIZE
                  '|' PROP-STATE               DELIMITED BY SIZE
                  '|' APPL-RACE                DELIMITED BY SIZE
                  '|' APPL-SEX                 DELIMITED BY SIZE
                  '|' FUNCTION TRIM (WS-INC-K-E) DELIMITED BY SIZE
                  '|' FUNCTION TRIM (WS-SPREAD-X) DELIMITED BY SIZE
                  INTO HMDA-LINE WITH POINTER WS-PTR
           END-STRING
           ADD 1 TO WS-ROWS
           WRITE HMDA-LINE.
