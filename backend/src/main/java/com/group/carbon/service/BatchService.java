package com.group.carbon.service;

import com.group.carbon.common.BizException;
import com.group.carbon.common.ConflictStateException;
import com.group.carbon.common.EnergyTypes;
import com.group.carbon.common.Types.*;
import com.group.carbon.dto.Requests.CreateBatchReq;
import com.group.carbon.dto.Requests.RowInput;
import com.group.carbon.model.*;
import com.group.carbon.repo.*;
import org.springframework.context.annotation.Lazy;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.*;
import java.util.stream.Collectors;

/**
 * 来源批次服务：
 * - 上传/核验/驳回/修订/作废；
 * - 采购批次按 来源行 + 供应商凭证 检测冲突；
 * - 已被核算消耗的采购量不允许被后续上传覆盖；
 * - 核验/作废后触发影响评估。
 */
@Service
public class BatchService {
  private final SourceBatchRepo batchRepo;
  private final SourceRowRepo rowRepo;
  private final EmissionLineageRepo lineageRepo;
  private final PlantRepo plantRepo;
  private final ProductLineRepo lineRepo;
  private final CalcRunRepo runRepo;
  private final ReportVersionRepo versionRepo;
  private final ImpactService impactService;
  private final BatchService self;

  public BatchService(SourceBatchRepo batchRepo, SourceRowRepo rowRepo,
                      EmissionLineageRepo lineageRepo, PlantRepo plantRepo,
                      ProductLineRepo lineRepo, CalcRunRepo runRepo,
                      ReportVersionRepo versionRepo, @Lazy ImpactService impactService,
                      @Lazy BatchService self) {
    this.batchRepo = batchRepo;
    this.rowRepo = rowRepo;
    this.lineageRepo = lineageRepo;
    this.plantRepo = plantRepo;
    this.lineRepo = lineRepo;
    this.runRepo = runRepo;
    this.versionRepo = versionRepo;
    this.impactService = impactService;
    this.self = self;
  }

  private static final Set<String> CATEGORIES = Set.of("FUEL", "ELECTRICITY", "PROCUREMENT");

  // ---------------- 查询 ----------------

  public List<SourceBatch> list(String period, String status, String category) {
    List<SourceBatch> all = period != null && !period.isBlank()
        ? batchRepo.findByPeriodOrderByIdDesc(period) : batchRepo.findAllByOrderByIdDesc();
    return all.stream()
        .filter(b -> status == null || status.isBlank() || b.getStatus().name().equals(status))
        .filter(b -> category == null || category.isBlank() || b.getCategory().equals(category))
        .toList();
  }

  public Map<String, Object> detail(Long id) {
    SourceBatch batch = getBatch(id);
    List<SourceRow> rows = rowRepo.findByBatchIdOrderByRowNo(id);
    Set<Long> consumed = consumedRowIds(rows.stream().map(SourceRow::getId).toList());
    Map<String, Object> out = new LinkedHashMap<>();
    out.put("batch", batch);
    out.put("rows", rows);
    out.put("consumedRowIds", consumed);
    out.put("revisions", batchRepo.findByReplacesBatchIdOrderByIdDesc(id));
    return out;
  }

  private SourceBatch getBatch(Long id) {
    return batchRepo.findById(id).orElseThrow(() -> BizException.notFound("批次不存在: " + id));
  }

  private Set<Long> consumedRowIds(List<Long> rowIds) {
    if (rowIds.isEmpty()) return Set.of();
    return lineageRepo.findBySourceRowIdIn(rowIds).stream()
        .map(EmissionLineage::getSourceRowId).collect(Collectors.toSet());
  }

  // ---------------- 上传 ----------------

  @Transactional
  public SourceBatch create(String username, CreateBatchReq req) {
    Plant plant = plantRepo.findById(req.plantId())
        .orElseThrow(() -> BizException.badRequest("工厂不存在: " + req.plantId()));
    SourceBatch batch = new SourceBatch();
    batch.setBatchNo(req.batchNo() != null && !req.batchNo().isBlank()
        ? req.batchNo() : RunService.newNo("B"));
    batch.setPlantId(plant.getId());
    batch.setCategory(req.category());
    batch.setPeriod(req.period());
    batch.setStatus(BatchStatus.UPLOADED);
    batch.setUploadedBy(username);
    batch.setUploadedAt(LocalDateTime.now());
    validateAndFill(batch, req.rows());
    batchRepo.save(batch);
    saveRows(batch, req.rows());
    return batch;
  }

