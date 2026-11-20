package com.group.carbon.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.group.carbon.common.BizException;
import com.group.carbon.common.EnergyTypes;
import com.group.carbon.common.MeasurementUnits;
import com.group.carbon.common.Types.*;
import com.group.carbon.dto.Requests.CreateConversionBaseReq;
import com.group.carbon.dto.Requests.CreateEvidenceReq;
import com.group.carbon.model.*;
import com.group.carbon.repo.*;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDate;
import java.time.YearMonth;
import java.util.*;

/**
 * 测量换算与派生指标服务（特性 5 核心）。
 *
 * 设计取舍（对应需求两条路线）：换算依据（ConversionBase）经批准后生成有向换算边（ConversionEdge），
 * 批量核算时在边图上逐边求值、带循环检测与中途截断；预编译模板与图路径共用同一份依据表，
 * 模板不是图路径的缓存，图路径也不是模板的事后核查。
 *
 * 四类证明结果：CONVERTIBLE 可直接转换 / MANUAL_BRIDGE 需人工桥接 /
 * DIMENSION_INCOMPATIBLE 量纲不相容 / INSUFFICIENT_EVIDENCE 证据不足。
 * 湿基/干基、标况/工况、净热值/低位热值按量纲+基准匹配，不凭单位名称互换；
 * 同一读数被两条链解释为不同能量时保留冲突，不取平均。
 */
@Service
public class ConversionService {
  private final MeasurementEvidenceRepo evidenceRepo;
  private final ConversionBaseRepo baseRepo;
  private final ConversionEdgeRepo edgeRepo;
  private final ActivityConversionRepo convRepo;
  private final ConversionConflictRepo conflictRepo;
  private final DerivedMetricRepo metricRepo;
  private final DisclosureCellTraceRepo traceRepo;
  private final SourceRowRepo rowRepo;
  private final EmissionResultRepo resultRepo;
  private final PlantRepo plantRepo;
  private final ReportVersionRepo versionRepo;
  private final ReportItemRepo itemRepo;
  private final ImpactService impactService;
  private final ObjectMapper json = new ObjectMapper();

  /** 路径搜索最大深度，防止复合单位下的长尾路径；正常物理换算链 ≤ 4 边。 */
  private static final int MAX_DEPTH = 8;
  /** 两条链目标量相对差异超过该阈值即判为冲突（不取平均）。 */
  private static final BigDecimal CONFLICT_EPS = new BigDecimal("0.002");

  public ConversionService(MeasurementEvidenceRepo evidenceRepo, ConversionBaseRepo baseRepo,
                           ConversionEdgeRepo edgeRepo, ActivityConversionRepo convRepo,
                           ConversionConflictRepo conflictRepo, DerivedMetricRepo metricRepo,
                           DisclosureCellTraceRepo traceRepo, SourceRowRepo rowRepo,
                           EmissionResultRepo resultRepo, PlantRepo plantRepo,
                           ReportVersionRepo versionRepo, ReportItemRepo itemRepo,
                           ImpactService impactService) {
    this.evidenceRepo = evidenceRepo;
    this.baseRepo = baseRepo;
    this.edgeRepo = edgeRepo;
    this.convRepo = convRepo;
    this.conflictRepo = conflictRepo;
    this.metricRepo = metricRepo;
    this.traceRepo = traceRepo;
    this.rowRepo = rowRepo;
    this.resultRepo = resultRepo;
    this.plantRepo = plantRepo;
    this.versionRepo = versionRepo;
    this.itemRepo = itemRepo;
    this.impactService = impactService;
  }

  // ================= 证据与依据维护 =================

  @Transactional
  public MeasurementEvidence createEvidence(String username, CreateEvidenceReq req) {
    if (evidenceRepo.findByEvidenceNo(req.evidenceNo()).isPresent()) {
      throw BizException.conflict("证据编号已存在: " + req.evidenceNo());
    }
    EvidenceType type;
    try { type = EvidenceType.valueOf(req.evidenceType()); }
    catch (IllegalArgumentException e) {
      throw BizException.badRequest("证据类型必须为 CALIBRATION/LAB_ASSAY/DENSITY_REVISION/MANUAL_BRIDGE");
    }
    MeasurementEvidence ev = new MeasurementEvidence();
    ev.setEvidenceNo(req.evidenceNo());
    ev.setEvidenceType(type);
    ev.setTitle(req.title());
    ev.setSourceRef(req.sourceRef());
    ev.setIssuer(req.issuer());
    ev.setIssuedDate(parseDate(req.issuedDate(), "出具日期"));
    LocalDate from = parseDate(req.effectiveFrom(), "适用期起");
    ev.setEffectiveFrom(from);
    ev.setEffectiveTo(req.effectiveTo() == null || req.effectiveTo().isBlank()
        ? null : parseDate(req.effectiveTo(), "适用期止"));
    ev.setPlantId(req.plantId());
    ev.setEnergyType(req.energyType());
    ev.setMeasuredProp(req.measuredProp());
    ev.setNominalValue(req.nominalValue());
    ev.setUnit(req.unit());
    ev.setSigFigs(req.sigFigs());
    ev.setUncertaintyPct(req.uncertaintyPct());
    ev.setNote(req.note());
    ev.setCreatedBy(username);
    return evidenceRepo.save(ev);
  }

  @Transactional
  public ConversionBase createBase(String username, CreateConversionBaseReq req) {
    if (baseRepo.findByCode(req.code()).isPresent()) {
      throw BizException.conflict("换算依据编码已存在: " + req.code());
    }
    EdgeKind kind;
    try { kind = EdgeKind.valueOf(req.edgeKind()); }
    catch (IllegalArgumentException e) {
      throw BizException.badRequest("边类型必须为 UNIT_PREFIX/BASIS_SWITCH/PROPERTY/COMPOUND");
    }
    if (req.factorValue().compareTo(BigDecimal.ZERO) <= 0) {
      throw BizException.badRequest("换算系数必须为正数");
    }
    if (req.evidenceId() != null && evidenceRepo.findById(req.evidenceId()).isEmpty()) {
      throw BizException.badRequest("引用的证据不存在: " + req.evidenceId());
    }
    ConversionBase b = new ConversionBase();
    b.setCode(req.code());
    b.setName(req.name());
    b.setEnergyType(req.energyType());
    b.setPlantId(req.plantId());
    b.setPeriodFrom(req.periodFrom());
    b.setPeriodTo(req.periodTo());
    b.setSourceUnit(req.sourceUnit());
    b.setSourceDimension(MeasurementUnits.dimensionOf(req.sourceUnit()));
    b.setSourceBasis(req.sourceBasis());
    b.setTargetUnit(req.targetUnit());
    b.setTargetDimension(MeasurementUnits.dimensionOf(req.targetUnit()));
    b.setTargetBasis(req.targetBasis());
    b.setFactorValue(req.factorValue());
    b.setSigFigs(req.sigFigs());
    b.setUncertaintyPct(req.uncertaintyPct());
    b.setEvidenceId(req.evidenceId());
    b.setConditionKey(req.conditionKey());
    b.setConditionValue(req.conditionValue());
    b.setParallelConflict(Boolean.TRUE.equals(req.parallelConflict()));
    b.setStatus(ConversionBaseStatus.DRAFT);
    b.setCreatedBy(username);
    return baseRepo.save(b);
  }

