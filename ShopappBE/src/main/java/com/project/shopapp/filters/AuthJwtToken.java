package com.project.shopapp.filters;

import org.springframework.http.HttpHeaders;
import org.springframework.stereotype.Component;

@Component
public class AuthJwtToken {
    public static String extractToken(String authentication)
    {
        if(authentication != null && authentication.startsWith("Bearer "))
        {
            return authentication.substring(7);
        }
        return null;
    }

    public static String authHeader(HttpHeaders headers) {

        String authHeader= headers.getFirst(HttpHeaders.AUTHORIZATION);
        return authHeader;
    }

}