  private void validateAndFill(SourceBatch batch, List<RowInput> rows) {
    RunService.validatePeriod(batch.getPeriod());
    if (!CATEGORIES.contains(batch.getCategory())) {
      throw BizException.badRequest("批次类别必须为 FUEL / ELECTRICITY / PROCUREMENT");
    }
    Set<String> vouchers = new HashSet<>();
    for (RowInput row : rows) {
      EnergyTypes.Meta meta = EnergyTypes.metaOf(row.energyType());
      if (!meta.category().equals(batch.getCategory())) {
        throw BizException.badRequest("能源类型 " + row.energyType() + " 与批次类别 " + batch.getCategory() + " 不匹配");
      }
      if (row.quantity() == null || row.quantity().compareTo(BigDecimal.ZERO) <= 0) {
        throw BizException.badRequest("数量必须为正数");
      }
      if (row.productLineId() != null && lineRepo.findById(row.productLineId()).isEmpty()) {
        throw BizException.badRequest("产品线不存在: " + row.productLineId());
      }
      if (row.plantId() != null && plantRepo.findById(row.plantId()).isEmpty()) {
        throw BizException.badRequest("行级工厂不存在: " + row.plantId());
      }
      if ("PROCUREMENT".equals(batch.getCategory())) {
        if (row.voucherNo() == null || row.voucherNo().isBlank()) {
          throw BizException.badRequest("采购行必须填写供应商凭证号");
        }
        if (!vouchers.add(row.voucherNo())) {
          throw BizException.badRequest("批次内供应商凭证号重复: " + row.voucherNo());
        }
      }
    }
  }

  private void saveRows(SourceBatch batch, List<RowInput> rows) {
    int i = 1;
    for (RowInput input : rows) {
      EnergyTypes.Meta meta = EnergyTypes.metaOf(input.energyType());
      SourceRow row = new SourceRow();
      row.setBatchId(batch.getId());
      row.setRowNo(i++);
      row.setPlantId(input.plantId() != null ? input.plantId() : batch.getPlantId());
      row.setProductLineId(input.productLineId());
      row.setScope(meta.scope());
      row.setEnergyType(input.energyType());
      row.setQuantity(input.quantity());
      // 允许按表计/采购原始单位登记；为空时回退能源类型规范单位
      row.setUnit(input.unit() != null && !input.unit().isBlank() ? input.unit() : meta.unit());
      row.setBasis(input.basis());
      row.setVoucherNo(input.voucherNo());
      row.setPeriod(batch.getPeriod());
      row.setStatus(RowStatus.ACTIVE);
      rowRepo.save(row);
    }
    batch.setRowCount(rows.size());
    batchRepo.save(batch);
  }

  // ---------------- 核验 ----------------

  /** 核验入口：冲突时先在独立事务中把批次标记为 CONFLICT，再向调用方返回 409。 */
  public SourceBatch verify(Long id, String username) {
    try {
      return self.verifyInternal(id, username);
    } catch (ConflictStateException e) {
      self.markConflict(e.getBatchId());
      throw BizException.conflict(e.getMessage());
    }
  }

  /** 独立事务落库冲突标记（核验事务回滚后执行，不受回滚影响）。 */
  @Transactional(propagation = Propagation.REQUIRES_NEW)
  public void markConflict(Long batchId) {
    batchRepo.findById(batchId).ifPresent(b -> {
      b.setStatus(BatchStatus.CONFLICT);
      batchRepo.save(b);
    });
  }