  /** 批准依据：同 物质/能源—工厂—期间—量纲 组合的旧批准依据被取代，同时生成有向换算边。 */
  @Transactional
  public ConversionBase approveBase(Long baseId, String username) {
    ConversionBase b = baseRepo.findById(baseId)
        .orElseThrow(() -> BizException.notFound("换算依据不存在: " + baseId));
    if (b.getStatus() != ConversionBaseStatus.DRAFT) {
      throw BizException.conflict("只有草稿状态的换算依据可以批准，当前: " + b.getStatus());
    }
    boolean replacedAny = false;
    for (ConversionBase old : baseRepo.findByEnergyTypeAndStatusOrderById(
        b.getEnergyType(), ConversionBaseStatus.APPROVED)) {
      // 并行冲突依据（两份相反热值/基准）同时保留，不互相取代
      if (b.isParallelConflict() || old.isParallelConflict()) continue;
      if (sameApplicability(old, b) && Objects.equals(old.getSourceDimension(), b.getSourceDimension())
          && Objects.equals(old.getTargetDimension(), b.getTargetDimension())) {
        old.setStatus(ConversionBaseStatus.SUPERSEDED);
        baseRepo.save(old);
        for (ConversionEdge e : edgeRepo.findByBaseId(old.getId())) {
          e.setStatus("RETIRED");
          edgeRepo.save(e);
        }
        b.setSupersedesId(old.getId());
        replacedAny = true;
      }
    }
    b.setStatus(ConversionBaseStatus.APPROVED);
    b.setApprovedBy(username);
    b.setApprovedAt(java.time.LocalDateTime.now());
    baseRepo.save(b);

    ConversionEdge edge = new ConversionEdge();
    edge.setBaseId(b.getId());
    edge.setFromDimension(b.getSourceDimension());
    edge.setToDimension(b.getTargetDimension());
    edge.setFromUnit(b.getSourceUnit());
    edge.setToUnit(b.getTargetUnit());
    edge.setFromBasis(b.getSourceBasis());
    edge.setToBasis(b.getTargetBasis());
    edge.setEdgeKind(deriveKind(b));
    edge.setConditionKey(b.getConditionKey());
    edge.setConditionValue(b.getConditionValue());
    edge.setFactorValue(b.getFactorValue());
    edge.setUncertaintyPct(b.getUncertaintyPct());
    edge.setEvidenceId(b.getEvidenceId());
    edge.setStatus("APPROVED");
    edgeRepo.save(edge);

    // 仅“修订”（取代旧依据）才触发影响评估；首次登记依据不属于修订，不制造噪音评估
    if (replacedAny) triggerConversionImpact(b);
    return b;
  }

  private EdgeKind deriveKind(ConversionBase b) {
    if ("unit".equals(b.getConditionKey())) {
      return EdgeKind.COMPOUND;
    }
    if (Objects.equals(b.getSourceDimension(), b.getTargetDimension())) {
      return b.getSourceBasis() != null && !b.getSourceBasis().equals(b.getTargetBasis())
          ? EdgeKind.BASIS_SWITCH : EdgeKind.UNIT_PREFIX;
    }
    return EdgeKind.PROPERTY;
  }

  private boolean sameApplicability(ConversionBase a, ConversionBase b) {
    return Objects.equals(a.getPlantId(), b.getPlantId())
        && a.getPeriodFrom().compareTo(b.getPeriodFrom()) <= 0
        && (a.getPeriodTo() == null || b.getPeriodFrom().compareTo(a.getPeriodTo()) <= 0);
  }

  private void triggerConversionImpact(ConversionBase b) {
    Set<String> periods = new TreeSet<>();
    for (SourceRow row : rowRepo.findAll()) {
      if (row.getStatus() != RowStatus.ACTIVE || !row.getEnergyType().equals(b.getEnergyType())) continue;
      if (b.getPlantId() != null && !b.getPlantId().equals(row.getPlantId())) continue;
      if (!periodApplies(b, row.getPeriod())) continue;
      // 仅对已完成核算（已生成换算证明）或已发布报告的期间评估，避免为历史空期间制造噪音
      boolean proven = !convRepo.findAll().stream()
          .filter(c -> c.getPeriod().equals(row.getPeriod())).toList().isEmpty();
      boolean hasPublished = !versionRepo.findPublishedByPeriod(row.getPeriod()).isEmpty();
      if (proven || hasPublished) periods.add(row.getPeriod());
    }
    for (String period : periods) {
      impactService.assess(period, TriggerType.CONVERSION_REVISION,
          "换算依据 " + b.getCode() + " 批准生效（" + b.getName() + "），期间 " + period + " 需重新换算");
    }
  }

  private boolean periodApplies(ConversionBase b, String period) {
    if (period.compareTo(b.getPeriodFrom()) < 0) return false;
    return b.getPeriodTo() == null || period.compareTo(b.getPeriodTo()) <= 0;
  }

  // ================= 核算运行：逐边求值 =================

  /** 为一次运行的每段活动量生成换算证明与冲突记录；幂等，重算前清理本运行旧证明。 */
  @Transactional
  public void proveConversions(Long runId, String period) {
    convRepo.findByRunIdOrderById(runId).forEach(convRepo::delete);
    conflictRepo.findByRunIdOrderByIdDesc(runId).forEach(conflictRepo::delete);
    metricRepo.findByRunIdOrderById(runId).forEach(metricRepo::delete);

    List<SourceRow> rows = rowRepo.findActiveVerifiedByPeriod(period, RowStatus.ACTIVE);
    List<ConversionEdge> edges = edgeRepo.findByStatusOrderById("APPROVED");
    Map<Long, Plant> plants = plantRepo.findAll().stream()
        .collect(java.util.stream.Collectors.toMap(Plant::getId, p -> p));

    for (SourceRow row : rows) {
      convRepo.save(evaluate(row, edges, plants, runId));
    }
  }

  /** 排放结果落库后，把换算证明回填到对应结果（按 工厂×产品线×能源类型 匹配）。 */
  @Transactional
  public void linkResults(Long runId) {
    List<EmissionResult> results = resultRepo.findByRunIdOrderById(runId);
    Map<String, Long> resultIndex = new HashMap<>();
    for (EmissionResult r : results) {
      resultIndex.put(r.getPlantId() + "|" + r.getProductLineId() + "|" + r.getEnergyType(), r.getId());
    }
    for (ActivityConversion c : convRepo.findByRunIdOrderById(runId)) {
      c.setResultId(resultIndex.get(c.getPlantId() + "|" + c.getProductLineId() + "|" + c.getEnergyType()));
      convRepo.save(c);
    }
  }

