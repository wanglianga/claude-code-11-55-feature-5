package com.group.carbon.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;

import java.math.BigDecimal;
import java.util.List;

/** API 请求体定义。 */
public final class Requests {
  private Requests() {}

  public record LoginReq(@NotBlank(message = "用户名不能为空") String username,
                         @NotBlank(message = "密码不能为空") String password) {}

  /** plantId 为空时取批次工厂；集团采购批次可按行归属不同工厂；unit 为空时取能源类型规范单位；basis 显式声明计量基准 */
  public record RowInput(Long plantId,
                         Long productLineId,
                         @NotBlank(message = "能源类型不能为空") String energyType,
                         @NotNull(message = "数量不能为空") BigDecimal quantity,
                         String voucherNo,
                         String unit,
                         String basis) {}

  public record CreateBatchReq(@NotNull(message = "工厂不能为空") Long plantId,
                               @NotBlank(message = "批次类别不能为空") String category,
                               @NotBlank(message = "期间不能为空") String period,
                               String batchNo,
                               @NotEmpty(message = "批次至少包含一行数据") List<RowInput> rows) {}

  public record VoidReq(@NotBlank(message = "作废原因不能为空") String reason) {}

  public record CreateFactorReq(@NotBlank(message = "因子编码不能为空") String code,
                                @NotBlank(message = "因子名称不能为空") String name,
                                @NotBlank(message = "能源类型不能为空") String energyType,
                                @NotBlank(message = "地域不能为空") String region) {}

  public record CreateVersionReq(@NotNull(message = "因子值不能为空") BigDecimal value,
                                 @NotBlank(message = "适用期起不能为空") String validFrom,
                                 String validTo) {}

  public record CreateRunReq(@NotBlank(message = "期间不能为空") String period) {}

  public record CreateReportReq(@NotBlank(message = "报告标题不能为空") String title,
                                @NotBlank(message = "期间不能为空") String period,
                                @NotNull(message = "必须选择核算运行") Long runId) {}

  public record FreezeReq(@NotNull(message = "必须选择核算运行") Long runId, String note) {}

  public record ReasonReq(String reason) {}

  /** 登记校准/化验证据 */
  public record CreateEvidenceReq(@NotBlank String evidenceNo,
                                  @NotBlank String evidenceType,
                                  @NotBlank String title,
                                  String sourceRef,
                                  String issuer,
                                  @NotBlank String issuedDate,
                                  @NotBlank String effectiveFrom,
                                  String effectiveTo,
                                  Long plantId,
                                  String energyType,
                                  @NotBlank String measuredProp,
                                  @NotNull BigDecimal nominalValue,
                                  @NotBlank String unit,
                                  Integer sigFigs,
                                  BigDecimal uncertaintyPct,
                                  String note) {}

  /** 登记换算依据（物质/能源—单位—工厂—期间组合，草稿；审核员批准后生成有向换算边） */
  public record CreateConversionBaseReq(@NotBlank String code,
                                        @NotBlank String name,
                                        @NotBlank String energyType,
                                        Long plantId,
                                        @NotBlank String periodFrom,
                                        String periodTo,
                                        @NotBlank String sourceUnit,
                                        String sourceBasis,
                                        @NotBlank String targetUnit,
                                        String targetBasis,
                                        @NotBlank String edgeKind,
                                        @NotNull BigDecimal factorValue,
                                        Integer sigFigs,
                                        @NotNull BigDecimal uncertaintyPct,
                                        Long evidenceId,
                                        String conditionKey,
                                        String conditionValue,
                                        Boolean parallelConflict) {}
}
