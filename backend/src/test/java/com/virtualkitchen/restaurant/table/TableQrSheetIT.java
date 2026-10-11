package com.virtualkitchen.restaurant.table;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.virtualkitchen.support.AuthClient;
import com.virtualkitchen.support.IntegrationTest;
import com.virtualkitchen.support.OwnerFixtures;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.openpdf.text.pdf.PdfReader;
import org.openpdf.text.pdf.parser.PdfTextExtractor;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.test.web.servlet.MockMvc;

import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.containsString;
import static org.springframework.http.HttpMethod.GET;
import static org.springframework.http.HttpMethod.POST;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

class TableQrSheetIT extends IntegrationTest {

    @Autowired MockMvc mvc;
    @Autowired ObjectMapper json;

    AuthClient client;
    OwnerFixtures fixtures;
    String owner;
    String restaurantId;

    @BeforeEach
    void setUp() throws Exception {
        client = new AuthClient(mvc, json);
        fixtures = new OwnerFixtures(client);
        owner = client.approvedOwnerToken();
        restaurantId = fixtures.createRestaurant(owner, OwnerFixtures.restaurantBody("Spice Garden",
                OwnerFixtures.randomCity(), List.of(), List.of("DINE_IN"), OwnerFixtures.ALWAYS_OPEN)).path("id").asText();
    }

    @Test
    void pdfHasOnePagePerTableWithNameLabelAndUrl() throws Exception {
        List<String> tokens = new java.util.ArrayList<>();
        for (String label : List.of("T2", "T1", "Patio 3")) {
            tokens.add(client.body(client.send(POST, base() + "/tables", owner, Map.of("label", label))
                    .andExpect(status().isCreated())
                    .andExpect(jsonPath("$.qrUrl", containsString("/t/")))).path("qrToken").asText());
        }

        byte[] pdf = client.send(GET, base() + "/tables/qr-codes.pdf", owner, null)
                .andExpect(status().isOk())
                .andExpect(content().contentType("application/pdf"))
                .andExpect(header().string("Content-Disposition", containsString("table-qr-codes.pdf")))
                .andReturn().getResponse().getContentAsByteArray();

        PdfReader reader = new PdfReader(pdf);
        assertThat(reader.getNumberOfPages()).isEqualTo(3);
        PdfTextExtractor text = new PdfTextExtractor(reader);
        // label order: "Patio 3", "T1", "T2"
        assertThat(text.getTextFromPage(1)).contains("Spice Garden", "Table Patio 3");
        assertThat(text.getTextFromPage(2)).contains("Table T1");
        String page3 = text.getTextFromPage(3);
        assertThat(page3).contains("Table T2", "http://localhost:3000/t/" + tokens.get(0));
        reader.close();
    }

    @Test
    void noTablesIs409() throws Exception {
        client.send(GET, base() + "/tables/qr-codes.pdf", owner, null)
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.message").value("Add at least one table before printing QR codes"));
    }

    @Test
    void onlyTheOwnerCanDownload() throws Exception {
        client.send(POST, base() + "/tables", owner, Map.of("label", "T1")).andExpect(status().isCreated());

        client.send(GET, base() + "/tables/qr-codes.pdf", client.approvedOwnerToken(), null).andExpect(status().isNotFound());
        client.send(GET, base() + "/tables/qr-codes.pdf", client.registered("CUSTOMER").path("accessToken").asText(), null)
                .andExpect(status().isForbidden());
        client.send(GET, base() + "/tables/qr-codes.pdf", null, null).andExpect(status().isUnauthorized());
    }

    private String base() {
        return "/api/owner/restaurants/" + restaurantId;
    }
}
