       IDENTIFICATION DIVISION.
       PROGRAM-ID. LNGL040.
      *----------------------------------------------------------------*
      * LOS NIGHTLY STEP040  GENERAL LEDGER POSTING EXTRACT            *
      * FOR EACH AUTO-APPROVED LOAN: FUNDING DR/CR PAIR, AND WHEN A    *
      * LOCK EXTENSION FEE WAS CHARGED, A FEE DR/CR PAIR.              *
      * CONTROL TOTALS MUST BALANCE OR THE STEP ENDS RC=12.            *
      *----------------------------------------------------------------*
       ENVIRONMENT DIVISION.
       INPUT-OUTPUT SECTION.
       FILE-CONTROL.
           SELECT RUNDATE-FILE  ASSIGN TO RUNDATE
                  ORGANIZATION IS LINE SEQUENTIAL.
           SELECT DECISION-FILE ASSIGN TO DECISION
                  ORGANIZATION IS LINE SEQUENTIAL.
           SELECT GLOUT-FILE    ASSIGN TO GLOUT
                  ORGANIZATION IS LINE SEQUENTIAL.
       DATA DIVISION.
       FILE SECTION.
       FD  RUNDATE-FILE.
       01  RUNDATE-REC.
           05  RD-DATE                 PIC 9(8).
           05  FILLER                  PIC X(72).
       FD  DECISION-FILE.
       COPY LNDECREC.
       FD  GLOUT-FILE.
       COPY LNGLREC.
       WORKING-STORAGE SECTION.
       01  WS-FLAGS.
           05  WS-EOF                  PIC X     VALUE 'N'.
               88  DEC-EOF             VALUE 'Y'.
       01  WS-WORK.
           05  WS-RUN-DATE             PIC 9(8).
           05  WS-LOAN-NUMBER          PIC 9(13).
           05  WS-COST-CENTER          PIC 9(5).
       01  WS-TOTALS.
           05  WS-LINES                PIC 9(7)      VALUE 0.
           05  WS-TOTAL-DR             PIC 9(13)V99  VALUE 0.
           05  WS-TOTAL-CR             PIC 9(13)V99  VALUE 0.
           05  WS-TOTAL-DR-E           PIC Z(12)9.99.
           05  WS-TOTAL-CR-E           PIC Z(12)9.99.
       PROCEDURE DIVISION.
       0000-MAIN.
           OPEN INPUT RUNDATE-FILE
           READ RUNDATE-FILE
           MOVE RD-DATE TO WS-RUN-DATE
           CLOSE RUNDATE-FILE
           OPEN INPUT DECISION-FILE
                OUTPUT GLOUT-FILE
           PERFORM UNTIL DEC-EOF
               READ DECISION-FILE
                   AT END SET DEC-EOF TO TRUE
                   NOT AT END
                       IF DEC-CODE = 'A'
                           PERFORM 2000-POST-LOAN
                       END-IF
               END-READ
           END-PERFORM
           CLOSE DECISION-FILE GLOUT-FILE
           MOVE WS-TOTAL-DR TO WS-TOTAL-DR-E
           MOVE WS-TOTAL-CR TO WS-TOTAL-CR-E
           DISPLAY 'LNGL040  LINES    ' WS-LINES
           DISPLAY 'LNGL040  TOTAL DR ' WS-TOTAL-DR-E
           DISPLAY 'LNGL040  TOTAL CR ' WS-TOTAL-CR-E
           IF WS-TOTAL-DR NOT = WS-TOTAL-CR
               DISPLAY 'LNGL040  OUT OF BALANCE'
               MOVE 12 TO RETURN-CODE
           END-IF
           STOP RUN.

       2000-POST-LOAN.
           COMPUTE WS-LOAN-NUMBER = 040000000000 + DEC-APP-ID
           IF DEC-LOAN-TYPE = 'FHA'
               MOVE 10020 TO WS-COST-CENTER
           ELSE
               MOVE 10010 TO WS-COST-CENTER
           END-IF
           MOVE 140100000000 TO GL-ACCOUNT
           MOVE 'DR' TO GL-DRCR
           MOVE DEC-LOAN-AMT TO GL-AMOUNT
           MOVE SPACES TO GL-DESC
           STRING 'LOAN FUNDING ' DEC-PRODUCT
                  DELIMITED BY SIZE INTO GL-DESC
           PERFORM 3000-WRITE-LINE
           MOVE 210500000000 TO GL-ACCOUNT
           MOVE 'CR' TO GL-DRCR
           PERFORM 3000-WRITE-LINE
           IF DEC-EXT-FEE-AMT > 0
               MOVE 120300000000 TO GL-ACCOUNT
               MOVE 'DR' TO GL-DRCR
               MOVE DEC-EXT-FEE-AMT TO GL-AMOUNT
               MOVE 'RATE LOCK EXTENSION FEE' TO GL-DESC
               PERFORM 3000-WRITE-LINE
               MOVE 410200000000 TO GL-ACCOUNT
               MOVE 'CR' TO GL-DRCR
               PERFORM 3000-WRITE-LINE
           END-IF.

       3000-WRITE-LINE.
           MOVE WS-RUN-DATE    TO GL-POST-DATE
           MOVE WS-LOAN-NUMBER TO GL-LOAN-NUMBER
           MOVE WS-COST-CENTER TO GL-COST-CENTER
           IF GL-DRCR = 'DR'
               ADD GL-AMOUNT TO WS-TOTAL-DR
           ELSE
               ADD GL-AMOUNT TO WS-TOTAL-CR
           END-IF
           ADD 1 TO WS-LINES
           WRITE LN-GL-REC.
