package com.budgetingapp.security;

import com.budgetingapp.dto.response.ApiErrorResponse;
import com.budgetingapp.exception.BusinessException;
import tools.jackson.databind.ObjectMapper;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;

import java.io.IOException;
import java.time.Clock;
import java.util.List;
import java.util.UUID;

public class SecurityErrorWriter {

    private final ObjectMapper objectMapper;
    private final Clock clock;

    public SecurityErrorWriter(ObjectMapper objectMapper, Clock clock) {
        this.objectMapper = objectMapper;
        this.clock = clock;
    }

    public void writeError(HttpServletResponse response, HttpStatus status, String code, String message) {
        ApiErrorResponse body = new ApiErrorResponse(clock.instant(), status.value(), code, message,
                List.of(), UUID.randomUUID().toString());
        writeJson(response, status, body);
    }

    public void writeJson(HttpServletResponse response, HttpStatus status, Object body) {
        try {
            response.setStatus(status.value());
            response.setContentType(MediaType.APPLICATION_JSON_VALUE);
            response.setCharacterEncoding("UTF-8");
            objectMapper.writeValue(response.getOutputStream(), body);
        } catch (IOException ex) {
            response.setStatus(status.value());
        }
    }
}
