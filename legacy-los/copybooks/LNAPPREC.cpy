      *----------------------------------------------------------------*
      * LNAPPREC  LOAN APPLICATION INTAKE RECORD (APPSIN)              *
      * ONE RECORD PER APPLICATION, FIXED 150 BYTES                    *
      *----------------------------------------------------------------*
       01  LN-APP-REC.
           05  APP-ID                  PIC 9(10).
           05  BORR-ID                 PIC 9(9).
           05  BORR-TIN                PIC 9(9).
           05  BORR-LAST-NAME          PIC X(15).
           05  BORR-FIRST-NAME         PIC X(12).
           05  LOAN-TYPE               PIC X(3).
               88  LOAN-IS-CNV         VALUE 'CNV'.
               88  LOAN-IS-FHA         VALUE 'FHA'.
           05  LOAN-PURPOSE            PIC X(1).
           05  LOAN-AMT                PIC 9(9)V99.
           05  TERM-MONTHS             PIC 9(3).
           05  PROP-STATE              PIC X(2).
           05  PROP-TYPE               PIC X(1).
           05  ANNUAL-INCOME           PIC 9(9)V99.
           05  MONTHLY-DEBT            PIC 9(7)V99.
           05  CREDIT-SCORE            PIC 9(3).
           05  SCORE-DATE              PIC 9(8).
           05  EMP-YEARS               PIC 9(2)V9.
           05  LOCK-DAYS               PIC 9(3).
           05  LOCK-EXTS               PIC 9(2).
           05  APP-DATE                PIC 9(8).
           05  APPL-SEX                PIC X(1).
           05  APPL-RACE               PIC X(1).
           05  FILLER                  PIC X(25).
