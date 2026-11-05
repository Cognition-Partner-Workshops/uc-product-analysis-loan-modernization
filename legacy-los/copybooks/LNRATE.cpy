      *----------------------------------------------------------------*
      * LNRATE    DAILY RATE SHEET RECORD (RATESIN), FIXED 40 BYTES    *
      * RATE-PRODUCT CNV30 CNV15 FHA30, OR APOR FOR THE HMDA BENCHMARK *
      *----------------------------------------------------------------*
       01  LN-RATE-REC.
           05  RATE-PRODUCT            PIC X(5).
           05  RATE-LOCK-DAYS          PIC 9(3).
           05  RATE-BASE               PIC 9(2)V9999.
           05  RATE-EFF-DATE           PIC 9(8).
           05  FILLER                  PIC X(18).
