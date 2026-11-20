package com.group.carbon.common;

/** 携带需标记冲突的批次 id 的内部异常：外层捕获后在独立事务中落库冲突状态。 */
public class ConflictStateException extends RuntimeException {
  private final Long batchId;

  public ConflictStateException(Long batchId, String message) {
    super(message);
    this.batchId = batchId;
  }

  public Long getBatchId() { return batchId; }
}
