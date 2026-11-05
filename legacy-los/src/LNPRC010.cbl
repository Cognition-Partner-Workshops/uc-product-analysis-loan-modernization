       IDENTIFICATION DIVISION.
       PROGRAM-ID. LNPRC010.
      *----------------------------------------------------------------*
      * LOS NIGHTLY STEP010  RISK-BASED PRICING                        *
      * READS THE DAY'S APPLICATIONS AND THE 6AM RATE SHEET, PICKS THE *
      * PRODUCT, APPLIES THE CREDIT TIER ADJUSTMENT, COMPUTES THE      *
      * MONTHLY PAYMENT AND THE RATE LOCK EXTENSION FEE.               *
      *                                                                *
      * 2011-06 RK  FHA TIER 580-619 ADDED (+2.50), NOT IN THE BRD    *
      * 2014-02 MT  EXTENSIONS PAST THE THIRD PRICED FROM WS-ADDON-PCT *
      *----------------------------------------------------------------*
       ENVIRONMENT DIVISION.
       INPUT-OUTPUT SECTION.
       FILE-CONTROL.
           SELECT APPS-FILE   ASSIGN TO APPSIN
                  ORGANIZATION IS LINE SEQUENTIAL.
           SELECT RATES-FILE  ASSIGN TO RATESIN
                  ORGANIZATION IS LINE SEQUENTIAL.
           SELECT PRICED-FILE ASSIGN TO PRICED
                  ORGANIZATION IS LINE SEQUENTIAL.
       DATA DIVISION.
       FILE SECTION.
       FD  APPS-FILE.
       COPY LNAPPREC.
       FD  RATES-FILE.
       COPY LNRATE.
       FD  PRICED-FILE.
       COPY LNPRCREC.
       WORKING-STORAGE SECTION.
       01  WS-FLAGS.
           05  WS-APPS-EOF             PIC X     VALUE 'N'.
               88  APPS-EOF            VALUE 'Y'.
           05  WS-RATES-EOF            PIC X     VALUE 'N'.
               88  RATES-EOF           VALUE 'Y'.
       01  WS-RATE-TAB.
           05  WS-RATE-COUNT           PIC 9(3)  VALUE 0.
           05  WS-RATE-ENT OCCURS 50 TIMES INDEXED BY RX.
               10  WS-RT-PRODUCT       PIC X(5).
               10  WS-RT-LOCK          PIC 9(3).
               10  WS-RT-BASE          PIC 9(2)V9999.
       01  WS-EXT-FEE-TAB.
           05  FILLER                  PIC 9V999 VALUE 0.125.
           05  FILLER                  PIC 9V999 VALUE 0.250.
           05  FILLER                  PIC 9V999 VALUE 0.375.
       01  WS-EXT-FEE-R REDEFINES WS-EXT-FEE-TAB.
           05  WS-EXT-FEE              PIC 9V999 OCCURS 3 TIMES.
       01  WS-WORK.
           05  WS-ADDON-PCT            PIC 9V99.
           05  WS-MONTH-RATE           PIC 9V9(12).
           05  WS-FACTOR               PIC 9(4)V9(12).
           05  WS-FOUND                PIC X.
       01  WS-COUNTS.
           05  WS-READ                 PIC 9(7)  VALUE 0.
           05  WS-PRICED               PIC 9(7)  VALUE 0.
           05  WS-REJECTED             PIC 9(7)  VALUE 0.
       PROCEDURE DIVISION.
       0000-MAIN.
           PERFORM 1000-LOAD-RATES
           OPEN INPUT APPS-FILE
                OUTPUT PRICED-FILE
           PERFORM UNTIL APPS-EOF
               READ APPS-FILE
                   AT END SET APPS-EOF TO TRUE
                   NOT AT END PERFORM 2000-PRICE-APP
               END-READ
           END-PERFORM
           CLOSE APPS-FILE PRICED-FILE
           DISPLAY 'LNPRC010 READ     ' WS-READ
           DISPLAY 'LNPRC010 PRICED   ' WS-PRICED
           DISPLAY 'LNPRC010 REJECTED ' WS-REJECTED
           STOP RUN.

       1000-LOAD-RATES.
           OPEN INPUT RATES-FILE
           PERFORM UNTIL RATES-EOF
               READ RATES-FILE
                   AT END SET RATES-EOF TO TRUE
                   NOT AT END
                       ADD 1 TO WS-RATE-COUNT
                       SET RX TO WS-RATE-COUNT
                       MOVE RATE-PRODUCT   TO WS-RT-PRODUCT (RX)
                       MOVE RATE-LOCK-DAYS TO WS-RT-LOCK (RX)
                       MOVE RATE-BASE      TO WS-RT-BASE (RX)
               END-READ
           END-PERFORM
           CLOSE RATES-FILE.

       2000-PRICE-APP.
           ADD 1 TO WS-READ
           INITIALIZE LN-PRC-REC
           MOVE LN-APP-REC TO PRC-APP
           MOVE '00' TO PRC-STATUS
           EVALUATE TRUE
               WHEN LOAN-IS-CNV AND TERM-MONTHS = 360
                   MOVE 'CNV30' TO PRC-PRODUCT
               WHEN LOAN-IS-CNV AND TERM-MONTHS = 180
                   MOVE 'CNV15' TO PRC-PRODUCT
               WHEN LOAN-IS-FHA AND TERM-MONTHS = 360
                   MOVE 'FHA30' TO PRC-PRODUCT
               WHEN OTHER
                   MOVE 'E1' TO PRC-STATUS
           END-EVALUATE
           IF PRC-STATUS = '00'
               PERFORM 2100-FIND-BASE
           END-IF
           IF PRC-STATUS = '00'
               PERFORM 2200-RISK-ADJ
               COMPUTE PRC-FINAL-RATE = PRC-BASE-RATE + PRC-RISK-ADJ
               PERFORM 2300-PAYMENT
               PERFORM 2400-EXT-FEE
               ADD 1 TO WS-PRICED
           ELSE
               ADD 1 TO WS-REJECTED
           END-IF
           WRITE LN-PRC-REC.

       2100-FIND-BASE.
           MOVE 'N' TO WS-FOUND
           PERFORM VARYING RX FROM 1 BY 1
                   UNTIL RX > WS-RATE-COUNT OR WS-FOUND = 'Y'
               IF WS-RT-PRODUCT (RX) = PRC-PRODUCT
                  AND WS-RT-LOCK (RX) = LOCK-DAYS
                   MOVE WS-RT-BASE (RX) TO PRC-BASE-RATE
                   MOVE 'Y' TO WS-FOUND
               END-IF
           END-PERFORM
           IF WS-FOUND = 'N'
               MOVE 'E2' TO PRC-STATUS
           END-IF.

       2200-RISK-ADJ.
           EVALUATE TRUE
               WHEN CREDIT-SCORE >= 760
                   MOVE -0.25 TO PRC-RISK-ADJ
               WHEN CREDIT-SCORE >= 720
                   MOVE 0 TO PRC-RISK-ADJ
               WHEN CREDIT-SCORE >= 680
                   MOVE 0.50 TO PRC-RISK-ADJ
               WHEN CREDIT-SCORE >= 640
                   MOVE 1.00 TO PRC-RISK-ADJ
               WHEN CREDIT-SCORE >= 620
                   MOVE 1.75 TO PRC-RISK-ADJ
               WHEN OTHER
                   MOVE 2.50 TO PRC-RISK-ADJ
           END-EVALUATE.

       2300-PAYMENT.
           COMPUTE WS-MONTH-RATE = PRC-FINAL-RATE / 1200
           COMPUTE WS-FACTOR = (1 + WS-MONTH-RATE) ** TERM-MONTHS
           COMPUTE PRC-MONTHLY-PMT ROUNDED =
               LOAN-AMT * WS-MONTH-RATE * WS-FACTOR / (WS-FACTOR - 1).

       2400-EXT-FEE.
           MOVE 0.125 TO WS-ADDON-PCT
           EVALUATE TRUE
               WHEN LOCK-EXTS = 0
                   MOVE 0 TO PRC-EXT-FEE-PCT
               WHEN LOCK-EXTS <= 3
                   MOVE WS-EXT-FEE (LOCK-EXTS) TO PRC-EXT-FEE-PCT
               WHEN OTHER
                   COMPUTE PRC-EXT-FEE-PCT = WS-EXT-FEE (3)
                       + (LOCK-EXTS - 3) * WS-ADDON-PCT
           END-EVALUATE
           COMPUTE PRC-EXT-FEE-AMT ROUNDED =
               LOAN-AMT * PRC-EXT-FEE-PCT / 100.
