
package com.haengunping.common.security;

import org.springframework.security.config.annotation.web.builders.HttpSecurity;

@FunctionalInterface
public interface ApiSecurityCustomizer {

    void customize(HttpSecurity http) throws Exception;
}
