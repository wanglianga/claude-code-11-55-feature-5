package com.group.carbon.common;

import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.http.ResponseEntity;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.validation.FieldError;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.MissingServletRequestParameterException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

import java.util.Map;

@RestControllerAdvice
public class GlobalExceptionHandler {

  @ExceptionHandler(BizException.class)
  public ResponseEntity<Map<String, Object>> biz(BizException e) {
    return ResponseEntity.status(e.getStatus())
        .body(Map.of("error", e.getMessage(), "status", e.getStatus()));
  }

  @ExceptionHandler(MethodArgumentNotValidException.class)
  public ResponseEntity<Map<String, Object>> validation(MethodArgumentNotValidException e) {
    String msg = e.getBindingResult().getFieldErrors().stream()
        .findFirst().map(FieldError::getDefaultMessage).orElse("参数校验失败");
    return ResponseEntity.badRequest().body(Map.of("error", msg, "status", 400));
  }

  @ExceptionHandler({HttpMessageNotReadableException.class, MissingServletRequestParameterException.class})
  public ResponseEntity<Map<String, Object>> badBody(Exception e) {
    return ResponseEntity.badRequest().body(Map.of("error", "请求参数不合法: " + e.getMessage(), "status", 400));
  }

  @ExceptionHandler(DataIntegrityViolationException.class)
  public ResponseEntity<Map<String, Object>> conflict(DataIntegrityViolationException e) {
    return ResponseEntity.status(409)
        .body(Map.of("error", "数据完整性冲突（可能存在并发操作或重复数据），请刷新后重试", "status", 409));
  }

  @ExceptionHandler(org.springframework.web.servlet.resource.NoResourceFoundException.class)
  public ResponseEntity<Map<String, Object>> noResource(
      org.springframework.web.servlet.resource.NoResourceFoundException e) {
    return ResponseEntity.status(404)
        .body(Map.of("error", "资源不存在: " + e.getResourcePath(), "status", 404));
  }

  @ExceptionHandler(Exception.class)
  public ResponseEntity<Map<String, Object>> unknown(Exception e) {
    e.printStackTrace();
    return ResponseEntity.status(500)
        .body(Map.of("error", "服务器内部错误: " + e.getMessage(), "status", 500));
  }
}
