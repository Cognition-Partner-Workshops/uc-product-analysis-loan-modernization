      *----------------------------------------------------------------*
      * FISACCT   CORE BANKING ACCOUNT CREATE REQUEST, FIXED 114 BYTES *
      * LAYOUT OWNED BY THE CORE BANKING TEAM, DO NOT CHANGE           *
      *----------------------------------------------------------------*
       01  FIS-ACCT-CREATE-REQ.
           05  MSG-TYPE                PIC X(4).
           05  LOAN-NUMBER             PIC 9(12).
           05  BORROWER-SSN            PIC 9(9).
           05  FIS-LOAN-TYPE           PIC X(3).
           05  FIS-LOAN-AMOUNT         PIC 9(9)V99.
           05  INTEREST-RATE           PIC 9(2)V9999.
           05  FIS-TERM-MONTHS         PIC 9(3).
           05  FIRST-PMT-DATE          PIC 9(8).
           05  FUNDING-DATE            PIC 9(8).
           05  FILLER                  PIC X(50).
