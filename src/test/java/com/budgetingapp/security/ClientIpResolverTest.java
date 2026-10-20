package com.budgetingapp.security;

import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockHttpServletRequest;

import static org.junit.jupiter.api.Assertions.assertEquals;

class ClientIpResolverTest {

    private final ClientIpResolver trustedProxyResolver = new ClientIpResolver(true);
    private final ClientIpResolver untrustedProxyResolver = new ClientIpResolver(false);

    @Test
    void trustedProxyUsesRightmostNonBlankForwardedHop() {
        MockHttpServletRequest request = new MockHttpServletRequest();
        request.setRemoteAddr("10.0.0.1");
        request.addHeader("X-Forwarded-For", " 203.0.113.9 , 198.51.100.4 ");

        assertEquals("198.51.100.4", trustedProxyResolver.resolve(request));
    }

    @Test
    void trustedProxyFallsBackToRemoteAddrWhenHeaderMissingOrBlank() {
        MockHttpServletRequest missing = new MockHttpServletRequest();
        missing.setRemoteAddr("10.0.0.1");
        assertEquals("10.0.0.1", trustedProxyResolver.resolve(missing));

        MockHttpServletRequest blank = new MockHttpServletRequest();
        blank.setRemoteAddr("10.0.0.1");
        blank.addHeader("X-Forwarded-For", "  ,  ");
        assertEquals("10.0.0.1", trustedProxyResolver.resolve(blank));
    }

    @Test
    void untrustedProxyIgnoresForwardedForHeader() {
        MockHttpServletRequest request = new MockHttpServletRequest();
        request.setRemoteAddr("10.0.0.1");
        request.addHeader("X-Forwarded-For", "198.51.100.4");

        assertEquals("10.0.0.1", untrustedProxyResolver.resolve(request));
    }
}