  /** 换算证明闭合、排放结果回填后生成派生指标（能量/排放/强度）。 */
  @Transactional
  public void buildDerivedMetrics(Long runId) {
    metricRepo.findByRunIdOrderById(runId).forEach(metricRepo::delete);
    buildDerivedMetrics(runId, resultRepo.findByRunIdOrderById(runId));
  }

  /** 求值结果：某一目标单位下的取值、区间、路径与冲突标记。 */
  private record ResolvedTarget(BigDecimal qty, BigDecimal lower, BigDecimal upper,
                                BigDecimal uncertaintyPct, List<Map<String, Object>> paths,
                                boolean conflict) {}

  private ActivityConversion evaluate(SourceRow row, List<ConversionEdge> edges,
                                      Map<Long, Plant> plants, Long runId) {
    ActivityConversion c = new ActivityConversion();
    c.setRunId(runId);
    c.setSourceRowId(row.getId());
    c.setEnergyType(row.getEnergyType());
    c.setPlantId(row.getPlantId());
    c.setProductLineId(row.getProductLineId());
    c.setPeriod(row.getPeriod());
    c.setSourceQty(row.getQuantity());
    c.setSourceUnit(row.getUnit());
    String sourceDim = MeasurementUnits.dimensionOf(row.getUnit());
    c.setSourceDimension(sourceDim);
    // 物理目标（能量/电量/质量）与排放因子规范单位目标分别求值
    String physicalDim = MeasurementUnits.targetDimensionOf(row.getEnergyType());
    String physicalUnit = MeasurementUnits.targetUnitOf(row.getEnergyType());
    String canonicalUnit = EnergyTypes.unitOf(row.getEnergyType());
    String canonicalDim = MeasurementUnits.dimensionOf(canonicalUnit);
    c.setTargetDimension(physicalDim);
    c.setTargetUnit(physicalUnit);
    c.setCanonicalUnit(canonicalUnit);

    // 1) 量纲家族不相容 → 直接判定，不做猜测性换算
    if (!familyCompatible(sourceDim, canonicalDim)) {
      c.setConvStatus(ConversionStatus.DIMENSION_INCOMPATIBLE);
      c.setTruncateReason("量纲家族不相容：" + sourceDim + " 无法物理换算到 " + canonicalDim
          + "（湿基/干基、标况/工况等基准不可仅凭单位名称互换）");
      c.setPathJson(writeJson(Map.of("steps", List.of(), "truncated", true)));
      return c;
    }

    // 2) 图上 DFS（带循环检测），枚举所有简单路径并逐边求值；起点基准优先取来源行显式声明
    String startBasis = row.getBasis() != null ? row.getBasis() : MeasurementUnits.basisOf(row.getUnit());
    SearchState start = new SearchState(sourceDim, startBasis, row.getUnit());
    List<Path> paths = new ArrayList<>();
    dfs(start, row, edges, new LinkedHashSet<>(), new ArrayList<>(), paths, 0);

    // 读数单位与排放因子规范单位一致：恒等换算（无需物性/基准边，零不确定度）
    ResolvedTarget canonical = row.getUnit().equals(canonicalUnit)
        ? identityTarget(row, canonicalUnit)
        : resolveTarget(row, paths, canonicalUnit);
    ResolvedTarget physical = canonicalUnit.equals(physicalUnit) ? canonical
        : resolveTarget(row, paths, physicalUnit);

    if (canonical != null) {
      c.setConvStatus(ConversionStatus.CONVERTIBLE);
      c.setCanonicalQty(canonical.qty());
      c.setCanonicalLower(canonical.lower());
      c.setCanonicalUpper(canonical.upper());
      if (physical != null) {
        c.setTargetQty(physical.qty());
        c.setLowerQty(physical.lower());
        c.setUpperQty(physical.upper());
        c.setUncertaintyPct(physical.uncertaintyPct());
      }
      boolean conflict = canonical.conflict() || (physical != null && physical.conflict());
      c.setConflictFlag(conflict);
      List<Map<String, Object>> allPaths = new ArrayList<>();
      allPaths.addAll(canonical.paths());
      if (physical != null && physical != canonical) allPaths.addAll(physical.paths());
      c.setPathJson(writeJson(Map.of(
          "canonicalUnit", canonicalUnit, "physicalUnit", physicalUnit,
          "paths", allPaths, "conflict", conflict)));
      if (conflict) {
        ResolvedTarget rt = physical != null && physical.conflict() ? physical : canonical;
        saveConflict(row, runId, rt, physical != null ? physicalUnit : canonicalUnit);
      }
      return c;
    }

    // 3) 无完整链：区分证据不足 / 人工桥接
    String reason = paths.stream().filter(p -> p.truncated).map(p -> p.truncateReason)
        .findFirst().orElseGet(() -> "缺少从 " + sourceDim + " 到 " + canonicalDim
            + " 的已批准换算边（新化验、边界变更或罕见单位须先登记依据并批准）");
    if (hasManualBridge(row)) {
      c.setConvStatus(ConversionStatus.MANUAL_BRIDGE);
      c.setTruncateReason("图上无可用换算链，存在人工桥接说明但未形成批准依据：" + reason);
    } else {
      c.setConvStatus(ConversionStatus.INSUFFICIENT_EVIDENCE);
      c.setTruncateReason(reason);
    }
    c.setPathJson(writeJson(Map.of(
        "steps", paths.stream().flatMap(p -> p.steps.stream()).toList(),
        "truncated", true, "reason", c.getTruncateReason())));
    return c;
  }

  /** 读数单位即因子规范单位：恒等链，系数 1、无额外不确定度。 */
  private ResolvedTarget identityTarget(SourceRow row, String unit) {
    Map<String, Object> step = new LinkedHashMap<>();
    step.put("edgeKind", "IDENTITY");
    step.put("fromUnit", unit);
    step.put("toUnit", unit);
    step.put("factor", "1");
    step.put("uncertaintyPct", "0");
    step.put("note", "读数单位与排放因子规范单位一致，无需物性/基准换算");
    Map<String, Object> m = new LinkedHashMap<>();
    m.put("targetUnit", unit);
    m.put("targetQty", row.getQuantity().setScale(6, RoundingMode.HALF_UP));
    m.put("uncertaintyPct", BigDecimal.ZERO.setScale(4, RoundingMode.HALF_UP));
    m.put("lowerQty", row.getQuantity().setScale(6, RoundingMode.HALF_UP));
    m.put("upperQty", row.getQuantity().setScale(6, RoundingMode.HALF_UP));
    m.put("steps", List.of(step));
    return new ResolvedTarget(
        (BigDecimal) m.get("targetQty"), (BigDecimal) m.get("lowerQty"),
        (BigDecimal) m.get("upperQty"), (BigDecimal) m.get("uncertaintyPct"),
        List.of(m), false);
  }

