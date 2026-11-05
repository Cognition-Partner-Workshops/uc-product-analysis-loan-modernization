package los.program;

import java.io.IOException;
import java.io.PrintStream;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.List;
import java.util.Locale;
import los.BatchProgram;
import los.Dd;
import los.record.Copybooks;
import los.record.FixedRecord;
import los.record.LineMode;
import los.record.RecordReader;
import los.record.RecordWriter;

/** STEP010 risk-based pricing: reads APPSIN, RATESIN, writes PRICED. */
public final class LNPRC010 implements BatchProgram {
    @Override
    public String name() {
        return "LNPRC010";
    }

    @Override
    public List<String> inputs() {
        return List.of("APPSIN", "RATESIN");
    }

    @Override
    public List<String> outputs() {
        return List.of("PRICED");
    }

    @Override
    public int run(Dd dd, PrintStream sysout) throws IOException {
        List<FixedRecord> rates = RecordReader.readAll(dd.path("RATESIN"), Copybooks.LNRATE);
        int read = 0;
        int priced = 0;
        try (RecordReader apps = RecordReader.open(dd.path("APPSIN"), Copybooks.LNAPPREC);
                RecordWriter output = RecordWriter.open(dd.path("PRICED"), LineMode.FIXED)) {
            for (FixedRecord app = apps.read(); app != null; app = apps.read()) {
                FixedRecord result = price(app, rates);
                output.write(result);
                read++;
                if (result.getString("PRC-STATUS").equals("00")) {
                    priced++;
                }
            }
        }
        sysout.printf(Locale.ROOT, "LNPRC010 READ     %07d%n", read);
        sysout.printf(Locale.ROOT, "LNPRC010 PRICED   %07d%n", priced);
        sysout.printf(Locale.ROOT, "LNPRC010 REJECTED %07d%n", read - priced);
        return 0;
    }

    static FixedRecord price(FixedRecord app, List<FixedRecord> rates) {
        FixedRecord result = FixedRecord.blank(Copybooks.LNPRCREC).initialize()
                .set("PRC-APP", app.toLine()).set("PRC-STATUS", "00");
        int term = app.getInt("TERM-MONTHS");
        String product = switch (app.getString("LOAN-TYPE")) {
            case "CNV" -> term == 360 ? "CNV30" : term == 180 ? "CNV15" : "";
            case "FHA" -> term == 360 ? "FHA30" : "";
            default -> "";
        };
        if (product.isEmpty()) {
            return result.set("PRC-STATUS", "E1");
        }
        result.set("PRC-PRODUCT", product);
        FixedRecord rate = rates.stream().filter(r -> r.getString("RATE-PRODUCT").equals(product)
                && r.getInt("RATE-LOCK-DAYS") == app.getInt("LOCK-DAYS")).findFirst().orElse(null);
        if (rate == null) {
            return result.set("PRC-STATUS", "E2");
        }
        BigDecimal base = rate.getDecimal("RATE-BASE");
        BigDecimal risk = riskAdjustment(app.getInt("CREDIT-SCORE"));
        result.set("PRC-BASE-RATE", base).set("PRC-RISK-ADJ", risk)
                .set("PRC-FINAL-RATE", base.add(risk));
        BigDecimal monthly = monthlyRate(result.getDecimal("PRC-FINAL-RATE"));
        BigDecimal factor = factor(monthly, term);
        if (factor.compareTo(BigDecimal.ONE) != 0) {
            BigDecimal payment = app.getDecimal("LOAN-AMT").multiply(monthly).multiply(factor)
                    .divide(factor.subtract(BigDecimal.ONE), 2, RoundingMode.HALF_UP);
            result.set("PRC-MONTHLY-PMT", payment);
        }
        BigDecimal fee = extensionPercent(app.getInt("LOCK-EXTS"));
        return result.set("PRC-EXT-FEE-PCT", fee)
                .set("PRC-EXT-FEE-AMT", app.getDecimal("LOAN-AMT").multiply(fee)
                        .movePointLeft(2), RoundingMode.HALF_UP);
    }

    static BigDecimal riskAdjustment(int score) {
        return new BigDecimal(score >= 760 ? "-0.25" : score >= 720 ? "0.00"
                : score >= 680 ? "0.50" : score >= 640 ? "1.00" : score >= 620 ? "1.75" : "2.50");
    }

    static BigDecimal monthlyRate(BigDecimal rate) {
        return rate.divide(new BigDecimal("1200"), 12, RoundingMode.DOWN);
    }

    static BigDecimal factor(BigDecimal monthly, int term) {
        return BigDecimal.ONE.add(monthly).pow(term).setScale(12, RoundingMode.DOWN)
                .remainder(new BigDecimal("10000"));
    }

    static BigDecimal extensionPercent(int extensions) {
        BigDecimal percent = extensions <= 3 ? new BigDecimal("0.125").multiply(BigDecimal.valueOf(extensions))
                : new BigDecimal("0.375").add(new BigDecimal("0.12").multiply(BigDecimal.valueOf(extensions - 3)));
        return percent.remainder(BigDecimal.TEN);
    }
}