  @Transactional
  public SourceBatch verifyInternal(Long id, String username) {
    SourceBatch batch = batchRepo.findByIdForUpdate(id)
        .orElseThrow(() -> BizException.notFound("批次不存在: " + id));
    if (batch.getStatus() != BatchStatus.UPLOADED) {
      throw BizException.conflict("只有“已上传”状态的批次可以核验，当前状态: " + batch.getStatus());
    }
    List<SourceRow> rows = rowRepo.findByBatchIdOrderByRowNo(id);

    if ("PROCUREMENT".equals(batch.getCategory())) {
      checkVoucherConflicts(batch, rows);
    }

    boolean isRevision = batch.getReplacesBatchId() != null;
    if (isRevision) {
      applyRevisionRules(batch, rows);
    }

    batch.setStatus(BatchStatus.VERIFIED);
    batch.setVerifiedBy(username);
    batch.setVerifiedAt(LocalDateTime.now());
    batchRepo.save(batch);

    // 触发影响评估：该期间已有完成运行或已发布报告时
    if (hasCompletedRunOrPublishedReport(batch.getPeriod())) {
      impactService.assess(batch.getPeriod(),
          isRevision ? TriggerType.SOURCE_REVISION : TriggerType.SOURCE_SUPPLEMENT,
          (isRevision ? "批次修订核验 " : "来源批次补录核验 ") + batch.getBatchNo());
    }
    return batch;
  }

  /** 采购凭证冲突：其他已核验批次存在同凭证号但数量/工厂不一致的生效行。 */
  private void checkVoucherConflicts(SourceBatch batch, List<SourceRow> rows) {
    for (SourceRow row : rows) {
      if (row.getVoucherNo() == null) continue;
      for (SourceRow other : rowRepo.findByVoucherNo(row.getVoucherNo())) {
        if (other.getBatchId().equals(batch.getId()) || other.getStatus() != RowStatus.ACTIVE) continue;
        SourceBatch otherBatch = batchRepo.findById(other.getBatchId()).orElse(null);
        if (otherBatch == null || otherBatch.getStatus() != BatchStatus.VERIFIED) continue;
        boolean qtyDiffers = other.getQuantity().compareTo(row.getQuantity()) != 0;
        boolean plantDiffers = !other.getPlantId().equals(row.getPlantId());
        if (qtyDiffers || plantDiffers) {
          throw new ConflictStateException(batch.getId(), String.format(
              "供应商凭证冲突：凭证 %s 与批次 %s 第 %d 行重复（数量 %s vs %s），已标记冲突",
              row.getVoucherNo(), otherBatch.getBatchNo(), other.getRowNo(),
              other.getQuantity(), row.getQuantity()));
        }
      }
    }
  }

  /** 修订规则：已被核算消耗的采购量不得被修订覆盖；通过后旧批次被取代。 */
  private void applyRevisionRules(SourceBatch revision, List<SourceRow> newRows) {
    SourceBatch old = batchRepo.findByIdForUpdate(revision.getReplacesBatchId())
        .orElseThrow(() -> BizException.badRequest("被修订的原批次不存在"));
    if (old.getStatus() != BatchStatus.VERIFIED) {
      throw BizException.conflict("只能修订“已核验”状态的批次，原批次当前状态: " + old.getStatus());
    }
    List<SourceRow> oldRows = rowRepo.findByBatchIdOrderByRowNo(old.getId());
    Set<Long> consumed = consumedRowIds(oldRows.stream().map(SourceRow::getId).toList());

    if ("PROCUREMENT".equals(old.getCategory()) && !consumed.isEmpty()) {
      Map<String, SourceRow> newByVoucher = newRows.stream()
          .filter(r -> r.getVoucherNo() != null)
          .collect(Collectors.toMap(SourceRow::getVoucherNo, r -> r, (a, b) -> a));
      for (SourceRow oldRow : oldRows) {
        if (!consumed.contains(oldRow.getId())) continue;
        SourceRow match = oldRow.getVoucherNo() != null ? newByVoucher.get(oldRow.getVoucherNo()) : null;
        if (match == null) {
          throw new ConflictStateException(revision.getId(), String.format(
              "修订冲突：凭证 %s 的采购量 %s %s 已被核算运行消耗，修订不能删除或缺失该行",
              oldRow.getVoucherNo(), oldRow.getQuantity(), oldRow.getUnit()));
        }
        if (match.getQuantity().compareTo(oldRow.getQuantity()) != 0) {
          throw new ConflictStateException(revision.getId(), String.format(
              "修订冲突：凭证 %s 的采购量已被核算消耗（%s %s），不允许被后续上传覆盖为 %s %s",
              oldRow.getVoucherNo(), oldRow.getQuantity(), oldRow.getUnit(),
              match.getQuantity(), match.getUnit()));
        }
      }
    }

    old.setStatus(BatchStatus.SUPERSEDED);
    batchRepo.save(old);
    for (SourceRow oldRow : oldRows) {
      oldRow.setStatus(RowStatus.SUPERSEDED);
      rowRepo.save(oldRow);
    }
  }