  /** 在 DFS 结果中筛选终止于指定目标单位的完整链，逐边复算并检测多链冲突。 */
  private ResolvedTarget resolveTarget(SourceRow row, List<Path> paths, String targetUnit) {
    String targetDim = MeasurementUnits.dimensionOf(targetUnit);
    String targetBasis = MeasurementUnits.basisOf(targetUnit);
    List<Path> complete = paths.stream()
        .filter(p -> !p.truncated && p.head.dimension.equals(targetDim)
            && p.head.unit.equals(targetUnit)
            && (p.head.basis == null || p.head.basis.equals(targetBasis)))
        .toList();
    if (complete.isEmpty()) return null;

    // 同因子+同不确定度的链为等价路径（星形拓扑下仅单位写法不同），保留最短一条
    Map<String, Map<String, Object>> dedup = new LinkedHashMap<>();
    for (Path p : complete) {
      BigDecimal qty = row.getQuantity().multiply(p.factor).setScale(6, RoundingMode.HALF_UP);
      BigDecimal u = sqrt(p.uncertaintySqSum);
      String key = qty.toPlainString() + "|" + u.toPlainString();
      Map<String, Object> existing = dedup.get(key);
      if (existing == null || ((List<?>) existing.get("steps")).size() > p.steps.size()) {
        Map<String, Object> m = new LinkedHashMap<>();
        m.put("targetUnit", targetUnit);
        m.put("targetQty", qty);
        m.put("uncertaintyPct", u.setScale(4, RoundingMode.HALF_UP));
        m.put("lowerQty", qty.multiply(BigDecimal.ONE.subtract(u.movePointLeft(2))).setScale(6, RoundingMode.HALF_UP));
        m.put("upperQty", qty.multiply(BigDecimal.ONE.add(u.movePointLeft(2))).setScale(6, RoundingMode.HALF_UP));
        m.put("steps", p.steps);
        dedup.put(key, m);
      }
    }
    List<Map<String, Object>> evaluated = new ArrayList<>(dedup.values());
    BigDecimal firstQty = (BigDecimal) evaluated.get(0).get("targetQty");
    boolean conflict = evaluated.size() > 1 && evaluated.stream().anyMatch(m -> {
      BigDecimal q = (BigDecimal) m.get("targetQty");
      if (q.compareTo(BigDecimal.ZERO) == 0) return false;
      return q.subtract(firstQty).abs()
          .divide(firstQty, 8, RoundingMode.HALF_UP).compareTo(CONFLICT_EPS) > 0;
    });
    Map<String, Object> primary = evaluated.get(0);
    return new ResolvedTarget(
        (BigDecimal) primary.get("targetQty"),
        (BigDecimal) primary.get("lowerQty"),
        (BigDecimal) primary.get("upperQty"),
        (BigDecimal) primary.get("uncertaintyPct"),
        evaluated, conflict);
  }

  private void saveConflict(SourceRow row, Long runId, ResolvedTarget rt, String targetUnit) {
    List<Map<String, Object>> ps = rt.paths();
    ConversionConflict cc = new ConversionConflict();
    cc.setSourceRowId(row.getId());
    cc.setRunId(runId);
    cc.setEnergyType(row.getEnergyType());
    cc.setPathAJson(writeJson(ps.get(0)));
    cc.setPathBJson(writeJson(ps.get(1)));
    cc.setQtyA((BigDecimal) ps.get(0).get("targetQty"));
    cc.setQtyB((BigDecimal) ps.get(1).get("targetQty"));
    cc.setTargetUnit(targetUnit);
    cc.setBasisDiff("两份相反热值/基准依据同时适用：链A=" + describeChain(ps.get(0))
        + "；链B=" + describeChain(ps.get(1)) + "。冲突保留，不以平均值消除");
    cc.setStatus(ConflictStatus.OPEN);
    conflictRepo.save(cc);
  }

  /** 家族判定：电只能↔电；质量/体积/能量同属燃料物理量家族可经物性边连通；复合单位须经 COMPOUND 边展开。 */
  private boolean familyCompatible(String sourceDim, String targetDim) {
    if (Objects.equals(sourceDim, targetDim)) return true;
    Set<String> fuelFamily = Set.of(MeasurementUnits.DIM_MASS, MeasurementUnits.DIM_VOLUME_STD,
        MeasurementUnits.DIM_VOLUME_OP, MeasurementUnits.DIM_ENERGY_LHV_DRY,
        MeasurementUnits.DIM_ENERGY_LHV_WET, MeasurementUnits.DIM_ENERGY_NCV);
    if (sourceDim.equals(MeasurementUnits.DIM_COMPOUND)) return true; // 尝试 COMPOUND 展开，展开失败再截断
    if (targetDim.equals(MeasurementUnits.DIM_ELECTRICITY)) {
      return sourceDim.equals(MeasurementUnits.DIM_ELECTRICITY);
    }
    return fuelFamily.contains(sourceDim) && fuelFamily.contains(targetDim);
  }

  private record SearchState(String dimension, String basis, String unit) {}

  private static final class Path {
    SearchState head;
    List<Map<String, Object>> steps = new ArrayList<>();
    BigDecimal factor = BigDecimal.ONE;
    BigDecimal uncertaintySqSum = BigDecimal.ZERO;
    boolean truncated;
    String truncateReason;
  }

  private void dfs(SearchState state, SourceRow row, List<ConversionEdge> edges,
                   Set<String> visited, List<Map<String, Object>> trail,
                   List<Path> out, int depth) {
    String key = state.dimension + "|" + state.basis + "|" + state.unit;
    if (visited.contains(key)) return; // 循环检测：同一量纲+基准+单位节点不重复进入
    visited.add(key);

    // 当前节点作为一条终止路径登记；调用方按目标节点筛选完整链
    Path terminal = new Path();
    terminal.head = state;
    terminal.steps = new ArrayList<>(trail);
    terminal.factor = stateFactor(trail);
    terminal.uncertaintySqSum = sumSq(trail);
    out.add(terminal);
    if (depth >= MAX_DEPTH) return;

    // 同量纲 SI 前缀虚拟边（t↔kg、m3↔L、kWh↔MWh、GJ↔MJ）：无需证据、零不确定度，基准保持不变
    for (String peer : MeasurementUnits.sameDimensionUnits(state.unit)) {
      BigDecimal prefix = unitAlignFactor(state.unit, peer);
      if (prefix == null || prefix.compareTo(BigDecimal.ONE) == 0) continue;
      Map<String, Object> vs = new LinkedHashMap<>();
      vs.put("edgeKind", "UNIT_PREFIX");
      vs.put("fromUnit", state.unit);
      vs.put("toUnit", peer);
      vs.put("fromDimension", state.dimension);
      vs.put("toDimension", state.dimension);
      vs.put("fromBasis", state.basis);
      vs.put("toBasis", state.basis);
      vs.put("unitAlignFactor", prefix.stripTrailingZeros().toPlainString());
      vs.put("factor", "1");
      vs.put("uncertaintyPct", "0");
      vs.put("virtual", true);
      List<Map<String, Object>> prefixTrail = new ArrayList<>(trail);
      prefixTrail.add(vs);
      dfs(new SearchState(state.dimension, state.basis, peer), row, edges,
          new LinkedHashSet<>(visited), prefixTrail, out, depth + 1);
    }

    for (ConversionEdge e : edges) {
      if (!edgeApplicable(e, state, row)) continue;
      // 同量纲单位对齐（t/kg、kWh/MWh、GJ/MJ、m3/L）：先做前缀换算再应用边
      BigDecimal prefix = unitAlignFactor(state.unit, e.getFromUnit());
      if (prefix == null) continue;
      if (e.getEvidenceId() == null) {
        // 中途缺证：截断并记录，不再沿该边继续
        Path t = new Path();
        t.head = state;
        t.steps = new ArrayList<>(trail);
        t.steps.add(stepMap(e, prefix, true, "该换算边缺少校准/化验证据，路径在此截断"));
        t.truncated = true;
        t.truncateReason = "边 " + e.getId() + "（" + e.getFromUnit() + "→" + e.getToUnit()
            + "）无证据支撑，中途截断";
        out.add(t);
        continue;
      }
      List<Map<String, Object>> nextTrail = new ArrayList<>(trail);
      nextTrail.add(stepMap(e, prefix, false, null));
      SearchState next = new SearchState(e.getToDimension(),
          e.getToBasis() != null ? e.getToBasis() : state.basis, e.getToUnit());
      dfs(next, row, edges, new LinkedHashSet<>(visited), nextTrail, out, depth + 1);
    }
  }

