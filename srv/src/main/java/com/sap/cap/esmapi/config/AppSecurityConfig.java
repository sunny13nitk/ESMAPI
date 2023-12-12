package com.sap.cap.esmapi.config;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.convert.converter.Converter;
import org.springframework.http.HttpMethod;
import org.springframework.scheduling.annotation.EnableAsync;
import org.springframework.security.authentication.AbstractAuthenticationToken;
import org.springframework.security.config.annotation.method.configuration.EnableGlobalMethodSecurity;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity;
import org.springframework.security.config.annotation.web.configuration.WebSecurityCustomizer;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.util.matcher.AntPathRequestMatcher;

import com.sap.cloud.security.xsuaa.XsuaaServiceConfiguration;
import com.sap.cloud.security.xsuaa.extractor.IasXsuaaExchangeBroker;
import com.sap.cloud.security.xsuaa.token.TokenAuthenticationConverter;
import com.sap.cloud.security.xsuaa.tokenflows.XsuaaTokenFlows;

@Configuration
@EnableWebSecurity

// @Order(1) // needs to have higher priority than CAP security config
@EnableGlobalMethodSecurity(prePostEnabled = true, securedEnabled = true, jsr250Enabled = true)
@EnableAsync
public class AppSecurityConfig
{

  @Autowired
  private XsuaaServiceConfiguration xsuaaServiceConfiguration;

  @Autowired
  XsuaaTokenFlows xsuaaTokenFlows;

  @Bean
  public SecurityFilterChain appFilterChain(HttpSecurity http) throws Exception
  {

    /*
     * ----------- Local Testing --------------------
     */

    // http.authorizeRequests().antMatchers(HttpMethod.GET,
    // "/static/**").permitAll();
    // http.requestMatchers().antMatchers("/api/**").antMatchers("/esslocal/**").antMatchers("/poclocal/**").and().csrf()
    // .disable() // don't insist on csrf tokens in put, post etc.
    // .authorizeRequests().anyRequest().permitAll();

    /*
     * ----------- CF Deployment --------------------
     */

    // @formatter:off
    http.sessionManagement().sessionCreationPolicy(SessionCreationPolicy.STATELESS)
        // session is created by approuter
        .and().authorizeRequests() // authorize all requests
        .antMatchers("/api/**").hasAuthority("Administrators") // Only Administrators
        // Allowed
        .antMatchers("/ess/**").authenticated() // Only Authenticated user(s) via IDP
        // allowed
        .antMatchers("/lso/**").authenticated() // Only Authenticated user(s) via IDP
        .antMatchers(HttpMethod.GET, "/static/images/**").permitAll().antMatchers(HttpMethod.GET, "/static/css/**")
        .permitAll().antMatchers(HttpMethod.GET, "/static/js/**").permitAll()
        // allowed
        .anyRequest().denyAll() // Deny any other endpoint access then listed above
        .and().oauth2ResourceServer().bearerTokenResolver(new IasXsuaaExchangeBroker(xsuaaTokenFlows)).jwt()
        .jwtAuthenticationConverter(getJwtAuthoritiesConverter());
    // @formatter:on

    return http.build();

  }

  @Bean
  public WebSecurityCustomizer webSecurityCustomizer() throws Exception
  {
    return (web) -> web.ignoring().antMatchers("/static/**").antMatchers("/images/**").antMatchers("/css/**")
        .antMatchers("/js/**");
  }

  // /*
  // ----------- CF Deployment --------------------
  // */
  Converter<Jwt, AbstractAuthenticationToken> getJwtAuthoritiesConverter()
  {
    TokenAuthenticationConverter converter = new TokenAuthenticationConverter(xsuaaServiceConfiguration);
    converter.setLocalScopeAsAuthorities(true);
    return converter;
  }

}
