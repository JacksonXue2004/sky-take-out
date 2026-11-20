package com.sky.interceptor;

import ch.qos.logback.classic.Level;
import ch.qos.logback.classic.Logger;
import ch.qos.logback.classic.spi.ILoggingEvent;
import ch.qos.logback.core.read.ListAppender;
import com.sky.constant.JwtClaimsConstant;
import com.sky.context.BaseContext;
import com.sky.properties.JwtProperties;
import com.sky.utils.JwtUtil;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.slf4j.LoggerFactory;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;
import org.springframework.test.util.ReflectionTestUtils;
import org.springframework.web.method.HandlerMethod;

import java.util.HashMap;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class JwtTokenUserInterceptorTest {

    // Test-only value; real secrets come from configuration and are never committed.
    private static final String SECRET = "test-user-secret-for-interceptor-tests";

    private final JwtTokenUserInterceptor interceptor = new JwtTokenUserInterceptor();
    private final Logger logger = (Logger) LoggerFactory.getLogger(JwtTokenUserInterceptor.class);
    private final ListAppender<ILoggingEvent> logs = new ListAppender<>();
    private Level originalLevel;

    @BeforeEach
    void setUp() {
        JwtProperties properties = new JwtProperties();
        properties.setUserSecretKey(SECRET);
        properties.setUserTokenName("authentication");
        ReflectionTestUtils.setField(interceptor, "jwtProperties", properties);

        originalLevel = logger.getLevel();
        logger.setLevel(Level.DEBUG);
        logs.start();
        logger.addAppender(logs);
    }

    @AfterEach
    void tearDown() {
        logger.detachAppender(logs);
        logger.setLevel(originalLevel);
        BaseContext.removeCurrentId();
    }

    @Test
    void validTokenSignsTheUserInWithoutLoggingTheToken() throws Exception {
        // jjwt adds the expiry to this map, so it must be mutable.
        Map<String, Object> claims = new HashMap<>();
        claims.put(JwtClaimsConstant.USER_ID, 42L);
        String token = JwtUtil.createJWT(SECRET, 60_000, claims);
        MockHttpServletRequest request = new MockHttpServletRequest();
        request.addHeader("authentication", token);

        assertTrue(interceptor.preHandle(request, new MockHttpServletResponse(), handler()));

        assertEquals(42L, BaseContext.getCurrentId());
        assertFalse(logs.list.stream().anyMatch(event -> event.getFormattedMessage().contains(token)),
                "the bearer token must not be written to the log");
    }

    @Test
    void missingTokenIsRejectedWith401() throws Exception {
        MockHttpServletResponse response = new MockHttpServletResponse();

        assertFalse(interceptor.preHandle(new MockHttpServletRequest(), response, handler()));
        assertEquals(401, response.getStatus());
    }

    private static HandlerMethod handler() throws NoSuchMethodException {
        return new HandlerMethod(new Object(), Object.class.getMethod("toString"));
    }
}