  private boolean edgeApplicable(ConversionEdge e, SearchState state, SourceRow row) {
    if (!"APPROVED".equals(e.getStatus())) return false;
    if (!e.getFromDimension().equals(state.dimension)) return false;
    // 基准守卫：湿基/干基、标况/工况、LHV/NCV 不同基准不得隐式穿越
    if (e.getFromBasis() != null && state.basis != null && !e.getFromBasis().equals(state.basis)) {
      return false;
    }
    ConversionBase b = baseRepo.findById(e.getBaseId()).orElse(null);
    if (b == null || b.getStatus() != ConversionBaseStatus.APPROVED) return false;
    if (!b.getEnergyType().equals(row.getEnergyType())) return false;
    if (b.getPlantId() != null && !b.getPlantId().equals(row.getPlantId())) return false;
    if (!periodApplies(b, row.getPeriod())) return false;
    if (e.getConditionKey() != null) {
      String expected = switch (e.getConditionKey()) {
        case "unit" -> row.getUnit();
        case "period" -> row.getPeriod();
        case "plant" -> String.valueOf(row.getPlantId());
        case "moisture_present" -> String.valueOf(moistureEvidencePresent(row));
        default -> null; // 未知条件键不自动匹配，安全跳过
      };
      if (expected == null || !expected.equals(e.getConditionValue())) return false;
    }
    return true;
  }

  /** 同量纲单位对齐系数：把 state.unit 的数值换算到 edgeFromUnit。 */
  private BigDecimal unitAlignFactor(String currentUnit, String edgeFromUnit) {
    if (currentUnit.equals(edgeFromUnit)) return BigDecimal.ONE;
    if (!MeasurementUnits.dimensionOf(currentUnit).equals(MeasurementUnits.dimensionOf(edgeFromUnit))) {
      return null;
    }
    BigDecimal a = MeasurementUnits.prefixFactor(currentUnit);
    BigDecimal b = MeasurementUnits.prefixFactor(edgeFromUnit);
    if (b.compareTo(BigDecimal.ZERO) == 0) return null;
    return a.divide(b, 10, RoundingMode.HALF_UP);
  }

  private Map<String, Object> stepMap(ConversionEdge e, BigDecimal prefix, boolean truncated, String reason) {
    Map<String, Object> m = new LinkedHashMap<>();
    m.put("edgeId", e.getId());
    m.put("baseId", e.getBaseId());
    m.put("edgeKind", e.getEdgeKind().name());
    m.put("fromUnit", e.getFromUnit());
    m.put("toUnit", e.getToUnit());
    m.put("fromDimension", e.getFromDimension());
    m.put("toDimension", e.getToDimension());
    m.put("fromBasis", e.getFromBasis());
    m.put("toBasis", e.getToBasis());
    m.put("unitAlignFactor", prefix.stripTrailingZeros().toPlainString());
    m.put("factor", e.getFactorValue().toPlainString());
    m.put("uncertaintyPct", e.getUncertaintyPct().toPlainString());
    m.put("evidenceId", e.getEvidenceId());
    m.put("condition", e.getConditionKey() == null ? null
        : e.getConditionKey() + "=" + e.getConditionValue());
    if (truncated) m.put("truncateReason", reason);
    if (e.getEvidenceId() != null) {
      evidenceRepo.findById(e.getEvidenceId()).ifPresent(ev -> {
        m.put("evidenceNo", ev.getEvidenceNo());
        m.put("evidenceType", ev.getEvidenceType().name());
        m.put("sigFigs", ev.getSigFigs());
      });
    }
    return m;
  }

  private String describeChain(Map<String, Object> path) {
    @SuppressWarnings("unchecked")
    List<Map<String, Object>> steps = (List<Map<String, Object>>) path.get("steps");
    return steps.stream().map(s -> s.get("fromUnit") + "→" + s.get("toUnit")
        + "(证据:" + s.getOrDefault("evidenceNo", "无") + ")")
        .reduce((a, b) -> a + "→" + b).orElse("空链");
  }

  private boolean moistureEvidencePresent(SourceRow row) {
    LocalDate at = YearMonth.parse(row.getPeriod()).atDay(1);
    return evidenceRepo.findAll().stream().anyMatch(ev ->
        "MOISTURE".equals(ev.getMeasuredProp())
            && (ev.getEnergyType() == null || ev.getEnergyType().equals(row.getEnergyType()))
            && (ev.getPlantId() == null || ev.getPlantId().equals(row.getPlantId()))
            && !ev.getEffectiveFrom().isAfter(at)
            && (ev.getEffectiveTo() == null || !ev.getEffectiveTo().isBefore(at)));
  }

  private boolean hasManualBridge(SourceRow row) {
    LocalDate at = YearMonth.parse(row.getPeriod()).atDay(1);
    return evidenceRepo.findAll().stream().anyMatch(ev ->
        ev.getEvidenceType() == EvidenceType.MANUAL_BRIDGE
            && (ev.getEnergyType() == null || ev.getEnergyType().equals(row.getEnergyType()))
            && (ev.getPlantId() == null || ev.getPlantId().equals(row.getPlantId()))
            && !ev.getEffectiveFrom().isAfter(at)
            && (ev.getEffectiveTo() == null || !ev.getEffectiveTo().isBefore(at)));
  }

  // ================= 派生指标 =================

