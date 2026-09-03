package com.bankms.security;

import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Component;

@Component
public class CurrentUserProvider {

    public String username() {
        return SecurityContextHolder.getContext().getAuthentication().getName();
    }

    public boolean hasAnyRole(String... roles) {
        var authorities = SecurityContextHolder.getContext().getAuthentication().getAuthorities();
        for (String role : roles) {
            String target = "ROLE_" + role;
            if (authorities.stream().map(GrantedAuthority::getAuthority).anyMatch(target::equals)) {
                return true;
            }
        }
        return false;
    }
}
