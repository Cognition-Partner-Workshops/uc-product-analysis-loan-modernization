       IDENTIFICATION DIVISION.
       PROGRAM-ID. LNUWR020.
      *----------------------------------------------------------------*
      * LOS NIGHTLY STEP020  AUTOMATED UNDERWRITING DECISION           *
      * APPLIES THE CREDIT CACHE WINDOW, THE BACK-END DTI AND THE      *
      * DECISION MATRIX BY LOAN TYPE AND SIZE.                         *
      *                                                                *
      * 2009-11 JL  CREDIT CACHE WINDOW 30 DAYS (WS-CACHE-DAYS)        *
      * 2016-08 RK  UNDER 2 YEARS EMPLOYMENT REFERS AN AUTO-APPROVE    *
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
       WORKING-STORAGE SECTION.
       COPY LNAPPREC.
       01  WS-FLAGS.
           05  WS-EOF                  PIC X     VALUE 'N'.
               88  PRICED-EOF          VALUE 'Y'.
       01  WS-WORK.
           05  WS-RUN-DATE             PIC 9(8).
           05  WS-CACHE-DAYS           PIC 9(3)  VALUE 30.
           05  WS-SCORE-AGE            PIC S9(7).
           05  WS-MONTHLY-INC          PIC 9(9)V99.
           05  WS-AUTO-SCORE           PIC 9(3).
           05  WS-AUTO-DTI             PIC 9(3)V99.
           05  WS-DECL-SCORE           PIC 9(3).
           05  WS-DECL-DTI             PIC 9(3)V99.
       01  WS-COUNTS.
           05  WS-READ                 PIC 9(7)  VALUE 0.
           05  WS-APPROVED             PIC 9(7)  VALUE 0.
           05  WS-REVIEW               PIC 9(7)  VALUE 0.
           05  WS-DECLINED             PIC 9(7)  VALUE 0.
           05  WS-REPULL               PIC 9(7)  VALUE 0.
       PROCEDURE DIVISION.
       0000-MAIN.
           OPEN INPUT RUNDATE-FILE
           READ RUNDATE-FILE
           MOVE RD-DATE TO WS-RUN-DATE
           CLOSE RUNDATE-FILE
           OPEN INPUT PRICED-FILE
                OUTPUT DECISION-FILE
           PERFORM UNTIL PRICED-EOF
               READ PRICED-FILE
                   AT END SET PRICED-EOF TO TRUE
                   NOT AT END PERFORM 2000-DECIDE
               END-READ
           END-PERFORM
           CLOSE PRICED-FILE DECISION-FILE
           DISPLAY 'LNUWR020 READ     ' WS-READ
           DISPLAY 'LNUWR020 APPROVED ' WS-APPROVED
           DISPLAY 'LNUWR020 REVIEW   ' WS-REVIEW
           DISPLAY 'LNUWR020 DECLINED ' WS-DECLINED
           DISPLAY 'LNUWR020 REPULL   ' WS-REPULL
           STOP RUN.

       2000-DECIDE.
           ADD 1 TO WS-READ
           MOVE PRC-APP TO LN-APP-REC
           INITIALIZE LN-DEC-REC
           MOVE APP-ID          TO DEC-APP-ID
           MOVE LOAN-TYPE       TO DEC-LOAN-TYPE
           MOVE PRC-PRODUCT     TO DEC-PRODUCT
           MOVE LOAN-AMT        TO DEC-LOAN-AMT
           MOVE PRC-FINAL-RATE  TO DEC-FINAL-RATE
           MOVE PRC-MONTHLY-PMT TO DEC-MONTHLY-PMT
           MOVE PRC-EXT-FEE-AMT TO DEC-EXT-FEE-AMT
           MOVE WS-RUN-DATE     TO DEC-DATE
           PERFORM 2100-DTI
           IF PRC-STATUS NOT = '00'
               MOVE 'D'     TO DEC-CODE
               MOVE 'PRICE' TO DEC-REASON
           ELSE
               COMPUTE WS-SCORE-AGE =
                   FUNCTION INTEGER-OF-DATE (WS-RUN-DATE)
                 - FUNCTION INTEGER-OF-DATE (SCORE-DATE)
               IF WS-SCORE-AGE > WS-CACHE-DAYS
                   MOVE 'R'     TO DEC-CODE
                   MOVE 'STALE' TO DEC-REASON
               ELSE
                   PERFORM 2200-MATRIX
               END-IF
           END-IF
           EVALUATE DEC-CODE
               WHEN 'A' ADD 1 TO WS-APPROVED
               WHEN 'M' ADD 1 TO WS-REVIEW
               WHEN 'D' ADD 1 TO WS-DECLINED
               WHEN 'R' ADD 1 TO WS-REPULL
           END-EVALUATE
           WRITE LN-DEC-REC.

       2100-DTI.
           COMPUTE WS-MONTHLY-INC ROUNDED = ANNUAL-INCOME / 12
           IF WS-MONTHLY-INC = 0
               MOVE 999.99 TO DEC-DTI
           ELSE
               COMPUTE DEC-DTI ROUNDED =
                   (MONTHLY-DEBT + PRC-MONTHLY-PMT) * 100
                   / WS-MONTHLY-INC
                   ON SIZE ERROR MOVE 999.99 TO DEC-DTI
               END-COMPUTE
           END-IF.

       2200-MATRIX.
           EVALUATE TRUE
               WHEN LOAN-IS-FHA
                   MOVE 680    TO WS-AUTO-SCORE
                   MOVE 40.00  TO WS-AUTO-DTI
                   MOVE 580    TO WS-DECL-SCORE
                   MOVE 50.00  TO WS-DECL-DTI
               WHEN LOAN-AMT < 500000
                   MOVE 740    TO WS-AUTO-SCORE
                   MOVE 36.00  TO WS-AUTO-DTI
                   MOVE 620    TO WS-DECL-SCORE
                   MOVE 43.00  TO WS-DECL-DTI
               WHEN OTHER
                   MOVE 760    TO WS-AUTO-SCORE
                   MOVE 33.00  TO WS-AUTO-DTI
                   MOVE 680    TO WS-DECL-SCORE
                   MOVE 43.00  TO WS-DECL-DTI
           END-EVALUATE
           EVALUATE TRUE
               WHEN CREDIT-SCORE < WS-DECL-SCORE
                   MOVE 'D'     TO DEC-CODE
                   MOVE 'SCORE' TO DEC-REASON
               WHEN DEC-DTI > WS-DECL-DTI
                   MOVE 'D'     TO DEC-CODE
                   MOVE 'DTI'   TO DEC-REASON
               WHEN CREDIT-SCORE >= WS-AUTO-SCORE
                AND DEC-DTI <= WS-AUTO-DTI
                   MOVE 'A'     TO DEC-CODE
                   MOVE 'AUTO'  TO DEC-REASON
               WHEN OTHER
                   MOVE 'M'     TO DEC-CODE
                   MOVE 'RVW'   TO DEC-REASON
           END-EVALUATE
           IF DEC-CODE = 'A' AND EMP-YEARS < 2.0
               MOVE 'M'     TO DEC-CODE
               MOVE 'EMPL'  TO DEC-REASON
           END-IF.
