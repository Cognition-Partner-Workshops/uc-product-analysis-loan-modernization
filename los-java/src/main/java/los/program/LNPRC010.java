package los.program;

import java.io.IOException;
import java.io.PrintStream;
import java.math.BigDecimal;
import java.math.MathContext;
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
    private static final MathContext PRECISION = new MathContext(50);

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
        long read = 0;
        long priced = 0;
        try (RecordReader apps = RecordReader.open(dd.path("APPSIN"), Copybooks.LNAPPREC);
                RecordWriter out = RecordWriter.open(dd.path("PRICED"), LineMode.FIXED)) {
            for (FixedRecord app = apps.read(); app != null; app = apps.read()) {
                FixedRecord result = price(app, rates);
                read++;
                if (result.getString("PRC-STATUS").equals("00")) {
                    priced++;
                }
                out.write(result);
            }
        }
        sysout.printf(Locale.ROOT, "LNPRC010 READ     %07d\n", read % 10_000_000);
        sysout.printf(Locale.ROOT, "LNPRC010 PRICED   %07d\n", priced % 10_000_000);
        sysout.printf(Locale.ROOT, "LNPRC010 REJECTED %07d\n", (read - priced) % 10_000_000);
        return 0;
    }

    private static FixedRecord price(FixedRecord app, List<FixedRecord> rates) {
        FixedRecord out = FixedRecord.blank(Copybooks.LNPRCREC).initialize()
                .set("PRC-APP", app.toLine()).set("PRC-STATUS", "00");
        int term = app.getInt("TERM-MONTHS");
        String type = app.getString("LOAN-TYPE");
        String product = type.equals("CNV") && term == 360 ? "CNV30"
                : type.equals("CNV") && term == 180 ? "CNV15"
                : type.equals("FHA") && term == 360 ? "FHA30" : "";
        if (product.isEmpty()) {
            return out.set("PRC-STATUS", "E1");
        }
        out.set("PRC-PRODUCT", product);
        FixedRecord rate = rates.stream().filter(r -> r.getString("RATE-PRODUCT").equals(product)
                && r.getInt("RATE-LOCK-DAYS") == app.getInt("LOCK-DAYS")).findFirst().orElse(null);
        if (rate == null) {
            return out.set("PRC-STATUS", "E2");
        }
        int score = app.getInt("CREDIT-SCORE");
        String adjustment = score >= 760 ? "-0.25" : score >= 720 ? "0"
                : score >= 680 ? "0.50" : score >= 640 ? "1.00" : score >= 620 ? "1.75" : "2.50";
        out.set("PRC-BASE-RATE", rate.getDecimal("RATE-BASE"));
        out.set("PRC-RISK-ADJ", new BigDecimal(adjustment));
        out.set("PRC-FINAL-RATE", out.getDecimal("PRC-BASE-RATE").add(out.getDecimal("PRC-RISK-ADJ")));
        BigDecimal monthly = out.getDecimal("PRC-FINAL-RATE")
                .divide(BigDecimal.valueOf(1200), 12, RoundingMode.DOWN);
        BigDecimal factor = BigDecimal.ONE.add(monthly).pow(term, PRECISION)
                .setScale(12, RoundingMode.DOWN).remainder(BigDecimal.valueOf(10_000));
        BigDecimal amount = app.getDecimal("LOAN-AMT");
        if (factor.compareTo(BigDecimal.ONE) != 0) {
            out.set("PRC-MONTHLY-PMT", amount.multiply(monthly).multiply(factor)
                    .divide(factor.subtract(BigDecimal.ONE), PRECISION), RoundingMode.HALF_UP);
        }
        int extensions = app.getInt("LOCK-EXTS");
        BigDecimal fee = extensions <= 3 ? new BigDecimal("0.125").multiply(BigDecimal.valueOf(extensions))
                : new BigDecimal("0.375").add(new BigDecimal("0.12").multiply(BigDecimal.valueOf(extensions - 3)));
        out.set("PRC-EXT-FEE-PCT", fee);
        out.set("PRC-EXT-FEE-AMT", amount.multiply(out.getDecimal("PRC-EXT-FEE-PCT"))
                .movePointLeft(2), RoundingMode.HALF_UP);
        return out;
    }
}
