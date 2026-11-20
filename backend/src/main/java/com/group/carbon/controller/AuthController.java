package com.group.carbon.controller;

import com.group.carbon.common.AuthContext;
import com.group.carbon.dto.Requests.LoginReq;
import com.group.carbon.security.AuthService;
import jakarta.validation.Valid;
import org.springframework.web.bind.annotation.*;

import java.util.LinkedHashMap;
import java.util.Map;

@RestController
@RequestMapping("/api/auth")
public class AuthController {
  private final AuthService authService;

  public AuthController(AuthService authService) {
    this.authService = authService;
  }

  @PostMapping("/login")
  public Map<String, Object> login(@Valid @RequestBody LoginReq req) {
    AuthService.LoginResult result = authService.login(req.username(), req.password());
    Map<String, Object> out = new LinkedHashMap<>();
    out.put("token", result.token());
    out.put("user", result.user());
    return out;
  }

  @GetMapping("/me")
  public Object me() {
    return AuthContext.require();
  }
}

@RestController
@RequestMapping("/api")
class HealthController {
  @GetMapping("/health")
  public Map<String, String> health() {
    return Map.of("status", "UP");
  }
}
