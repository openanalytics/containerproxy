/*
 * ContainerProxy
 *
 * Copyright (C) 2016-2025 Open Analytics
 *
 * ===========================================================================
 *
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the Apache License as published by
 * The Apache Software Foundation, either version 2 of the License, or
 * (at your option) any later version.
 *
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * Apache License for more details.
 *
 * You should have received a copy of the Apache License
 * along with this program.  If not, see <http://www.apache.org/licenses/>
 */
package eu.openanalytics.containerproxy.auth.impl.customHeader;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import net.minidev.json.parser.JSONParser;
import net.minidev.json.parser.ParseException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.authentication.event.AuthenticationSuccessEvent;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.AuthenticationException;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.web.util.matcher.AntPathRequestMatcher;
import org.springframework.security.web.util.matcher.NegatedRequestMatcher;
import org.springframework.security.web.util.matcher.OrRequestMatcher;
import org.springframework.security.web.util.matcher.RequestMatcher;
import org.springframework.web.filter.OncePerRequestFilter;

import javax.annotation.Nonnull;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

public class CustomHeaderAuthenticationFilter extends OncePerRequestFilter {

    private final Logger logger = LoggerFactory.getLogger(getClass());
    private final AuthenticationManager authenticationManager;
    private final ApplicationEventPublisher eventPublisher;

    // prevent re-login on logout-success page
    private static final RequestMatcher REQUEST_MATCHER = new NegatedRequestMatcher(new OrRequestMatcher(
        new AntPathRequestMatcher("/logout-success"),
        new AntPathRequestMatcher("/webjars/**"),
        new AntPathRequestMatcher("/css/**"))
    );

    private final String usernameHeaderName;
    private final String groupsHeaderName;

    public CustomHeaderAuthenticationFilter(AuthenticationManager authenticationManager, ApplicationEventPublisher eventPublisher, String usernameHeaderName, String groupsHeaderName) {
        this.authenticationManager = authenticationManager;
        this.eventPublisher = eventPublisher;
        this.usernameHeaderName = usernameHeaderName;
        this.groupsHeaderName = groupsHeaderName;
    }

    @Override
    protected void doFilterInternal(@Nonnull HttpServletRequest request, @Nonnull HttpServletResponse response, @Nonnull FilterChain chain) throws ServletException, IOException, AuthenticationException {
        if (!REQUEST_MATCHER.matches(request)) {
            chain.doFilter(request, response);
            return;
        }
        try {
            String remoteUser = request.getHeader(usernameHeaderName);
            if (remoteUser == null) {
                throw new CustomHeaderAuthenticationException(String.format("Missing username header '%s'", usernameHeaderName));
            }

            Authentication existingAuthentication = SecurityContextHolder.getContext().getAuthentication();
            if (existingAuthentication instanceof CustomHeaderAuthenticationToken) {
                if (!existingAuthentication.getPrincipal().equals(remoteUser)) {
                    throw new CustomHeaderAuthenticationException(String.format("Username in header '%s' does not match existing session '%s'", remoteUser, existingAuthentication.getPrincipal()));
                } else {
                    chain.doFilter(request, response);
                    return;
                }
            }

            Authentication authRequest = new CustomHeaderAuthenticationToken(remoteUser, parseGroups(request, remoteUser), false);
            Authentication authResult = authenticationManager.authenticate(authRequest);
            if (authResult == null) {
                throw new CustomHeaderAuthenticationException("No authentication");
            }

            SecurityContextHolder.getContext().setAuthentication(authResult);
            eventPublisher.publishEvent(new AuthenticationSuccessEvent(authResult));
        } catch (CustomHeaderAuthenticationException e) {
            logger.warn("Authentication failed: {}", e.getMessage());
            SecurityContextHolder.clearContext();
        } catch (Exception e) {
            logger.warn("Authentication failed", e);
            SecurityContextHolder.clearContext();
        }
        chain.doFilter(request, response);
    }

    private List<GrantedAuthority> parseGroups(HttpServletRequest request, String username) {
        if (groupsHeaderName == null) {
            return List.of();
        }
        String remoteGroups = request.getHeader(groupsHeaderName);
        if (remoteGroups == null) {
            logger.warn("Header '{}' does not contain the groups of user '{}', the proxy should always override this header. This is a security risk, users might spoof groups!", groupsHeaderName, username);
            return List.of();
        }

        remoteGroups = remoteGroups.strip();

        List<String> roles = new ArrayList<>();

        if (remoteGroups.startsWith("[")) {
            // this is probably json
            try {
                Object value = new JSONParser(JSONParser.MODE_PERMISSIVE).parse(remoteGroups);
                if (value instanceof List<?> valueList) {
                    valueList.forEach(o -> roles.add(o.toString()));
                    logger.debug("Parsed groups header as JSON: {} -> {}", groupsHeaderName, roles);
                }
            } catch (ParseException e) {
                // Unable to parse JSON
                logger.debug("Unable to parse groups header as JSON: {} -> {}", groupsHeaderName, remoteGroups);
            }
        } else {
            if (remoteGroups.contains(",")) {
                Arrays.stream(remoteGroups.split(",")).forEach(g -> roles.add(g.strip()));
            } else {
                // assuming it's a single role
                roles.add(remoteGroups);
            }
            logger.debug("Parsed groups header as comma-separated string: {} -> {}", groupsHeaderName, roles);
        }

        List<GrantedAuthority> authorities = new ArrayList<>();
        for (String role : roles) {
            String mappedRole = role.toUpperCase().startsWith("ROLE_") ? role : "ROLE_" + role;
            authorities.add(new SimpleGrantedAuthority(mappedRole.toUpperCase()));
        }
        return authorities;
    }

}
