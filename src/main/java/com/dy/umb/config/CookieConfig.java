package com.dy.umb.config;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.session.web.http.CookieSerializer;
import org.springframework.session.web.http.DefaultCookieSerializer;

import javax.servlet.http.Cookie;
import javax.servlet.http.HttpServletResponse;


//@Configuration
//public class CookieConfig {
//
//    @Bean
//    public CookieSerializer cookieSerializer() {
//        DefaultCookieSerializer serializer = new DefaultCookieSerializer();
//        serializer.setSameSite("Strict"); // 设置 SameSite 标志
//        return serializer;
//    }
//
//    public void setSecureCookie(HttpServletResponse response) {
//        Cookie cookie = new Cookie("sessionId", "123456789");
//        cookie.setHttpOnly(true); // 设置 HttpOnly 标志
//        cookie.setSecure(true); // 设置 Secure 标志
//        cookie.setPath("/");
//        response.addCookie(cookie);
//    }
//}