  private void buildDerivedMetrics(Long runId, List<EmissionResult> results) {
    List<ActivityConversion> convs = convRepo.findByRunIdOrderById(runId);
    Map<Long, List<ActivityConversion>> byResult = new LinkedHashMap<>();
    for (ActivityConversion c : convs) {
      if (c.getResultId() != null) byResult.computeIfAbsent(c.getResultId(), k -> new ArrayList<>()).add(c);
    }
    for (EmissionResult r : results) {
      List<ActivityConversion> mine = byResult.getOrDefault(r.getId(), List.of());
      boolean allConvertible = !mine.isEmpty()
          && mine.stream().allMatch(c -> c.getConvStatus() == ConversionStatus.CONVERTIBLE);
      boolean anyConflict = mine.stream().anyMatch(ActivityConversion::isConflictFlag);

      // 能量（燃料）：逐行目标量求和，区间按行求和保持相关保守边界
      if (MeasurementUnits.targetDimensionOf(r.getEnergyType()).equals(MeasurementUnits.DIM_ENERGY_LHV_DRY)) {
        DerivedMetric energy = new DerivedMetric();
        energy.setRunId(runId);
        energy.setResultId(r.getId());
        energy.setPlantId(r.getPlantId());
        energy.setProductLineId(r.getProductLineId());
        energy.setScope(r.getScope());
        energy.setEnergyType(r.getEnergyType());
        energy.setMetricKind(MetricKind.ENERGY);
        energy.setMetricUnit(MeasurementUnits.metricUnit("ENERGY"));
        energy.setDisplayDecimals(MeasurementUnits.metricDecimals("ENERGY"));
        if (allConvertible) {
          BigDecimal v = sum(mine, ActivityConversion::getTargetQty);
          BigDecimal lo = sum(mine, ActivityConversion::getLowerQty);
          BigDecimal hi = sum(mine, ActivityConversion::getUpperQty);
          energy.setMetricValue(v);
          energy.setLowerValue(lo);
          energy.setUpperValue(hi);
          energy.setUncertaintyPct(relativeBand(v, lo, hi));
          energy.setMetricStatus(MetricStatus.OK);
          energy.setCalcPathJson(writeJson(Map.of("conversionIds",
              mine.stream().map(ActivityConversion::getId).toList(), "aggregation", "SUM")));
          if (anyConflict) energy.setNote("存在相反热值依据冲突，单元格追溯状态为 CONFLICT，数值取主链但保留双链范围");
        } else {
          energy.setMetricStatus(MetricStatus.PENDING_CONVERSION);
          energy.setNote("部分活动量换算未闭合：" + statusBreakdown(mine));
          energy.setCalcPathJson(writeJson(Map.of("conversionIds",
              mine.stream().map(ActivityConversion::getId).toList())));
        }
        metricRepo.save(energy);
      }

      // 排放量：沿用运行结果，附换算不确定边界
      DerivedMetric emission = new DerivedMetric();
      emission.setRunId(runId);
      emission.setResultId(r.getId());
      emission.setPlantId(r.getPlantId());
      emission.setProductLineId(r.getProductLineId());
      emission.setScope(r.getScope());
      emission.setEnergyType(r.getEnergyType());
      emission.setMetricKind(MetricKind.EMISSION);
      emission.setMetricUnit(MeasurementUnits.metricUnit("EMISSION"));
      emission.setDisplayDecimals(MeasurementUnits.metricDecimals("EMISSION"));
      if (r.getStatus() == ResultStatus.PENDING_FACTOR || r.getEmissionTco2e() == null) {
        emission.setMetricStatus(r.getStatus() == ResultStatus.PENDING_CONVERSION
            ? MetricStatus.PENDING_CONVERSION : MetricStatus.PENDING_FACTOR);
        emission.setNote(r.getStatus() == ResultStatus.PENDING_CONVERSION
            ? "测量换算未闭合，排放量待核：" + statusBreakdown(mine)
            : "缺少适用批准因子，排放量待核");
      } else {
        emission.setMetricValue(r.getEmissionTco2e());
        emission.setMetricStatus(MetricStatus.OK);
        if (allConvertible) {
          BigDecimal v = r.getEmissionTco2e();
          BigDecimal band = avgRelativeBand(mine);
          emission.setUncertaintyPct(band);
          emission.setLowerValue(applyPct(v, band, false));
          emission.setUpperValue(applyPct(v, band, true));
        }
        emission.setCalcPathJson(writeJson(Map.of(
            "factorVersionId", r.getFactorVersionId(), "factorValue", r.getFactorValue(),
            "formula", "activityQty * factorValue / 1000")));
      }
      metricRepo.save(emission);

      // 强度 = 排放量 / 能量；分母为空（电力、未闭合换算、零能量）→ EMPTY_DENOMINATOR
      DerivedMetric intensity = new DerivedMetric();
      intensity.setRunId(runId);
      intensity.setResultId(r.getId());
      intensity.setPlantId(r.getPlantId());
      intensity.setProductLineId(r.getProductLineId());
      intensity.setScope(r.getScope());
      intensity.setEnergyType(r.getEnergyType());
      intensity.setMetricKind(MetricKind.INTENSITY);
      intensity.setMetricUnit(MeasurementUnits.metricUnit("INTENSITY"));
      intensity.setDisplayDecimals(MeasurementUnits.metricDecimals("INTENSITY"));
      DerivedMetric energyMetric = metricRepo.findByResultId(r.getId()).stream()
          .filter(m -> m.getMetricKind() == MetricKind.ENERGY).findFirst().orElse(null);
      if (r.getEmissionTco2e() == null) {
        intensity.setMetricStatus(MetricStatus.PENDING_FACTOR);
        intensity.setNote("排放量待核，强度无法计算");
      } else if (energyMetric == null || energyMetric.getMetricValue() == null
          || energyMetric.getMetricValue().compareTo(BigDecimal.ZERO) == 0) {
        intensity.setMetricStatus(MetricStatus.EMPTY_DENOMINATOR);
        intensity.setNote("强度分母（能量 GJ）为空或为零：该能源无闭合能量换算链或非燃料能源");
      } else {
        BigDecimal v = r.getEmissionTco2e().divide(energyMetric.getMetricValue(), 6, RoundingMode.HALF_UP);
        intensity.setMetricValue(v);
        intensity.setMetricStatus(MetricStatus.OK);
        // 比值区间：下界=排放下界/能量上界，上界=排放上界/能量下界
        BigDecimal eLo = emission.getLowerValue() != null ? emission.getLowerValue() : r.getEmissionTco2e();
        BigDecimal eHi = emission.getUpperValue() != null ? emission.getUpperValue() : r.getEmissionTco2e();
        intensity.setLowerValue(eLo.divide(energyMetric.getUpperValue(), 6, RoundingMode.HALF_UP));
        intensity.setUpperValue(eHi.divide(energyMetric.getLowerValue(), 6, RoundingMode.HALF_UP));
        intensity.setUncertaintyPct(relativeBand(v, intensity.getLowerValue(), intensity.getUpperValue()));
        intensity.setCalcPathJson(writeJson(Map.of(
            "formula", "emission_tco2e / energy_GJ",
            "emissionMetric", r.getEmissionTco2e(),
            "energyMetric", energyMetric.getMetricValue())));
      }
      metricRepo.save(intensity);
    }
  }

