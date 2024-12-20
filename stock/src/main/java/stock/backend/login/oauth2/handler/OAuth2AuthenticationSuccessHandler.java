package com.example.project.oauth2.oauth2.handler;

import com.example.project.oauth2.oauth2.HttpCookieOAuth2AuthorizationRequestRepository;
import com.example.project.oauth2.oauth2.service.OAuth2UserPrincipal;
import com.example.project.oauth2.oauth2.user.OAuth2Provider;
import com.example.project.oauth2.oauth2.user.OAuth2UserUnlinkManage;
import com.example.project.oauth2.oauth2.util.CookieUtils;
import jakarta.servlet.http.Cookie;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.security.core.Authentication;
import org.springframework.security.web.authentication.SimpleUrlAuthenticationSuccessHandler;
import org.springframework.stereotype.Component;
import org.springframework.web.util.UriComponents;
import org.springframework.web.util.UriComponentsBuilder;

import java.io.IOException;
import java.util.Objects;
import java.util.Optional;

import static com.example.project.oauth2.oauth2.HttpCookieOAuth2AuthorizationRequestRepository.MODE_PARAM_COOKIE_NAME;
import static com.example.project.oauth2.oauth2.HttpCookieOAuth2AuthorizationRequestRepository.REDIRECT_URI_PARAM_COOKIE_NAME;

@Slf4j
@RequiredArgsConstructor
@Component
public class OAuth2AuthenticationSuccessHandler extends SimpleUrlAuthenticationSuccessHandler {
    private final HttpCookieOAuth2AuthorizationRequestRepository httpCookieOAuth2AuthorizationRequestRepository;
    private final OAuth2UserUnlinkManage oAuth2UserUnlinkManage;
    @Override
    public void onAuthenticationSuccess(HttpServletRequest request, HttpServletResponse response, Authentication authentication)throws IOException{
        String targetUrl;
        targetUrl = determineTargetUrl(request,response,authentication);
        if(response.isCommitted()){
            logger.debug("Response has already been commited. Unable to redirect to" + targetUrl);
            return;
        }
        clearAuthenticationAttributes(request,response);
        getRedirectStrategy().sendRedirect(request,response,targetUrl);
    }
    protected String determineTargetUrl(HttpServletRequest request,HttpServletResponse response,Authentication authentication){
        Optional<String> redirectUri = CookieUtils.getCookie(request,REDIRECT_URI_PARAM_COOKIE_NAME).map(Cookie::getValue);
        String targetUrl = redirectUri.orElse(getDefaultTargetUrl());
        String mode = CookieUtils.getCookie(request,MODE_PARAM_COOKIE_NAME).map(Cookie::getValue).orElse("");
        OAuth2UserPrincipal principal = getOAuth2UserPrincipal(authentication);
        if (principal == null){
            return UriComponentsBuilder.fromUriString(targetUrl).queryParam("error","Login failed").build().toUriString();
        }
        if ("login".equalsIgnoreCase(mode)){
            log.info("email={}, name={}, nickname={}, accessToken={}",principal.getUserInfo().getEmail(),
                    principal.getUserInfo().getName(),
                    principal.getUserInfo().getNickname(),
                    principal.getUserInfo().getAccessToken()
            );
            String accessToken = "test_access_token";
            String refreshToken = "test_refresh_token";
            return UriComponentsBuilder.fromUriString(targetUrl)
                    .queryParam("access_token",accessToken)
                    .queryParam("refresh_token",refreshToken)
                    .build().toUriString();
        }else if("unlink".equalsIgnoreCase(mode)){
            String accessToken = principal.getUserInfo().getAccessToken();
            OAuth2Provider provider = principal.getUserInfo().getProvider();
            oAuth2UserUnlinkManage.unlink(provider,accessToken);
            return UriComponentsBuilder.fromUriString(targetUrl)
                    .build()
                    .toUriString();
        }
        return UriComponentsBuilder.fromUriString(targetUrl)
                .queryParam("error","Login failed")
                .build()
                .toUriString();
    }

    private OAuth2UserPrincipal getOAuth2UserPrincipal(Authentication authentication) {
        Object principal = authentication.getPrincipal();
        if (principal instanceof OAuth2UserPrincipal){
            return (OAuth2UserPrincipal) principal;
        }
        return null;
    }
    protected void clearAuthenticationAttributes(HttpServletRequest request,HttpServletResponse response){
        super.clearAuthenticationAttributes(request);
        httpCookieOAuth2AuthorizationRequestRepository.removeAuthorizationRequestCookies(request,response);
    }
}
