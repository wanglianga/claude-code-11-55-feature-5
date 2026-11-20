package com.group.carbon.common;

/** 业务异常：携带 HTTP 状态码，由全局异常处理器转换为统一错误响应。 */
public class BizException extends RuntimeException {
  private final int status;

  public BizException(int status, String message) {
    super(message);
    this.status = status;
  }

  public int getStatus() { return status; }

  public static BizException badRequest(String msg) { return new BizException(400, msg); }
  public static BizException unauthorized(String msg) { return new BizException(401, msg); }
  public static BizException forbidden(String msg) { return new BizException(403, msg); }
  public static BizException notFound(String msg) { return new BizException(404, msg); }
  /** 冲突：并发修订、覆盖已核算采购量、重复运行等 */
  public static BizException conflict(String msg) { return new BizException(409, msg); }
}