  // ================= 披露单元格追溯 =================

  /** 报告版本冻结时，为能量/排放/强度单元格附可复算路径与范围。 */
  @Transactional
  public void traceVersion(ReportVersion version, List<ReportItem> items) {
    List<EmissionResult> results = resultRepo.findByRunIdOrderById(version.getRunId());
    Map<String, EmissionResult> resultIndex = new LinkedHashMap<>();
    for (EmissionResult r : results) {
      resultIndex.put(r.getPlantId() + "|" + r.getProductLineId() + "|" + r.getEnergyType(), r);
    }
    // 与任一上一版本（无论是否发布）对比：冻结后补证只产生带差异说明的新版本，原版本保留当时范围
    ReportVersion prior = versionRepo.findByReportIdOrderByVersionNoDesc(version.getReportId()).stream()
        .filter(x -> x.getVersionNo() < version.getVersionNo())
        .max(Comparator.comparingInt(ReportVersion::getVersionNo))
        .orElse(null);
    List<DisclosureCellTrace> priorTraces = prior == null ? List.of()
        : traceRepo.findByReportVersionIdOrderById(prior.getId());

    for (ReportItem item : items) {
      String key = item.getPlantId() + "|" + item.getProductLineId() + "|" + item.getEnergyType();
      EmissionResult r = resultIndex.get(key);
      List<ActivityConversion> convs = r == null ? List.of()
          : convRepo.findByRunIdOrderById(version.getRunId()).stream()
              .filter(c -> c.getResultId() != null && c.getResultId().equals(r.getId())).toList();
      List<DerivedMetric> metrics = r == null ? List.of()
          : metricRepo.findByResultId(r.getId());
      boolean conflict = convs.stream().anyMatch(ActivityConversion::isConflictFlag);
      String evidenceSummary = summarizeEvidence(convs);

      for (String cellType : List.of("ENERGY", "EMISSION", "INTENSITY")) {
        MetricKind kind = MetricKind.valueOf(cellType);
        DerivedMetric m = metrics.stream().filter(x -> x.getMetricKind() == kind).findFirst().orElse(null);
        DisclosureCellTrace t = new DisclosureCellTrace();
        t.setReportVersionId(version.getId());
        t.setReportItemId(item.getId());
        t.setCellType(cellType);
        t.setPlantId(item.getPlantId());
        t.setProductLineId(item.getProductLineId());
        t.setScope(item.getScope());
        t.setEnergyType(item.getEnergyType());
        t.setDisplayDecimals(MeasurementUnits.metricDecimals(cellType));
        t.setDisplayedUnit(MeasurementUnits.metricUnit(cellType));
        t.setEvidenceSummary(evidenceSummary);

        if (conflict) {
          t.setTraceStatus(TraceStatus.CONFLICT);
        }
        if (m == null || m.getMetricStatus() == MetricStatus.PENDING_CONVERSION
            || m.getMetricStatus() == MetricStatus.PENDING_FACTOR) {
          t.setTraceStatus(conflict ? TraceStatus.CONFLICT : TraceStatus.PENDING);
        } else if (m.getMetricStatus() == MetricStatus.EMPTY_DENOMINATOR) {
          if (!conflict) t.setTraceStatus(TraceStatus.EMPTY_DENOMINATOR);
        } else {
          if (!conflict) t.setTraceStatus(TraceStatus.TRACEABLE);
          t.setDisplayedValue(m.getMetricValue().setScale(t.getDisplayDecimals(), RoundingMode.HALF_UP));
          t.setLowerValue(m.getLowerValue() != null
              ? m.getLowerValue().setScale(t.getDisplayDecimals(), RoundingMode.HALF_UP) : null);
          t.setUpperValue(m.getUpperValue() != null
              ? m.getUpperValue().setScale(t.getDisplayDecimals(), RoundingMode.HALF_UP) : null);
        }
        if (conflict && m != null && m.getMetricValue() != null) {
          t.setDisplayedValue(m.getMetricValue().setScale(t.getDisplayDecimals(), RoundingMode.HALF_UP));
        }
        t.setTracePathJson(writeJson(buildCellPath(item, r, convs, m)));
        if (prior != null) {
          String diffNote = comparePrior(priorTraces, t, prior.getVersionNo(), version.getVersionNo());
          if (diffNote != null) t.setFrozenNote(diffNote);
        }
        traceRepo.save(t);
      }
    }
  }

  private Map<String, Object> buildCellPath(ReportItem item, EmissionResult r,
                                            List<ActivityConversion> convs, DerivedMetric m) {
    Map<String, Object> path = new LinkedHashMap<>();
    path.put("reportItemId", item.getId());
    path.put("sourceRowIds", convs.stream().map(ActivityConversion::getSourceRowId).toList());
    path.put("conversionIds", convs.stream().map(ActivityConversion::getId).toList());
    path.put("conversionStatuses", convs.stream().map(c -> c.getConvStatus().name()).toList());
    if (r != null) {
      path.put("factorVersionId", r.getFactorVersionId());
      path.put("factorValue", r.getFactorValue());
    }
    if (m != null) {
      path.put("metricKind", m.getMetricKind().name());
      path.put("metricStatus", m.getMetricStatus().name());
      path.put("calcPath", m.getCalcPathJson());
      path.put("uncertaintyPct", m.getUncertaintyPct());
    }
    return path;
  }

  private String comparePrior(List<DisclosureCellTrace> prior, DisclosureCellTrace now,
                              int oldVer, int newVer) {
    return prior.stream()
        .filter(p -> p.getCellType().equals(now.getCellType())
            && p.getPlantId().equals(now.getPlantId())
            && Objects.equals(p.getProductLineId(), now.getProductLineId())
            && p.getEnergyType().equals(now.getEnergyType()))
        .findFirst().map(p -> {
          // 仅当追溯状态或数值/范围确实变化时才写差异说明
          boolean statusChanged = p.getTraceStatus() != now.getTraceStatus();
          boolean valueChanged = !Objects.equals(p.getDisplayedValue(), now.getDisplayedValue())
              || !Objects.equals(p.getLowerValue(), now.getLowerValue())
              || !Objects.equals(p.getUpperValue(), now.getUpperValue());
          if (!statusChanged && !valueChanged) return null;
          String oldRange = p.getLowerValue() == null ? "待核/无范围"
              : "[" + p.getLowerValue().toPlainString() + ", " + p.getUpperValue().toPlainString() + "]";
          String newRange = now.getLowerValue() == null ? "待核/无范围"
              : "[" + now.getLowerValue().toPlainString() + ", " + now.getUpperValue().toPlainString() + "]";
          return "冻结后补证/依据变更：v" + oldVer + " 原披露范围 " + oldRange
              + " 保留不改；v" + newVer + " 新核算范围 " + newRange;
        }).orElse(null);
  }

