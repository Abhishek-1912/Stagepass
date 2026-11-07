package com.stagepass.identity;

import com.stagepass.support.PostgresIntegrationTest;
import com.stagepass.support.TestUsers;
import com.stagepass.support.TestUsers.TestUser;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.ResultActions;

import java.util.UUID;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

class ProfileExtrasTest extends PostgresIntegrationTest {

    static final String BASE = "https://res.cloudinary.com/demo/image/upload/";

    @Autowired
    TestUsers users;

    private ResultActions patchMe(TestUser u, String json) throws Exception {
        return mvc.perform(patch("/api/v1/me").header("Authorization", u.bearer()).contentType(MediaType.APPLICATION_JSON).content(json));
    }

    @Test
    void avatarMustBeOurCloudinaryFolder() throws Exception {
        TestUser me = users.attendee();
        for (String bad : new String[] {
                BASE + "stagepass/avatars/" + UUID.randomUUID() + "/me.jpg",
                "https://example.com/stagepass/avatars/" + me.id() + "/me.jpg",
                BASE + "stagepass/events/" + UUID.randomUUID() + "/me.jpg"}) {
            patchMe(me, "{\"avatarUrl\":\"" + bad + "\"}")
                    .andExpect(status().isBadRequest()).andExpect(jsonPath("$.errors[0].field").value("avatarUrl"));
        }
        String mine = BASE + "v1712/stagepass/avatars/" + me.id() + "/me.jpg";
        patchMe(me, "{\"avatarUrl\":\"" + mine + "\"}").andExpect(status().isOk()).andExpect(jsonPath("$.avatarUrl").value(mine));
        patchMe(me, "{\"avatarUrl\":\"\"}").andExpect(status().isOk()).andExpect(jsonPath("$.avatarUrl").doesNotExist());
    }

    @Test
    void avatarUploadSignature() throws Exception {
        TestUser me = users.attendee();
        mvc.perform(post("/api/v1/uploads/signature").header("Authorization", me.bearer())
                        .contentType(MediaType.APPLICATION_JSON).content("{\"purpose\":\"AVATAR\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.folder").value("stagepass/avatars/" + me.id()));
        mvc.perform(post("/api/v1/uploads/signature").header("Authorization", me.bearer())
                        .contentType(MediaType.APPLICATION_JSON).content("{\"purpose\":\"EVENT_IMAGE\"}"))
                .andExpect(status().isBadRequest()).andExpect(jsonPath("$.errors[0].field").value("purpose"));
    }

    @Test
    void reminderPreferenceRoundTrips() throws Exception {
        TestUser me = users.attendee();
        mvc.perform(get("/api/v1/me").header("Authorization", me.bearer())).andExpect(jsonPath("$.emailReminders").value(true));
        patchMe(me, "{\"emailReminders\":false}").andExpect(status().isOk()).andExpect(jsonPath("$.emailReminders").value(false));
        patchMe(me, "{}").andExpect(status().isOk());
        mvc.perform(get("/api/v1/me").header("Authorization", me.bearer())).andExpect(jsonPath("$.emailReminders").value(false));
    }
}
