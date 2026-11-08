package com.analoghub.platform.common.config;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpHeaders;
import org.springframework.mock.web.MockFilterChain;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;
import org.springframework.web.cors.CorsConfiguration;
import org.springframework.web.filter.CorsFilter;

import java.util.Arrays;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

class CorsConfigTest {

    @Test
    @DisplayName("CORS preflight allows Idempotency-Key and X-Idempotency-Key even when omitted from allowedHeaders property")
    void preflightAllowsIdempotencyHeadersWhenOmittedFromConfiguredCsv() throws Exception {
        CorsConfig corsConfig = new CorsConfig();
        corsConfig.setAllowedOrigins("http://localhost:5173,http://localhost:3000");
        corsConfig.setAllowedMethods("GET,POST,PUT,PATCH,DELETE,OPTIONS");
        corsConfig.setAllowedHeaders("Authorization,Content-Type,Accept,Cache-Control,X-Requested-With");
        corsConfig.setExposedHeaders("Retry-After,Content-Disposition");
        corsConfig.setAllowCredentials(true);
        corsConfig.setMaxAge(3600L);

        CorsFilter corsFilter = new CorsFilter(corsConfig.corsConfigurationSource());

        MockHttpServletRequest request = new MockHttpServletRequest("OPTIONS", "/api/v1/auth/oauth2/google");
        request.addHeader(HttpHeaders.ORIGIN, "http://localhost:3000");
        request.addHeader(HttpHeaders.ACCESS_CONTROL_REQUEST_METHOD, "POST");
        request.addHeader(HttpHeaders.ACCESS_CONTROL_REQUEST_HEADERS, "content-type,idempotency-key,x-idempotency-key");

        MockHttpServletResponse response = new MockHttpServletResponse();
        corsFilter.doFilter(request, response, new MockFilterChain());

        assertThat(response.getStatus()).isEqualTo(200);
        assertThat(response.getHeader(HttpHeaders.ACCESS_CONTROL_ALLOW_ORIGIN)).isEqualTo("http://localhost:3000");

        String allowHeadersRaw = response.getHeader(HttpHeaders.ACCESS_CONTROL_ALLOW_HEADERS);
        assertThat(allowHeadersRaw).isNotBlank();
        List<String> allowedHeaders = Arrays.stream(allowHeadersRaw.split(","))
                .map(s -> s.trim().toLowerCase())
                .toList();

        assertThat(allowedHeaders).contains("content-type", "idempotency-key", "x-idempotency-key");
    }

    @Test
    @DisplayName("CorsConfig preserves wildcard allowedHeaders and avoids case-insensitive duplicates")
    void preservesWildcardAndAvoidsDuplicateIdempotencyHeaders() {
        CorsConfig wildcardConfig = new CorsConfig();
        wildcardConfig.setAllowedHeaders("*");
        MockHttpServletRequest req = new MockHttpServletRequest("GET", "/api/v1/labs");
        CorsConfiguration resolvedWildcard = wildcardConfig.corsConfigurationSource().getCorsConfiguration(req);
        assertThat(resolvedWildcard).isNotNull();
        assertThat(resolvedWildcard.getAllowedHeaders()).containsExactly("*");

        CorsConfig configuredWithKeys = new CorsConfig();
        configuredWithKeys.setAllowedHeaders("Authorization,idempotency-key,X-Idempotency-Key");
        CorsConfiguration resolvedDeduped = configuredWithKeys.corsConfigurationSource().getCorsConfiguration(req);
        assertThat(resolvedDeduped).isNotNull();
        assertThat(resolvedDeduped.getAllowedHeaders())
                .containsExactly("Authorization", "idempotency-key", "X-Idempotency-Key");
    }
}
