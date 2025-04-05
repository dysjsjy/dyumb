package com.dy.umb.config;
import org.springframework.boot.web.servlet.FilterRegistrationBean;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class FilterConfig {

    @Bean
    public FilterRegistrationBean<CookieSecurityFilter> cookieSecurityFilter() {
        FilterRegistrationBean<CookieSecurityFilter> registrationBean = new FilterRegistrationBean<>();
        registrationBean.setFilter(new CookieSecurityFilter());
        registrationBean.addUrlPatterns("/*");
        return registrationBean;
    }
}