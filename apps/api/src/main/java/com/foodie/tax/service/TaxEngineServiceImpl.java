package com.foodie.tax.service;

import com.foodie.tax.entity.TaxRule;
import com.foodie.tax.entity.TaxSnapshot;
import com.foodie.tax.entity.TaxSnapshotItem;
import com.foodie.tax.enums.TaxType;
import com.foodie.tax.model.TaxCalculationRequest;
import com.foodie.tax.model.TaxCalculationResult;
import com.foodie.tax.model.TaxComponentInput;
import com.foodie.tax.model.TaxContext;
import com.foodie.tax.repository.TaxSnapshotItemRepository;
import com.foodie.tax.repository.TaxSnapshotRepository;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class TaxEngineServiceImpl implements TaxEngineService {

    private static final Logger log = LoggerFactory.getLogger(TaxEngineServiceImpl.class);

    private final TaxRuleResolver taxRuleResolver;
    private final RoundingPolicy roundingPolicy;
    private final TaxSnapshotRepository taxSnapshotRepository;
    private final TaxSnapshotItemRepository taxSnapshotItemRepository;

    public TaxEngineServiceImpl(
            TaxRuleResolver taxRuleResolver,
            RoundingPolicy roundingPolicy,
            TaxSnapshotRepository taxSnapshotRepository,
            TaxSnapshotItemRepository taxSnapshotItemRepository
    ) {
        this.taxRuleResolver = taxRuleResolver;
        this.roundingPolicy = roundingPolicy;
        this.taxSnapshotRepository = taxSnapshotRepository;
        this.taxSnapshotItemRepository = taxSnapshotItemRepository;
    }

    @Override
    @Transactional
    public TaxCalculationResult calculateAndSnapshot(TaxCalculationRequest request) {
        TaxContext context = request.taxContext() != null ? request.taxContext() : TaxContext.intraState("DEFAULT");

        long totalTaxablePaise = 0L;
        long totalCgstPaise = 0L;
        long totalSgstPaise = 0L;
        long totalIgstPaise = 0L;
        long totalCessPaise = 0L;

        List<TaxSnapshotItem> snapshotItems = new ArrayList<>();

        for (TaxComponentInput comp : request.components()) {
            long gross = comp.grossPaise();
            long discount = comp.discountPaise();
            long taxable = Math.max(0L, gross - discount);

            Optional<TaxRule> ruleOpt = taxRuleResolver.resolveRule(comp.componentType(), context, LocalDate.now());

            UUID ruleId = null;
            int ruleVersion = 1;
            long cgst = 0L;
            long sgst = 0L;
            long igst = 0L;
            long cess = 0L;

            if (ruleOpt.isPresent()) {
                TaxRule rule = ruleOpt.get();
                ruleId = rule.getId();
                ruleVersion = rule.getVersion();

                if (rule.getTaxType() == TaxType.CGST_SGST) {
                    cgst = roundingPolicy.roundTaxAmount(taxable, rule.getCgstRate());
                    sgst = roundingPolicy.roundTaxAmount(taxable, rule.getSgstRate());
                } else if (rule.getTaxType() == TaxType.IGST) {
                    igst = roundingPolicy.roundTaxAmount(taxable, rule.getIgstRate());
                }
                if (rule.getCessRate() != null) {
                    cess = roundingPolicy.roundTaxAmount(taxable, rule.getCessRate());
                }
            } else {
                log.warn("No active tax rule resolved for componentType={}. Defaulting to 0 tax.", comp.componentType());
            }

            long itemTotalPaise = taxable + cgst + sgst + igst + cess;

            totalTaxablePaise += taxable;
            totalCgstPaise += cgst;
            totalSgstPaise += sgst;
            totalIgstPaise += igst;
            totalCessPaise += cess;

            TaxSnapshotItem item = TaxSnapshotItem.create(
                    comp.orderItemId(),
                    comp.componentType(),
                    comp.description(),
                    gross,
                    discount,
                    taxable,
                    ruleId,
                    ruleVersion,
                    cgst,
                    sgst,
                    igst,
                    cess,
                    itemTotalPaise
            );
            snapshotItems.add(item);
        }

        long totalTaxPaise = totalCgstPaise + totalSgstPaise + totalIgstPaise + totalCessPaise;
        long roundingAdjustmentPaise = 0L;
        long grandTotalPaise = totalTaxablePaise + totalTaxPaise + roundingAdjustmentPaise;

        TaxSnapshot snapshot = TaxSnapshot.create(
                request.orderId(),
                request.sellerEntityId(),
                request.customerTaxProfileId(),
                context.toString(),
                "INR",
                totalTaxablePaise,
                totalCgstPaise,
                totalSgstPaise,
                totalIgstPaise,
                totalCessPaise,
                roundingAdjustmentPaise,
                1
        );

        for (TaxSnapshotItem item : snapshotItems) {
            snapshot.addItem(item);
        }

        TaxSnapshot savedSnapshot = taxSnapshotRepository.save(snapshot);
        log.info("TaxSnapshot saved snapshotId={} orderId={} totalTaxablePaise={} totalTaxPaise={} grandTotalPaise={}",
                savedSnapshot.getId(), request.orderId(), totalTaxablePaise, totalTaxPaise, grandTotalPaise);

        return new TaxCalculationResult(
                totalTaxablePaise,
                totalCgstPaise,
                totalSgstPaise,
                totalIgstPaise,
                totalCessPaise,
                totalTaxPaise,
                roundingAdjustmentPaise,
                grandTotalPaise,
                savedSnapshot,
                savedSnapshot.getItems()
        );
    }

    @Override
    @Transactional(readOnly = true)
    public Optional<TaxSnapshot> getSnapshotForOrder(UUID orderId) {
        return taxSnapshotRepository.findByOrderId(orderId);
    }
}
