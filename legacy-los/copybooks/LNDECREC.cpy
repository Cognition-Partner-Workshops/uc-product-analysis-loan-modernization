      *----------------------------------------------------------------*
      * LNDECREC  UNDERWRITING DECISION (DECISION), FIXED 80 BYTES     *
      * DEC-CODE  A AUTO-APPROVE  M MANUAL REVIEW  D AUTO-DECLINE      *
      *           R CREDIT RE-PULL (SCORE OLDER THAN CACHE WINDOW)     *
      *----------------------------------------------------------------*
       01  LN-DEC-REC.
           05  DEC-APP-ID              PIC 9(10).
           05  DEC-CODE                PIC X(1).
           05  DEC-REASON              PIC X(5).
           05  DEC-LOAN-TYPE           PIC X(3).
           05  DEC-PRODUCT             PIC X(5).
           05  DEC-LOAN-AMT            PIC 9(9)V99.
           05  DEC-FINAL-RATE          PIC 9(2)V9999.
           05  DEC-MONTHLY-PMT         PIC 9(7)V99.
           05  DEC-DTI                 PIC 9(3)V99.
           05  DEC-EXT-FEE-AMT         PIC 9(7)V99.
           05  DEC-DATE                PIC 9(8).
           05  FILLER                  PIC X(8).
