package com.budgetingapp.security;

import com.budgetingapp.entity.User;
import com.budgetingapp.entity.enums.AccountStatus;
import com.budgetingapp.repository.UserRepository;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import jakarta.servlet.http.HttpSession;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;

public class AccountStatusFilter extends OncePerRequestFilter {

    private final UserRepository userRepository;
    private final SecurityErrorWriter errorWriter;

    public AccountStatusFilter(UserRepository userRepository, SecurityErrorWriter errorWriter) {
        this.userRepository = userRepository;
        this.errorWriter = errorWriter;
    }

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response,
                                    FilterChain filterChain) throws ServletException, IOException {
        Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
        if (authentication != null && authentication.getPrincipal() instanceof UserPrincipal principal) {
            User user = userRepository.findByUserId(principal.getUserId()).orElse(null);
            if (user == null || user.getAccountStatus() != AccountStatus.ACTIVE) {
                SecurityContextHolder.clearContext();
                HttpSession session = request.getSession(false);
                if (session != null) {
                    session.invalidate();
                }
                errorWriter.writeError(response, HttpStatus.UNAUTHORIZED, "ACCOUNT_DEACTIVATED",
                        "This account has been deactivated.");
                return;
            }
        }
        filterChain.doFilter(request, response);
    }
}
