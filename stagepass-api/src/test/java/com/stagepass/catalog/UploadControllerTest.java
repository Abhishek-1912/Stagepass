package com.stagepass.catalog;

import com.jayway.jsonpath.JsonPath;
import com.stagepass.catalog.config.CloudinaryProperties;
import com.stagepass.catalog.service.CloudinarySigner;
import com.stagepass.catalog.service.UploadService;
import com.stagepass.common.error.ApiException;
import com.stagepass.common.error.ErrorCode;
import com.stagepass.identity.OrganizerApplicationControllerTest;
import com.stagepass.support.CatalogFixtures;
import com.stagepass.support.PostgresIntegrationTest;
import com.stagepass.support.TestUsers;
import com.stagepass.support.TestUsers.TestUser;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.MediaType;

import java.util.Map;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.endsWith;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

class UploadControllerTest extends PostgresIntegrationTest {

    @Autowired
    TestUsers users;

    @Autowired
    CatalogFixtures catalog;

    @Autowired
    CloudinarySigner signer;

    private String sign(TestUser user, String path, String body) throws Exception {
        return mvc.perform(post(path).header("Authorization", user.bearer())
                        .contentType(MediaType.APPLICATION_JSON).content(body))
                .andReturn().getResponse().getContentAsString();
    }

    @Test
    void signsUploadForOwnEvent() throws Exception {
        TestUser org = users.approvedOrganizer();
        UUID event = catalog.draftEvent(org);

        String json = sign(org, "/api/v1/organizer/uploads/signature",
                "{\"purpose\":\"EVENT_IMAGE\",\"eventId\":\"" + event + "\"}");

        assertThat((String) JsonPath.read(json, "$.folder")).isEqualTo("stagepass/events/" + event);
        assertThat((String) JsonPath.read(json, "$.uploadUrl")).isEqualTo("https://api.cloudinary.com/v1_1/demo/image/upload");
        assertThat((Integer) JsonPath.read(json, "$.maxBytes")).isEqualTo(5_242_880);
        long timestamp = ((Number) JsonPath.read(json, "$.timestamp")).longValue();
        assertThat((String) JsonPath.read(json, "$.signature")).isEqualTo(signer.sign(
                Map.of("folder", "stagepass/events/" + event, "allowed_formats", "jpg,png,webp"), timestamp));
    }

    @Test
    void otherOrganizersEventCannotBeSigned() throws Exception {
        UUID event = catalog.draftEvent(users.approvedOrganizer());

        mvc.perform(post("/api/v1/organizer/uploads/signature").header("Authorization", users.approvedOrganizer().bearer())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"purpose\":\"EVENT_IMAGE\",\"eventId\":\"" + event + "\"}"))
                .andExpect(status().isNotFound());
    }

    @Test
    void anyUserCanSignOwnLogoFolder() throws Exception {
        TestUser user = users.attendee();
        String json = sign(user, "/api/v1/uploads/signature", "{\"purpose\":\"ORGANIZER_LOGO\"}");

        assertThat((String) JsonPath.read(json, "$.folder")).isEqualTo("stagepass/organizers/" + user.id());
    }

    @Test
    void coverFromForeignFolderIsRejected() throws Exception {
        TestUser org = users.approvedOrganizer();
        UUID event = catalog.draftEvent(org);
        UUID other = catalog.draftEvent(org);

        mvc.perform(patch("/api/v1/organizer/events/" + event).header("Authorization", org.bearer())
                        .contentType(MediaType.APPLICATION_JSON).content("{\"coverImageUrl\":\"https://res.cloudinary.com/demo/image/upload/stagepass/events/" + other + "/c.jpg\"}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.errors[0].field").value("coverImageUrl"));

        mvc.perform(patch("/api/v1/organizer/events/" + event).header("Authorization", org.bearer())
                        .contentType(MediaType.APPLICATION_JSON).content("{\"coverImageUrl\":\"https://res.cloudinary.com/demo/image/upload/stagepass/events/" + event + "/c.jpg\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.coverImageUrl").value(endsWith("/c.jpg")));
    }

    @Test
    void organizerLogoMustComeFromOwnFolder() throws Exception {
        TestUser user = users.attendee();
        String body = OrganizerApplicationControllerTest.VALID.replace("\"bio\"",
                "\"logoUrl\":\"https://res.cloudinary.com/demo/image/upload/stagepass/organizers/" + UUID.randomUUID()
                        + "/logo.png\",\"bio\"");

        mvc.perform(post("/api/v1/organizer-applications").header("Authorization", user.bearer())
                        .contentType(MediaType.APPLICATION_JSON).content(body))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.errors[0].field").value("logoUrl"));
    }

    @Test
    void unconfiguredCloudinaryIsServiceUnavailable() {
        UploadService unconfigured = new UploadService(new CloudinaryProperties("", "", ""),
                new CloudinarySigner(new CloudinaryProperties("", "", "")), null);

        assertThatThrownBy(() -> unconfigured.signLogo(UUID.randomUUID()))
                .isInstanceOf(ApiException.class)
                .satisfies(e -> assertThat(((ApiException) e).code()).isEqualTo(ErrorCode.SERVICE_UNAVAILABLE));
    }
}
