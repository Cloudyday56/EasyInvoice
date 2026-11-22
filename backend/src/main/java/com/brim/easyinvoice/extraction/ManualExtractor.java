package com.brim.easyinvoice.extraction;

import com.brim.easyinvoice.model.SourceDocument;
import org.apache.pdfbox.Loader;
import org.apache.pdfbox.pdmodel.PDDocument;
import org.apache.pdfbox.text.PDFTextStripper;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;

import java.io.IOException;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Rule-based extractor for PDFs that have a text layer. No network, no model.
 *
 * It looks for labels instead of fixed positions, so it covers every layout in data/incoming_invoices
 * (English and French; "Invoice #", "Invoice Number", "INV NO", "Invoice No.", "Facture n°").
 * Scans and images have no text layer: they come back empty, and the pipeline parks them in the
 * extraction review queue (only the LLM extractor can read those).
 *
 * Confidence comes from arithmetic: lines must add up to the subtotal and subtotal + taxes to the total.
 */
@Component
@ConditionalOnProperty(name = "app.extractor", havingValue = "manual", matchIfMissing = true)
public class ManualExtractor implements Extractor {

    private static final Pattern MONEY = Pattern.compile(
            "-?\\$-?\\d{1,3}(?:,\\d{3})*\\.\\d{2}"              // $1,234.56  -$12.00  $-12.00
                    + "|-?\\d{1,3}(?: \\d{3})*,\\d{2} ?\\$");     // 1 234,56 $  (French)
    private static final String M = "(" + MONEY.pattern() + ")";

    private static final String DATE = "(\\d{4}-\\d{2}-\\d{2})";
    private static final int FLAGS = Pattern.CASE_INSENSITIVE;

    private static final Pattern INVOICE_NUMBER = Pattern.compile(
            "(?:Invoice #|Invoice Number|Invoice No\\.?|INV NO|Facture n°)\\s*:?\\s*([A-Z0-9][A-Z0-9-]*)", FLAGS);
    private static final Pattern INVOICE_DATE = Pattern.compile(
            "(?<!due )\\b(?:Invoice date|Issued|Date)\\b\\s*:?\\s*" + DATE, FLAGS);
    private static final Pattern DUE_DATE = Pattern.compile(
            "(?:Due date|Due by|Payment due|Due|Échéance)\\s*:?\\s*" + DATE, FLAGS);
    private static final Pattern PO = Pattern.compile(
            "(?:PO REF|Your PO|Customer PO|PO #)\\s*:?\\s*([A-Z0-9][A-Z0-9-]*)?\\s*$", FLAGS);
    private static final Pattern VENDOR_GST = Pattern.compile("(?:GST/HST|TPS/TVH)\\s+(\\d{9}RT\\d{4})");
    private static final Pattern VENDOR_QST = Pattern.compile("(?:QST|TVQ)\\s+(\\d{10}TQ\\d{4})");
    private static final Pattern TITLE_SUFFIX = Pattern.compile("\\s+(?:TAX INVOICE|INVOICE|FACTURE|CREDIT NOTE)$");
    private static final Pattern REMIT_BANK = Pattern.compile(
            "Inst\\.?\\s*(\\d+)\\s+Transit\\s*(\\d+)\\s+Acct\\s*(\\d+)", FLAGS);
    private static final Pattern PROVINCE = Pattern.compile("\\b(AB|BC|MB|NB|NL|NS|NT|NU|ON|PE|QC|SK|YT)\\s+[A-Z]\\d[A-Z]\\b");
    private static final Pattern CURRENCY_TOTAL = Pattern.compile("Total (CAD|USD)\\s+" + M);
    private static final Pattern SUBTOTAL = Pattern.compile("(?:Subtotal|Sous-total)\\s+" + M, FLAGS);
    private static final Pattern FEDERAL_TAX = Pattern.compile("^(?:GST|HST|TPS|TVH)\\b.*?" + M + "$");
    private static final Pattern PROVINCIAL_TAX = Pattern.compile("^(?:QST|TVQ|PST)\\b.*?" + M + "$");
    private static final Pattern LINE_ITEM = Pattern.compile("^(.+?)\\s+(\\d+(?:\\.\\d+)?)\\s+" + M + "\\s+" + M + "$");

    @Override
    public String name() {
        return "manual";
    }

    @Override
    public ExtractedInvoice extract(SourceDocument document, byte[] fileBytes) {
        String text = readText(fileBytes);
        if (text.isBlank()) return empty();
        return parse(text);
    }

    // ---------- reading ----------

    /** The PDF's text layer, or "" when the file is not a PDF or has no text (a scan). */
    private static String readText(byte[] bytes) {
        try (PDDocument pdf = Loader.loadPDF(bytes)) {
            PDFTextStripper stripper = new PDFTextStripper();
            stripper.setSortByPosition(true);
            return stripper.getText(pdf).replace(' ', ' ').replace(' ', ' ');
        } catch (IOException | RuntimeException notAPdf) {
            return "";
        }
    }

    private static ExtractedInvoice empty() {
        return new ExtractedInvoice(null, null, null, null, null, null, null, null, null, null, null, null,
                null, null, null, null, null, List.of(), 0, List.of());
    }

    // ---------- parsing ----------

