package com.group.carbon.common;

import com.group.carbon.common.Types.Role;

/** 当前请求的登录用户（ThreadLocal）。 */
public final class AuthContext {
  private AuthContext() {}

  public record AuthUser(Long id, String username, String displayName, Role role) {}

  private static final ThreadLocal<AuthUser> HOLDER = new ThreadLocal<>();

  public static void set(AuthUser user) { HOLDER.set(user); }
  public static AuthUser get() { return HOLDER.get(); }
  public static void clear() { HOLDER.remove(); }

  public static AuthUser require() {
    AuthUser u = HOLDER.get();
    if (u == null) throw BizException.unauthorized("未登录或登录已过期");
    return u;
  }

  public static void requireRole(Role... roles) {
    AuthUser u = require();
    for (Role r : roles) if (u.role() == r) return;
    throw BizException.forbidden("当前角色无权执行该操作（需要 " + java.util.Arrays.toString(roles) + "）");
  }
}
