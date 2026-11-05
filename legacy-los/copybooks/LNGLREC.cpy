      *----------------------------------------------------------------*
      * LNGLREC   GENERAL LEDGER POSTING LINE, FIXED 85 BYTES          *
      *----------------------------------------------------------------*
       01  LN-GL-REC.
           05  GL-POST-DATE            PIC 9(8).
           05  GL-ACCOUNT              PIC 9(12).
           05  GL-DRCR                 PIC X(2).
           05  GL-AMOUNT               PIC 9(13)V99.
           05  GL-LOAN-NUMBER          PIC 9(13).
           05  GL-DESC                 PIC X(30).
           05  GL-COST-CENTER          PIC 9(5).
