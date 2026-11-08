package com.stagepass.discovery;

import com.stagepass.support.PostgresIntegrationTest;
import org.junit.jupiter.api.Test;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

class PublicCompanyTest extends PostgresIntegrationTest {

    @Test
    void companyDetailsArePublicAndBlanksAreNull() throws Exception {
        mvc.perform(get("/api/v1/public/company"))
                .andExpect(status().isOk())
                .andExpect(header().string("Cache-Control", "max-age=300, public"))
                .andExpect(jsonPath("$.name").value("Stagepass"))
                .andExpect(jsonPath("$.gstin").value("36ABCDE1234F1Z5"))
                .andExpect(jsonPath("$.state").value("Telangana"))
                .andExpect(jsonPath("$.address").doesNotExist())
                .andExpect(jsonPath("$.email").doesNotExist())
                .andExpect(jsonPath("$.phone").doesNotExist());
    }
}
