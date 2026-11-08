package com.stagepass.discovery.api;

import com.stagepass.common.config.CompanyProperties;

/** The company's public identity, for the About and legal pages (blank settings become null). */
public record CompanyInfo(String name, String address, String gstin, String state, String email, String phone) {

    public static CompanyInfo from(CompanyProperties c) {
        return new CompanyInfo(blankToDefault(c.name()), blank(c.address()), blank(c.gstin()), blank(c.state()),
                blank(c.email()), blank(c.phone()));
    }

    private static String blank(String s) {
        return s == null || s.isBlank() ? null : s.strip();
    }

    private static String blankToDefault(String s) {
        return s == null || s.isBlank() ? "Stagepass" : s.strip();
    }
}
