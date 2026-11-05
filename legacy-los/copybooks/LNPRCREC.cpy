      *----------------------------------------------------------------*
      * LNPRCREC  PRICED APPLICATION (PRICED), FIXED 200 BYTES         *
      * THE INTAKE RECORD AS READ, FOLLOWED BY THE PRICING RESULT      *
      *----------------------------------------------------------------*
       01  LN-PRC-REC.
           05  PRC-APP                 PIC X(150).
           05  PRC-PRODUCT             PIC X(5).
           05  PRC-BASE-RATE           PIC 9(2)V9999.
           05  PRC-RISK-ADJ            PIC S9(1)V9999
                                       SIGN LEADING SEPARATE.
           05  PRC-FINAL-RATE          PIC 9(2)V9999.
           05  PRC-MONTHLY-PMT         PIC 9(7)V99.
           05  PRC-EXT-FEE-PCT         PIC 9(1)V999.
           05  PRC-EXT-FEE-AMT         PIC 9(7)V99.
           05  PRC-STATUS              PIC X(2).
           05  FILLER                  PIC X(3).
