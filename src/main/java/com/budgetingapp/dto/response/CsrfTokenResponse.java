package com.budgetingapp.dto.response;

public class CsrfTokenResponse {

    private String token;

    private String headerName;

    private String parameterName;

    public CsrfTokenResponse() {
    }

    public CsrfTokenResponse(String token, String headerName, String parameterName) {
        this.token = token;
        this.headerName = headerName;
        this.parameterName = parameterName;
    }

    public String getToken() {
        return token;
    }

    public void setToken(String token) {
        this.token = token;
    }

    public String getHeaderName() {
        return headerName;
    }

    public void setHeaderName(String headerName) {
        this.headerName = headerName;
    }

    public String getParameterName() {
        return parameterName;
    }

    public void setParameterName(String parameterName) {
        this.parameterName = parameterName;
    }
}
