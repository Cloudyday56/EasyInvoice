package com.brim.easyinvoice.pipeline.nodes;

import com.brim.easyinvoice.model.Invoice;
import com.brim.easyinvoice.model.InvoiceNumbers;
import com.brim.easyinvoice.model.Vendor;
import com.brim.easyinvoice.pipeline.HoldQueue;
import com.brim.easyinvoice.pipeline.Node;
import com.brim.easyinvoice.pipeline.NodeContext;
import com.brim.easyinvoice.pipeline.NodeName;
import com.brim.easyinvoice.pipeline.StageResult;
import com.brim.easyinvoice.repo.FxRateRepository;
import com.brim.easyinvoice.repo.InvoiceRepository;
import com.brim.easyinvoice.repo.VendorRepository;
import org.springframework.stereotype.Component;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.HashMap;
import java.util.Map;
import java.util.Optional;

/**
 * Job: make the data comparable and match it to the vendor master.
 * Partly real: exact legal-name match, canonical invoice number, USD->CAD.
 */
@Component
public class VendorMatchNode implements Node {

    private final VendorRepository vendors;
    private final InvoiceRepository invoices;
    private final FxRateRepository fxRates;

    public VendorMatchNode(VendorRepository vendors, InvoiceRepository invoices, FxRateRepository fxRates) {
        this.vendors = vendors;
        this.invoices = invoices;
        this.fxRates = fxRates;
    }

    @Override
    public NodeName name() {
        return NodeName.VENDOR_MATCH;
    }

    @Override
    public StageResult run(Invoice invoice, NodeContext ctx) {
        // TODO: match by GST/BN number first, then legal name, then fuzzy name (trigram) + sender domain.
        Optional<Vendor> match = invoice.vendorNameRaw() == null
                ? Optional.empty()
                : vendors.findByLegalNameIgnoreCase(invoice.vendorNameRaw());
        if (match.isEmpty()) {
            return StageResult.block(HoldQueue.VENDOR_HOLD, "NO_VENDOR_MATCH",
                    "No vendor named \"" + invoice.vendorNameRaw() + "\"");
        }
        Vendor vendor = match.get();
        if (!vendor.isActive()) {
            return StageResult.block(HoldQueue.VENDOR_HOLD, "VENDOR_NOT_ACTIVE",
                    vendor.legalName() + " is " + vendor.status());
        }

        // The DB has a UNIQUE (vendor_id, canonical_invoice_number) constraint. We check first so the
        // invoice is parked with a clear reason instead of failing on the constraint.
        String canonical = InvoiceNumbers.canonical(invoice.vendorInvoiceNumber());
        if (invoices.numberAlreadyUsed(vendor.id(), canonical, invoice.id())) {
            return StageResult.block(HoldQueue.VALIDATION_FAILED, "REPEAT_INVOICE_NUMBER",
                    vendor.legalName() + " already billed invoice number " + invoice.vendorInvoiceNumber());
        }

        BigDecimal rate = BigDecimal.ONE;
        if ("USD".equals(invoice.currency())) {
            Optional<BigDecimal> usd = fxRates.usdToCadOn(invoice.invoiceDate());
            if (usd.isEmpty()) {
                return StageResult.block(HoldQueue.VALIDATION_FAILED, "NO_FX_RATE",
                        "No USD/CAD rate on or before " + invoice.invoiceDate());
            }
            rate = usd.get();
        }

        Map<String, Object> patch = new HashMap<>();
        patch.put("vendor_id", vendor.id());
        patch.put("canonical_invoice_number", canonical);
        patch.put("fx_rate_to_cad", rate);
        patch.put("total_cad", invoice.total().multiply(rate).setScale(2, RoundingMode.HALF_UP));
        // TODO: date sanity (future / over a year old), credit notes negative, default GL coding from vendor.
        return StageResult.pass("Matched " + vendor.legalName() + " (" + vendor.id() + ")", patch);
    }
}
