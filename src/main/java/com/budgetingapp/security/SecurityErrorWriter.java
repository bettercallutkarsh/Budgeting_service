package com.budgetingapp.security;

import com.budgetingapp.dto.response.ApiErrorResponse;
import com.budgetingapp.exception.BusinessException;
import com.budgetingapp.logging.RequestIds;
import tools.jackson.databind.ObjectMapper;
import jakarta.servlet.http.HttpServletResponse;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;

import java.io.IOException;
import java.time.Clock;
import java.util.List;

public class SecurityErrorWriter {

    private static final Logger log = LoggerFactory.getLogger(SecurityErrorWriter.class);

    private final ObjectMapper objectMapper;
    private final Clock clock;

    public SecurityErrorWriter(ObjectMapper objectMapper, Clock clock) {
        this.objectMapper = objectMapper;
        this.clock = clock;
    }

    public void writeError(HttpServletResponse response, HttpStatus status, String code, String message) {
        if (status == HttpStatus.UNAUTHORIZED) {
            log.info("security.authentication_required status={} errorCode={}", status.value(), code);
        } else if (status == HttpStatus.FORBIDDEN) {
            log.warn("security.access_denied status={} errorCode={}", status.value(), code);
        } else if (status.is5xxServerError()) {
            log.error("security.error status={} errorCode={}", status.value(), code);
        }
        ApiErrorResponse body = new ApiErrorResponse(clock.instant(), status.value(), code, message,
                List.of(), RequestIds.currentOrNew());
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
