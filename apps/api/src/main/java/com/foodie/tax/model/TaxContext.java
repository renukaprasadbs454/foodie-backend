package com.foodie.tax.model;

public record TaxContext(
        String sellerStateCode,
        String sellerGstin,
        String customerStateCode,
        String customerGstin,
        boolean intraState
) {
    public static TaxContext intraState(String stateCode) {
        return new TaxContext(stateCode, null, stateCode, null, true);
    }

    public static TaxContext interState(String sellerState, String customerState) {
        boolean intra = sellerState != null && sellerState.equalsIgnoreCase(customerState);
        return new TaxContext(sellerState, null, customerState, null, intra);
    }
}
