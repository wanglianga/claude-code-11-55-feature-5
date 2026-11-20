package com.group.carbon.security;

import com.group.carbon.common.AuthContext;
import com.group.carbon.common.AuthContext.AuthUser;
import com.group.carbon.common.BizException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.stereotype.Component;
import org.springframework.web.servlet.HandlerInterceptor;

@Component
public class AuthInterceptor implements HandlerInterceptor {
  private final AuthService authService;

  public AuthInterceptor(AuthService authService) {
    this.authService = authService;
  }

  @Override
  public boolean preHandle(HttpServletRequest request, HttpServletResponse response, Object handler) {
    if ("OPTIONS".equalsIgnoreCase(request.getMethod())) return true;
    String path = request.getRequestURI();
    if (!path.startsWith("/api/")) return true;
    if (path.equals("/api/auth/login") || path.equals("/api/health")) return true;
    String token = request.getHeader("X-Auth-Token");
    if (token == null || token.isBlank()) {
      throw BizException.unauthorized("未登录");
    }
    AuthUser user = authService.parse(token);
    AuthContext.set(user);
    return true;
  }

  @Override
  public void afterCompletion(HttpServletRequest request, HttpServletResponse response,
                              Object handler, Exception ex) {
    AuthContext.clear();
  }
}
