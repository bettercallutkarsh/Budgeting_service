package com.budgetingapp.security;

import jakarta.servlet.http.HttpServletRequest;

public class ClientIpResolver {

    private static final String X_FORWARDED_FOR = "X-Forwarded-For";

    private final boolean trustProxyHeaders;

    public ClientIpResolver(boolean trustProxyHeaders) {
        this.trustProxyHeaders = trustProxyHeaders;
    }

    public String resolve(HttpServletRequest request) {
        String remoteAddr = request.getRemoteAddr();
        if (!trustProxyHeaders) {
            return remoteAddr;
        }
        String forwardedFor = request.getHeader(X_FORWARDED_FOR);
        if (forwardedFor == null || forwardedFor.isBlank()) {
            return remoteAddr;
        }
        String[] hops = forwardedFor.split(",");
        for (int i = hops.length - 1; i >= 0; i--) {
            String candidate = hops[i].trim();
            if (!candidate.isEmpty()) {
                return candidate;
            }
        }
        return remoteAddr;
    }
}