    static ExtractedInvoice parse(String text) {
        List<String> lines = text.lines().map(l -> l.strip().replaceAll("\\s+", " ")).filter(l -> !l.isEmpty()).toList();
        String all = String.join("\n", lines);
        String head = headerOf(lines);

        String vendorName = TITLE_SUFFIX.matcher(lines.get(0)).replaceFirst("");
        String documentType = head.contains("CREDIT NOTE") ? "CREDIT_NOTE" : "INVOICE";
        String invoiceNumber = find(INVOICE_NUMBER, head);
        LocalDate invoiceDate = date(find(INVOICE_DATE, head));
        LocalDate dueDate = date(find(DUE_DATE, head));
        String poNumber = poOf(lines);
        String gst = find(VENDOR_GST, head);
        String qst = find(VENDOR_QST, head);
        String shipTo = shipToProvince(lines);
        Matcher bank = REMIT_BANK.matcher(all);
        boolean hasBank = bank.find();   // card-only vendors print "pay by credit card" instead

        Matcher total = CURRENCY_TOTAL.matcher(all);
        String currency = null;
        BigDecimal totalAmount = null;
        if (total.find()) {
            currency = total.group(1);
            totalAmount = money(total.group(2));
        }
        BigDecimal subtotal = money(find(SUBTOTAL, all));
        BigDecimal gstHst = BigDecimal.ZERO;
        BigDecimal pstQst = BigDecimal.ZERO;
        boolean hst = false;
        for (String line : lines) {
            hst |= line.startsWith("HST") || line.startsWith("TVH");
            Matcher federal = FEDERAL_TAX.matcher(line);
            Matcher provincial = PROVINCIAL_TAX.matcher(line);
            if (federal.find()) gstHst = gstHst.add(money(federal.group(1)));
            else if (provincial.find()) pstQst = pstQst.add(money(provincial.group(1)));
        }

        String taxCode = taxCode(currency, hst, gstHst, pstQst);
        List<ExtractedInvoice.Line> items = lineItems(lines, taxCode);

        // Confidence: every check that fails is named, and a failed check pulls confidence under the gate.
        List<String> doubtful = new ArrayList<>();
        if (items.isEmpty()) doubtful.add("lines");
        else if (subtotal != null && items.stream().map(ExtractedInvoice.Line::amount)
                .reduce(BigDecimal.ZERO, BigDecimal::add).compareTo(subtotal) != 0) doubtful.add("lines");
        if (subtotal != null && totalAmount != null
                && subtotal.add(gstHst).add(pstQst).compareTo(totalAmount) != 0) doubtful.add("total");
        if (invoiceDate == null) doubtful.add("invoiceDate");
        double confidence = doubtful.isEmpty() ? 0.98 : 0.4;

        return new ExtractedInvoice(documentType, vendorName, gst, qst, invoiceNumber, invoiceDate, dueDate, currency,
                subtotal, gstHst, pstQst, totalAmount, poNumber, shipTo,
                hasBank ? bank.group(1) : null, hasBank ? bank.group(2) : null, hasBank ? bank.group(3) : null,
                items, confidence, doubtful);
    }

    /** Everything above "Bill to": vendor block and invoice number / dates. */
    private static String headerOf(List<String> lines) {
        StringBuilder head = new StringBuilder();
        for (String line : lines) {
            if (line.startsWith("Bill to") || line.startsWith("Facturer à")) break;
            head.append(line).append('\n');
        }
        return head.toString();
    }

    private static String poOf(List<String> lines) {
        for (String line : lines) {
            Matcher m = PO.matcher(line);
            if (m.find() && m.group(1) != null) return m.group(1);
        }
        return null;
    }

    /** The province in the address right after "Bill to": the place of supply. */
    private static String shipToProvince(List<String> lines) {
        for (int i = 0; i < lines.size(); i++) {
            if (lines.get(i).startsWith("Bill to") || lines.get(i).startsWith("Facturer à")) {
                for (int j = i + 1; j < Math.min(i + 4, lines.size()); j++) {
                    Matcher m = PROVINCE.matcher(lines.get(j));
                    if (m.find()) return m.group(1);
                }
            }
        }
        return null;
    }

    /** One code per invoice (lines carry only the federal part; the QST/PST amount stays on the invoice). */
    private static String taxCode(String currency, boolean hst, BigDecimal gstHst, BigDecimal pstQst) {
        if ("USD".equals(currency)) return "FOREIGN";
        if (gstHst.signum() == 0 && pstQst.signum() == 0) return "EXEMPT";
        return hst ? "HST13" : "GST5";
    }

    private static List<ExtractedInvoice.Line> lineItems(List<String> lines, String taxCode) {
        List<ExtractedInvoice.Line> items = new ArrayList<>();
        boolean inTable = false;
        for (String line : lines) {
            if (line.startsWith("Description")) {
                inTable = true;
            } else if (inTable && SUBTOTAL.matcher(line).find()) {
                break;
            } else if (inTable) {
                Matcher m = LINE_ITEM.matcher(line);
                if (m.find()) {
                    items.add(new ExtractedInvoice.Line(m.group(1), new BigDecimal(m.group(2)),
                            money(m.group(3)), money(m.group(4)), taxCode));
                }
            }
        }
        return items;
    }

    // ---------- small helpers ----------

    private static String find(Pattern pattern, String text) {
        Matcher m = pattern.matcher(text);
        return m.find() ? m.group(1) : null;
    }

    private static LocalDate date(String iso) {
        return iso == null ? null : LocalDate.parse(iso);
    }

    /** "$1,234.56", "-$12.00", "1 234,56 $" -> BigDecimal. */
    static BigDecimal money(String text) {
        if (text == null) return null;
        boolean negative = text.contains("-");
        String digits = text.replaceAll("[^\\d.,]", "");
        digits = digits.contains(",") && !digits.contains(".")
                ? digits.replace(",", ".")      // French decimal comma ("1 234,56" lost its space above)
                : digits.replace(",", "");      // English thousands comma
        BigDecimal value = new BigDecimal(digits);
        return negative ? value.negate() : value;
    }
}
