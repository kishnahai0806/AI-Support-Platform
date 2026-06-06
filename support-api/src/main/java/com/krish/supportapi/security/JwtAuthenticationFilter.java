package com.krish.supportapi.security;

import io.jsonwebtoken.JwtException;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.security.core.userdetails.UserDetailsService;
import org.springframework.security.web.authentication.WebAuthenticationDetailsSource;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

@Component
public class JwtAuthenticationFilter extends OncePerRequestFilter {

    private static final String ACCESS_TOKEN_TYPE = "access";

    private final JwtTokenProvider jwtTokenProvider;

    private final UserDetailsService userDetailsService;

    private final StringRedisTemplate stringRedisTemplate;

    public JwtAuthenticationFilter(
        JwtTokenProvider jwtTokenProvider,
        UserDetailsService userDetailsService,
        StringRedisTemplate stringRedisTemplate
    ) {
        this.jwtTokenProvider = jwtTokenProvider;
        this.userDetailsService = userDetailsService;
        this.stringRedisTemplate = stringRedisTemplate;
    }

    @Override
    protected void doFilterInternal(
        HttpServletRequest request,
        HttpServletResponse response,
        FilterChain filterChain
    ) throws ServletException, IOException {
        String authorizationHeader = request.getHeader(SecurityConstants.AUTHORIZATION_HEADER);

        if (authorizationHeader != null && authorizationHeader.startsWith(SecurityConstants.BEARER_PREFIX)) {
            try {
                String token = authorizationHeader.substring(SecurityConstants.BEARER_PREFIX.length());
                String username = jwtTokenProvider.extractUsername(token);

                if (username != null && SecurityContextHolder.getContext().getAuthentication() == null) {
                    if (Boolean.TRUE.equals(stringRedisTemplate.hasKey("blacklist:" + token))) {
                        SecurityContextHolder.clearContext();
                        filterChain.doFilter(request, response);
                        return;
                    }

                    UserDetails userDetails = userDetailsService.loadUserByUsername(username);
                    if (!userDetails.isEnabled()) {
                        SecurityContextHolder.clearContext();
                        response.sendError(HttpServletResponse.SC_UNAUTHORIZED);
                        return;
                    }

                    if (jwtTokenProvider.isTokenValid(token, userDetails)) {
                        String tokenType = jwtTokenProvider.extractTokenType(token);
                        if (!ACCESS_TOKEN_TYPE.equals(tokenType)) {
                            SecurityContextHolder.clearContext();
                            response.sendError(HttpServletResponse.SC_UNAUTHORIZED);
                            return;
                        }

                        UsernamePasswordAuthenticationToken authenticationToken =
                            new UsernamePasswordAuthenticationToken(
                                userDetails,
                                null,
                                userDetails.getAuthorities()
                            );
                        authenticationToken.setDetails(
                            new WebAuthenticationDetailsSource().buildDetails(request)
                        );
                        SecurityContextHolder.getContext().setAuthentication(authenticationToken);
                    }
                }
            } catch (JwtException exception) {
                SecurityContextHolder.clearContext();
            } catch (Exception exception) {
                SecurityContextHolder.clearContext();
            }
        }

        filterChain.doFilter(request, response);
    }
}
