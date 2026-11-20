package com.sky.metrics;

import com.alibaba.fastjson.JSON;
import com.alibaba.fastjson.JSONObject;
import com.sky.support.MySqlIntegrationTest;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.test.web.client.TestRestTemplate;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.beans.factory.annotation.Autowired;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** Actuator runs on its own management port, which docker compose keeps private. */
class ActuatorIntegrationTest extends MySqlIntegrationTest {

    @Value("${local.management.port}")
    private int managementPort;

    @Autowired
    private TestRestTemplate rest;

    @Test
    void healthIsUpAndIncludesTheDatabaseCheck() {
        ResponseEntity<String> response = rest.getForEntity(management("/actuator/health"), String.class);

        assertEquals(HttpStatus.OK, response.getStatusCode());
        JSONObject health = JSON.parseObject(response.getBody());
        assertEquals("UP", health.getString("status"));
        assertEquals("UP", health.getJSONObject("components").getJSONObject("db").getString("status"));
    }

    @Test
    void webhookOutcomesAreCounted() {
        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.APPLICATION_JSON);
        headers.set("Stripe-Signature", "t=1,v1=forged");
        rest.postForEntity("/notify/stripe", new HttpEntity<>("{}", headers), String.class);

        ResponseEntity<String> response = rest.getForEntity(
                management("/actuator/metrics/stripe.webhook.events?tag=outcome:invalid_signature"), String.class);

        assertEquals(HttpStatus.OK, response.getStatusCode());
        double count = JSON.parseObject(response.getBody()).getJSONArray("measurements").getJSONObject(0).getDoubleValue("value");
        assertTrue(count >= 1, response.getBody());
    }

    @Test
    void actuatorIsNotServedOnTheApplicationPort() {
        assertEquals(HttpStatus.NOT_FOUND, rest.getForEntity("/actuator/health", String.class).getStatusCode());
    }

    private String management(String path) {
        return "http://localhost:" + managementPort + path;
    }
}
