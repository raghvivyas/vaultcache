package com.vaultcache.security;

import com.vaultcache.config.AppProperties;
import io.jsonwebtoken.*;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.stereotype.Component;

import java.util.Date;
import java.util.stream.Collectors;

@Slf4j @Component @RequiredArgsConstructor
public class JwtTokenProvider {
    private final AppProperties props;

    public String generateToken(Authentication auth) {
        UserDetails user  = (UserDetails) auth.getPrincipal();
        String roles = user.getAuthorities().stream()
                .map(GrantedAuthority::getAuthority).collect(Collectors.joining(","));
        Date now = new Date();
        return Jwts.builder().setSubject(user.getUsername()).claim("roles", roles)
                .setIssuedAt(now)
                .setExpiration(new Date(now.getTime() + props.getJwtExpirationMs()))
                .signWith(SignatureAlgorithm.HS512, props.getJwtSecret()).compact();
    }

    public String getUsernameFromToken(String token) {
        return Jwts.parser().setSigningKey(props.getJwtSecret())
                .parseClaimsJws(token).getBody().getSubject();
    }

    public boolean validateToken(String token) {
        try { Jwts.parser().setSigningKey(props.getJwtSecret()).parseClaimsJws(token); return true; }
        catch (JwtException | IllegalArgumentException e) { log.warn("JWT invalid: {}", e.getMessage()); return false; }
    }
}
