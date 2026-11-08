package com.stagepass.common.config;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.boot.context.properties.bind.DefaultValue;

/** The legal entity selling convenience fees: used on GST invoices and in Razorpay Checkout. */
@ConfigurationProperties(prefix = "stagepass.company")
public record CompanyProperties(@DefaultValue("Stagepass") String name, String gstin, String state, String address,
        String email, String phone) {

    public boolean gstRegistered() {
        return gstin != null && !gstin.isBlank();
    }
}
