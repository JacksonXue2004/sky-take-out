package com.sky.config;

import org.junit.jupiter.api.Test;
import org.slf4j.MDC;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;

class RequestIdFilterTest {

    private final MockMvc mvc = MockMvcBuilders.standaloneSetup(new EchoController())
            .addFilters(new RequestIdFilter())
            .build();

    @Test
    void aRequestWithoutAnIdGetsOneThatItsLogLinesShare() throws Exception {
        MvcResult result = mvc.perform(get("/echo")).andReturn();

        String header = result.getResponse().getHeader("X-Request-Id");
        assertTrue(header.matches("[0-9a-f-]{36}"), header);
        // The controller saw the same id in the logging context.
        assertEquals(header, result.getResponse().getContentAsString());
        assertNull(MDC.get("requestId"), "the id must not leak to the next request on this thread");
    }

    @Test
    void aCallersPlainIdIsKept() throws Exception {
        MvcResult result = mvc.perform(get("/echo").header("X-Request-Id", "checkout-42.retry_1")).andReturn();

        assertEquals("checkout-42.retry_1", result.getResponse().getHeader("X-Request-Id"));
    }

    @Test
    void anIdThatCouldForgeLogLinesIsReplaced() throws Exception {
        MvcResult result = mvc.perform(get("/echo").header("X-Request-Id", "abc\nERROR fake log line")).andReturn();

        assertTrue(result.getResponse().getHeader("X-Request-Id").matches("[0-9a-f-]{36}"));
    }

    @RestController
    static class EchoController {
        @GetMapping("/echo")
        String echo() {
            return MDC.get("requestId");
        }
    }
}