  @Transactional
  public SourceBatch reject(Long id, String username, String reason) {
    SourceBatch batch = batchRepo.findByIdForUpdate(id)
        .orElseThrow(() -> BizException.notFound("批次不存在: " + id));
    if (batch.getStatus() != BatchStatus.UPLOADED) {
      throw BizException.conflict("只有“已上传”状态的批次可以驳回");
    }
    batch.setStatus(BatchStatus.REJECTED);
    batch.setVerifiedBy(username);
    batch.setVerifiedAt(LocalDateTime.now());
    batch.setVoidReason(reason);
    return batchRepo.save(batch);
  }

  // ---------------- 修订 ----------------

  @Transactional
  public SourceBatch revise(Long id, String username, CreateBatchReq req) {
    SourceBatch old = batchRepo.findByIdForUpdate(id)
        .orElseThrow(() -> BizException.notFound("批次不存在: " + id));
    if (old.getStatus() != BatchStatus.VERIFIED) {
      throw BizException.conflict("只有“已核验”状态的批次可以修订，当前状态: " + old.getStatus());
    }
    // 并发修订检测：同一批次同一时刻只允许一个进行中的修订
    if (batchRepo.existsByReplacesBatchIdAndStatus(id, BatchStatus.UPLOADED)) {
      throw BizException.conflict("该批次已存在进行中的修订（另一工厂/用户已提交），请刷新后处理冲突");
    }
    SourceBatch revision = new SourceBatch();
    revision.setBatchNo(RunService.newNo("B"));
    revision.setPlantId(old.getPlantId());
    revision.setCategory(old.getCategory());
    revision.setPeriod(old.getPeriod());
    revision.setStatus(BatchStatus.UPLOADED);
    revision.setUploadedBy(username);
    revision.setUploadedAt(LocalDateTime.now());
    revision.setReplacesBatchId(old.getId());
    revision.setRevisionNo(old.getRevisionNo() + 1);
    validateAndFill(revision, req.rows());
    batchRepo.save(revision);
    saveRows(revision, req.rows());
    return revision;
  }

  // ---------------- 作废（来源缺失） ----------------

  @Transactional
  public SourceBatch voidBatch(Long id, String username, String reason) {
    SourceBatch batch = batchRepo.findByIdForUpdate(id)
        .orElseThrow(() -> BizException.notFound("批次不存在: " + id));
    if (batch.getStatus() != BatchStatus.VERIFIED) {
      throw BizException.conflict("只有“已核验”状态的批次可以作废，当前状态: " + batch.getStatus());
    }
    List<SourceRow> rows = rowRepo.findByBatchIdOrderByRowNo(id);
    Set<Long> consumed = consumedRowIds(rows.stream().map(SourceRow::getId).toList());
    batch.setStatus(BatchStatus.VOID);
    batch.setVoidReason(reason);
    batchRepo.save(batch);
    for (SourceRow row : rows) {
      row.setStatus(RowStatus.VOID);
      rowRepo.save(row);
    }
    if (!consumed.isEmpty()) {
      // 已用于核算的来源被发现缺失：生成影响评估与重述建议，不直接改已发布报告
      impactService.assess(batch.getPeriod(), TriggerType.SOURCE_MISSING,
          "批次 " + batch.getBatchNo() + " 来源缺失（" + reason + "），已影响 " + consumed.size() + " 行已核算数据");
    }
    return batch;
  }

  private boolean hasCompletedRunOrPublishedReport(String period) {
    boolean hasRun = runRepo.findFirstByPeriodAndPurposeAndStatusOrderByIdDesc(
        period, RunPurpose.OFFICIAL, RunStatus.COMPLETED).isPresent();
    boolean hasPublished = !versionRepo.findPublishedByPeriod(period).isEmpty();
    return hasRun || hasPublished;
  }
}
