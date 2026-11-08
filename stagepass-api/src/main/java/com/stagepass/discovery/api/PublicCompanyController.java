package com.stagepass.discovery.api;

import com.stagepass.common.config.CompanyProperties;
import org.springframework.http.CacheControl;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;

import java.time.Duration;

@RestController
public class PublicCompanyController {

    private final CompanyProperties company;

    public PublicCompanyController(CompanyProperties company) {
        this.company = company;
    }

    @GetMapping("/api/v1/public/company")
    public ResponseEntity<CompanyInfo> company() {
        return ResponseEntity.ok().cacheControl(CacheControl.maxAge(Duration.ofMinutes(5)).cachePublic())
                .body(CompanyInfo.from(company));
    }
}