  private String summarizeEvidence(List<ActivityConversion> convs) {
    Set<String> nos = new TreeSet<>();
    for (ActivityConversion c : convs) {
      if (c.getPathJson() == null) continue;
      try {
        Map<?, ?> doc = json.readValue(c.getPathJson(), Map.class);
        collectEvidenceNos(doc, nos);
      } catch (Exception ignored) { }
    }
    return nos.isEmpty() ? "无已登记证据" : "引用证据: " + String.join(", ", nos);
  }

  @SuppressWarnings("unchecked")
  private void collectEvidenceNos(Object node, Set<String> nos) {
    if (node instanceof Map<?, ?> map) {
      Object no = map.get("evidenceNo");
      if (no != null) nos.add(no.toString());
      map.values().forEach(v -> collectEvidenceNos(v, nos));
    } else if (node instanceof List<?> list) {
      list.forEach(v -> collectEvidenceNos(v, nos));
    }
  }

  // ================= 查询 =================

  public List<MeasurementEvidence> listEvidence() { return evidenceRepo.findAllByOrderByIdDesc(); }

  public List<Map<String, Object>> listBases() {
    List<Map<String, Object>> out = new ArrayList<>();
    for (ConversionBase b : baseRepo.findAllByOrderByIdDesc()) {
      Map<String, Object> m = new LinkedHashMap<>();
      m.put("base", b);
      m.put("edges", edgeRepo.findByBaseId(b.getId()));
      out.add(m);
    }
    return out;
  }

  public List<ActivityConversion> conversionsOfRun(Long runId) {
    return convRepo.findByRunIdOrderById(runId);
  }

  public List<ConversionConflict> conflictsOfRun(Long runId) {
    return conflictRepo.findByRunIdOrderByIdDesc(runId);
  }

  public List<ConversionConflict> allConflicts() { return conflictRepo.findAllByOrderByIdDesc(); }

  public List<DerivedMetric> metricsOfRun(Long runId) { return metricRepo.findByRunIdOrderById(runId); }

  public List<DisclosureCellTrace> tracesOfVersion(Long versionId) {
    return traceRepo.findByReportVersionIdOrderById(versionId);
  }

  public Map<String, Object> conversionDetail(Long id) {
    ActivityConversion c = convRepo.findById(id)
        .orElseThrow(() -> BizException.notFound("换算证明不存在: " + id));
    Map<String, Object> out = new LinkedHashMap<>();
    out.put("conversion", c);
    out.put("conflicts", conflictRepo.findBySourceRowIdAndStatus(c.getSourceRowId(), ConflictStatus.OPEN));
    SourceRow row = rowRepo.findById(c.getSourceRowId()).orElse(null);
    out.put("sourceRow", row);
    return out;
  }

  /** 运行换算状态汇总（四类计数 + 冲突数）。 */
  public Map<String, Object> runSummary(Long runId) {
    List<ActivityConversion> all = convRepo.findByRunIdOrderById(runId);
    Map<String, Long> counts = new LinkedHashMap<>();
    for (ConversionStatus s : ConversionStatus.values()) {
      counts.put(s.name(), all.stream().filter(c -> c.getConvStatus() == s).count());
    }
    Map<String, Object> out = new LinkedHashMap<>(counts);
    out.put("conflictCount", all.stream().filter(ActivityConversion::isConflictFlag).count());
    out.put("total", all.size());
    return out;
  }

  // ================= 工具 =================

  private BigDecimal stateFactor(List<Map<String, Object>> trail) {
    BigDecimal f = BigDecimal.ONE;
    for (Map<String, Object> s : trail) {
      f = f.multiply(new BigDecimal((String) s.get("unitAlignFactor")))
          .multiply(new BigDecimal((String) s.get("factor")));
    }
    return f;
  }

  private BigDecimal sumSq(List<Map<String, Object>> trail) {
    BigDecimal sum = BigDecimal.ZERO;
    for (Map<String, Object> s : trail) {
      sum = sum.add(sq(new BigDecimal((String) s.get("uncertaintyPct"))));
    }
    return sum;
  }

  private BigDecimal sq(BigDecimal v) { return v.multiply(v); }
  private BigDecimal sqrt(BigDecimal v) {
    return BigDecimal.valueOf(Math.sqrt(v.doubleValue())).setScale(6, RoundingMode.HALF_UP);
  }

  private BigDecimal applyPct(BigDecimal v, BigDecimal pct, boolean upper) {
    BigDecimal delta = v.multiply(pct).movePointLeft(2);
    return upper ? v.add(delta).setScale(6, RoundingMode.HALF_UP)
        : v.subtract(delta).setScale(6, RoundingMode.HALF_UP);
  }

  private BigDecimal relativeBand(BigDecimal mid, BigDecimal lo, BigDecimal hi) {
    if (mid == null || mid.compareTo(BigDecimal.ZERO) == 0) return null;
    BigDecimal d = hi.subtract(mid).max(mid.subtract(lo));
    return d.divide(mid, 8, RoundingMode.HALF_UP).movePointRight(2).setScale(4, RoundingMode.HALF_UP);
  }

  private BigDecimal avgRelativeBand(List<ActivityConversion> mine) {
    List<BigDecimal> us = mine.stream()
        .filter(c -> c.getConvStatus() == ConversionStatus.CONVERTIBLE && c.getUncertaintyPct() != null)
        .map(ActivityConversion::getUncertaintyPct).toList();
    if (us.isEmpty()) return BigDecimal.ZERO;
    BigDecimal sqSum = us.stream().map(this::sq).reduce(BigDecimal.ZERO, BigDecimal::add);
    return sqrt(sqSum);
  }

  private BigDecimal sum(List<ActivityConversion> list, java.util.function.Function<ActivityConversion, BigDecimal> getter) {
    return list.stream().map(getter).filter(Objects::nonNull).reduce(BigDecimal.ZERO, BigDecimal::add);
  }

  private String statusBreakdown(List<ActivityConversion> mine) {
    Map<ConversionStatus, Long> counts = new LinkedHashMap<>();
    for (ActivityConversion c : mine) {
      counts.merge(c.getConvStatus(), 1L, Long::sum);
    }
    return counts.entrySet().stream().map(e -> e.getKey() + "×" + e.getValue())
        .reduce((a, b) -> a + ", " + b).orElse("无活动行");
  }

  private String writeJson(Object o) {
    try { return json.writeValueAsString(o); }
    catch (Exception e) { throw BizException.badRequest("换算路径序列化失败: " + e.getMessage()); }
  }

  private LocalDate parseDate(String s, String label) {
    try { return LocalDate.parse(s); }
    catch (Exception e) { throw BizException.badRequest(label + "格式必须为 YYYY-MM-DD: " + s); }
  }
}
