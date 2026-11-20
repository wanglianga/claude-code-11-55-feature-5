package com.group.carbon.common;

/** 平台全部业务枚举，集中定义避免散落。 */
public final class Types {
  private Types() {}

  public enum Role { ADMIN, ANALYST, AUDITOR, FINANCE }

  /** 批次状态：已上传 / 已核验 / 已驳回 / 冲突 / 被取代 / 已作废 */
  public enum BatchStatus { UPLOADED, VERIFIED, REJECTED, CONFLICT, SUPERSEDED, VOID }

  public enum RowStatus { ACTIVE, SUPERSEDED, VOID }

  /** 因子版本状态：草稿 / 已批准 / 被替换 / 已停用 */
  public enum FactorStatus { DRAFT, APPROVED, REPLACED, RETIRED }

  public enum RunStatus { RUNNING, COMPLETED, FAILED }

  /** OFFICIAL=正式运行；CANDIDATE=影响评估产生的候选运行 */
  public enum RunPurpose { OFFICIAL, CANDIDATE }

  /** OK=正常；PENDING_FACTOR=缺少适用批准因子，指标待核 */
  public enum ResultStatus { OK, PENDING_FACTOR, PENDING_CONVERSION }

  /** RECONCILE_FAILED 即“待核”状态：勾稽不平或存在待核指标 */
  public enum ReportStatus { DRAFT, PENDING_REVIEW, RECONCILE_FAILED, APPROVED, PUBLISHED }

  public enum VersionStatus { FROZEN, PUBLISHED, SUPERSEDED, RESTATED }

  public enum ReconcileStatus { OK, MISMATCH, PENDING_FACTOR }

  public enum TriggerType { SOURCE_SUPPLEMENT, SOURCE_REVISION, SOURCE_MISSING, FACTOR_REVISION, CONVERSION_REVISION }

  public enum ImpactStatus { OPEN, ACKNOWLEDGED, RESOLVED }

  public enum RestatementStatus { PROPOSED, ACCEPTED, REJECTED, RESOLVED }

  // ---------- 测量换算与派生指标（特性 5） ----------

  /** 证据类型：校准证书 / 化验报告 / 密度修订单 / 人工桥接说明 */
  public enum EvidenceType { CALIBRATION, LAB_ASSAY, DENSITY_REVISION, MANUAL_BRIDGE }

  /** 换算依据/边状态 */
  public enum ConversionBaseStatus { DRAFT, APPROVED, SUPERSEDED }

  /** 边类型：同量纲单位 / 计量基准切换（湿基↔干基、标况↔工况）/ 物性换算（密度、热值）/ 复合单位展开 */
  public enum EdgeKind { UNIT_PREFIX, BASIS_SWITCH, PROPERTY, COMPOUND }

  /** 活动量换算证明四类结果 */
  public enum ConversionStatus {
    CONVERTIBLE,            // 可直接转换
    MANUAL_BRIDGE,          // 需人工桥接
    DIMENSION_INCOMPATIBLE, // 量纲不相容
    INSUFFICIENT_EVIDENCE   // 证据不足
  }

  /** 换算冲突处置状态 */
  public enum ConflictStatus { OPEN, RESOLVED }

  /** 派生指标类型与状态 */
  public enum MetricKind { ENERGY, EMISSION, INTENSITY }
  public enum MetricStatus { OK, PENDING_CONVERSION, PENDING_FACTOR, EMPTY_DENOMINATOR }

  /** 披露单元格追溯状态 */
  public enum TraceStatus { TRACEABLE, PENDING, EMPTY_DENOMINATOR, CONFLICT }
}
