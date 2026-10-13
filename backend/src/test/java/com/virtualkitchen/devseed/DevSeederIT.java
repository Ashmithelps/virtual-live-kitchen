package com.virtualkitchen.devseed;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.virtualkitchen.support.AuthClient;
import com.virtualkitchen.support.IntegrationTest;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.DefaultApplicationArguments;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.web.servlet.MockMvc;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.http.HttpMethod.GET;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/** Its own Spring context (extra profile); seeds on startup, and running again changes nothing. */
@ActiveProfiles({"test", "dev-seed"})
@TestPropertySource(properties = "app.dev-seed.password=dev-seed-test-password")
class DevSeederIT extends IntegrationTest {

    private static final String PASSWORD = "dev-seed-test-password";

    @Autowired MockMvc mvc;
    @Autowired ObjectMapper json;
    @Autowired JdbcTemplate jdbc;
    @Autowired DevSeeder seeder;

    @Test
    void seedsALoginForEveryRoleAndAReadyRestaurant() throws Exception {
        AuthClient client = new AuthClient(mvc, json);
        for (String phone : new String[]{DevSeeder.ADMIN_PHONE, DevSeeder.OWNER_PHONE, DevSeeder.CHEF_1_PHONE,
                DevSeeder.CHEF_2_PHONE, DevSeeder.CUSTOMER_PHONE}) {
            client.login(phone, PASSWORD).andExpect(status().isOk());
        }
        String rid = jdbc.queryForObject("select r.id::text from restaurants r join users u on u.id = r.owner_id where u.phone = ?",
                String.class, DevSeeder.OWNER_PHONE);
        assertThat(count("select count(*) from menu_categories where restaurant_id = ?::uuid", rid)).isEqualTo(2);
        assertThat(count("select count(*) from menu_items where restaurant_id = ?::uuid", rid)).isEqualTo(8);
        assertThat(count("select count(*) from dining_tables where restaurant_id = ?::uuid", rid)).isEqualTo(3);
        assertThat(count("select count(*) from kitchen_stations where restaurant_id = ?::uuid", rid)).isEqualTo(2);
        assertThat(count("select count(*) from chefs where restaurant_id = ?::uuid", rid)).isEqualTo(2);

        mvc.perform(org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get("/api/restaurants/dev-spice-kitchen"))
                .andExpect(status().isOk()).andExpect(jsonPath("$.openNow").value(true));
        String chef = client.loggedIn(DevSeeder.CHEF_1_PHONE, PASSWORD).path("accessToken").asText();
        client.send(GET, "/api/chef/me", chef, null)
                .andExpect(jsonPath("$.consentRequired").value(false))
                .andExpect(jsonPath("$.stations[0].name").value("Tandoor"));

        seeder.run(new DefaultApplicationArguments());
        assertThat(count("select count(*) from menu_items where restaurant_id = ?::uuid", rid)).isEqualTo(8);
        assertThat(count("select count(*) from users where phone like '+91900000000%'", null)).isEqualTo(5);
    }

    private long count(String sql, String arg) {
        Long n = arg == null ? jdbc.queryForObject(sql, Long.class) : jdbc.queryForObject(sql, Long.class, arg);
        return n == null ? 0 : n;
    }
}
