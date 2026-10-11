package com.virtualkitchen.common.openapi;

import com.virtualkitchen.support.IntegrationTest;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.test.web.servlet.MockMvc;

import java.nio.file.Files;
import java.nio.file.Path;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

class OpenApiDocsIT extends IntegrationTest {

    @Autowired MockMvc mvc;

    /** Also writes target/openapi.json, which `npm run api:sync` in /web can copy. */
    @Test
    void specIsPublicAndCoversTheApi() throws Exception {
        String spec = mvc.perform(get("/v3/api-docs")).andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString();

        assertThat(spec).contains("\"/api/orders\"", "\"/api/tables/{qrToken}\"", "\"/api/guest/orders\"", "\"OrderResponse\"");
        Files.writeString(Path.of("target/openapi.json"), spec);
    }
}
