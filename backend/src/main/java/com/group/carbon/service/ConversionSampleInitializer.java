package com.group.carbon.service;

import com.group.carbon.dto.Requests.CreateConversionBaseReq;
import com.group.carbon.dto.Requests.CreateEvidenceReq;
import com.group.carbon.dto.Requests.CreateFactorReq;
import com.group.carbon.dto.Requests.CreateBatchReq;
import com.group.carbon.dto.Requests.RowInput;
import com.group.carbon.model.*;
import com.group.carbon.repo.*;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.util.List;

/**
 * 测量换算样例（特性 5，2025-07 期间）：
 * R1 标况/工况天然气（可直接转换）；R2 工况燃料油仅有人工桥接说明（需人工桥接）；
 * R3 成都煤炭无适用换算依据（证据不足）；R4 两份相反热值依据（冲突保留，不取平均）+ 湿度缺失截断；
 * R5 跨期密度修订（2025-07 适用新密度）；R6 复合/非规范单位采购（kg→t）；
 * R7 电力强度分母为空；R8 电量误按质量单位登记（量纲不相容）；R9 万Nm3 复合单位展开。
 */
@Component
@Order(20)
public class ConversionSampleInitializer implements ApplicationRunner {
  private static final Logger log = LoggerFactory.getLogger(ConversionSampleInitializer.class);

  private final MeasurementEvidenceRepo evidenceRepo;
  private final PlantRepo plantRepo;
  private final ProductLineRepo lineRepo;
  private final ConversionService conversionService;
  private final FactorService factorService;
  private final BatchService batchService;
  private final RunService runService;
  private final DisclosureService disclosureService;

  public ConversionSampleInitializer(MeasurementEvidenceRepo evidenceRepo, PlantRepo plantRepo,
                                     ProductLineRepo lineRepo, ConversionService conversionService,
                                     FactorService factorService, BatchService batchService,
                                     RunService runService, DisclosureService disclosureService) {
    this.evidenceRepo = evidenceRepo;
    this.plantRepo = plantRepo;
    this.lineRepo = lineRepo;
    this.conversionService = conversionService;
    this.factorService = factorService;
    this.batchService = batchService;
    this.runService = runService;
    this.disclosureService = disclosureService;
  }

  @Override
  @Transactional
  public void run(ApplicationArguments args) {
    if (evidenceRepo.count() > 0) {
      log.info("换算样例已存在，跳过初始化");
      return;
    }
    log.info("开始初始化测量换算样例...");
    Plant sz = plantRepo.findById(1L).orElseThrow();
    Plant cd = plantRepo.findById(2L).orElseThrow();
    ProductLine l1 = lineRepo.findById(1L).orElseThrow();
    ProductLine l2 = lineRepo.findById(2L).orElseThrow();

    // ---------- 校准/化验证据 ----------
    Long evNgLhv = evidence("EV-LAB-NG-SZ", "LAB_ASSAY", "苏州天然气化验报告（干基低位热值 38.93 MJ/Nm3）",
        "LAB-2025-NG-07", "集团中心化验室", "2025-07-03", "2025-01-01", null,
        sz.getId(), "NATURAL_GAS", "LHV_DRY", "38.93", "MJ/Nm3", 4, "1.50");
    Long evNgTemp = evidence("EV-CAL-NG-TEMP", "CALIBRATION", "苏州天然气温度体积修正校准证书（工况→标况 1.062）",
        "CAL-2025-T-118", "计量院校准中心", "2025-06-25", "2025-07-01", null,
        sz.getId(), "NATURAL_GAS", "VOLUME_CORRECTION", "1.062", "ratio", 4, "0.70");
    Long evDensOld = evidence("EV-DENS-DIESEL-OLD", "DENSITY_REVISION", "柴油密度单（旧）0.8350 t/m3，适用至 2025-06",
        "DR-2024-031", "集团中心化验室", "2025-01-10", "2025-01-01", "2025-06-30",
        sz.getId(), "DIESEL", "DENSITY_STD", "0.8350", "t/m3", 4, "1.00");
    Long evDensNew = evidence("EV-DENS-DIESEL-NEW", "DENSITY_REVISION", "柴油密度修订单（新）0.8420 t/m3，2025-07 起适用",
        "DR-2025-072", "集团中心化验室", "2025-07-02", "2025-07-01", null,
        sz.getId(), "DIESEL", "DENSITY_STD", "0.8420", "t/m3", 4, "0.80");
    Long evDieselTemp = evidence("EV-CAL-DIESEL-TEMP", "CALIBRATION", "柴油工况体积修正校准（1.055）",
        "CAL-2025-T-121", "计量院校准中心", "2025-06-28", "2025-07-01", null,
        sz.getId(), "DIESEL", "VOLUME_CORRECTION", "1.055", "ratio", 4, "0.50");
    Long evDieselLhv = evidence("EV-LAB-DIESEL", "LAB_ASSAY", "柴油低位热值化验 42.65 GJ/t",
        "LAB-2025-D-09", "集团中心化验室", "2025-07-03", "2025-01-01", null,
        sz.getId(), "DIESEL", "LHV_DRY", "42.65", "GJ/t", 4, "1.00");
    Long evCoalA = evidence("EV-LAB-COAL-A", "LAB_ASSAY", "煤炭低位热值化验 A 所：20.5 GJ/t（干基）",
        "LAB-2025-C-A1", "第三方化验所A", "2025-07-05", "2025-07-01", null,
        sz.getId(), "COAL", "LHV_DRY", "20.5", "GJ/t", 3, "2.00");
    Long evCoalB = evidence("EV-LAB-COAL-B", "LAB_ASSAY", "煤炭低位热值化验 B 所：24.1 GJ/t（干基，相反依据）",
        "LAB-2025-C-B7", "第三方化验所B", "2025-07-06", "2025-07-01", null,
        sz.getId(), "COAL", "LHV_DRY", "24.1", "GJ/t", 3, "2.20");
    evidence("EV-BRIDGE-FUELOIL", "MANUAL_BRIDGE", "燃料油工况→标况人工桥接说明（尚未形成批准换算依据）",
        "MB-2025-02", "苏州工厂计量岗", "2025-07-08", "2025-07-01", null,
        sz.getId(), "FUEL_OIL", "MANUAL_BRIDGE", "1", "note", 1, "5.00");

    // ---------- 换算依据（批准后生成有向边） ----------
    // 天然气：工况 m3 → 标况 Nm3 → 干基低位热值 GJ；另含标况→工况反向边支持复合单位回算
    baseAndApprove("CB-NG-OP-STD", "天然气工况体积→标况体积", "NATURAL_GAS", sz.getId(),
        "2025-01", null, "m3", "OP", "Nm3", "STD", "PROPERTY",
        "1.062", "0.70", evNgTemp, null, null, false);
    baseAndApprove("CB-NG-STD-OP", "天然气标况体积→工况体积（反向桥）", "NATURAL_GAS", sz.getId(),
        "2025-01", null, "Nm3", "STD", "m3", "OP", "PROPERTY",
        "0.941619", "0.70", evNgTemp, null, null, false);
    baseAndApprove("CB-NG-STD-GJ", "天然气标况体积→干基低位热值能量", "NATURAL_GAS", sz.getId(),
        "2025-01", null, "Nm3", "STD", "GJ", "LHV", "PROPERTY",
        "0.03893", "1.50", evNgLhv, null, null, false);
    // 复合单位：万Nm3 → Nm3
    baseAndApprove("CB-NG-WAN-EXPAND", "复合单位展开：万Nm3→Nm3", "NATURAL_GAS", sz.getId(),
        "2025-01", null, "万Nm3", null, "Nm3", "STD", "COMPOUND",
        "10000", "0.00", evNgTemp, "unit", "万Nm3", false);

    // 柴油：工况 L → 标况 LN → 质量 t（跨期密度修订）→ 能量 GJ
    baseAndApprove("CB-DIESEL-OP-STD", "柴油工况升→标况升", "DIESEL", sz.getId(),
        "2025-01", null, "L", "OP", "LN", "STD", "PROPERTY",
        "1.055", "0.50", evDieselTemp, null, null, false);
    baseAndApprove("CB-DIESEL-DENS-OLD", "柴油标况升→质量吨（旧密度 0.8350，适用至 2025-06）", "DIESEL", sz.getId(),
        "2025-01", "2025-06", "LN", "STD", "t", null, "PROPERTY",
        "0.000835", "1.00", evDensOld, null, null, false);
    baseAndApprove("CB-DIESEL-DENS-NEW", "柴油标况升→质量吨（新密度 0.8420，2025-07 起）", "DIESEL", sz.getId(),
        "2025-07", null, "LN", "STD", "t", null, "PROPERTY",
        "0.000842", "0.80", evDensNew, null, null, false);
    baseAndApprove("CB-DIESEL-T-GJ", "柴油质量→干基低位热值能量", "DIESEL", sz.getId(),
        "2025-01", null, "t", null, "GJ", "LHV", "PROPERTY",
        "42.65", "1.00", evDieselLhv, null, null, false);

    // 煤炭：湿基→干基边缺少化验证据（湿度缺失，路径中途截断）；两条相反干基热值依据并行保留，
    // 含水率化验补证前两条热值链均不可达（INSUFFICIENT_EVIDENCE），补证后可达并产生冲突
    baseAndApprove("CB-COAL-WET-DRY-NOEVID", "煤炭湿基吨→干基吨（含水率化验缺失，证据不足）", "COAL", sz.getId(),
        "2025-07", null, "t", "WET", "t", "DRY", "BASIS_SWITCH",
        "0.94", "3.00", null, null, null, false);
    baseAndApprove("CB-COAL-LHV-A", "煤炭干基质量→能量（A 所热值 20.5 GJ/t）", "COAL", sz.getId(),
        "2025-07", null, "t", "DRY", "GJ", "LHV", "PROPERTY",
        "20.5", "2.00", evCoalA, null, null, true);
    baseAndApprove("CB-COAL-LHV-B", "煤炭干基质量→能量（B 所热值 24.1 GJ/t，相反依据并行）", "COAL", sz.getId(),
        "2025-07", null, "t", "DRY", "GJ", "LHV", "PROPERTY",
        "24.1", "2.20", evCoalB, null, null, true);

    // ---------- 新增因子（煤炭/铝材），保证闭合换算能算出排放 ----------
    Factor coalFactor = factorService.createFactor("admin",
        new CreateFactorReq("COAL-CNEAST", "华东煤炭排放因子", "COAL", "CN-EAST"));
    factorService.approve(factorService.createVersion(coalFactor.getId(), "analyst",
        new com.group.carbon.dto.Requests.CreateVersionReq(new BigDecimal("95.0"), "2025-01-01", "2025-12-31")).getId(),
        "auditor");
    Factor aluFactor = factorService.createFactor("admin",
        new CreateFactorReq("ALU-CNWEST", "西南采购铝材排放因子", "PURCH_ALU", "CN-WEST"));
    factorService.approve(factorService.createVersion(aluFactor.getId(), "analyst",
        new com.group.carbon.dto.Requests.CreateVersionReq(new BigDecimal("1600.0"), "2025-01-01", "2025-12-31")).getId(),
        "auditor");

    // ---------- 2025-07 批次 ----------
    verify(batchService.create("analyst", new CreateBatchReq(sz.getId(), "FUEL", "2025-07", "B-2025-07-FUEL-SZ", List.of(
        rawRow(sz, l1, "NATURAL_GAS", "50000", "m3", "OP"),       // R1 工况→标况→能量
        rawRow(sz, l1, "DIESEL", "900", "L", "OP"),               // R5 跨期密度修订链
        rawRow(sz, l1, "COAL", "120", "t", "WET"),                // R4 相反热值冲突 + 湿度截断
        rawRow(sz, l2, "FUEL_OIL", "6000", "LN", "STD"),            // R2 标况升仅人工桥接，无量纲连通边
        rawRow(sz, l2, "NATURAL_GAS", "5.2", "万Nm3", "STD")      // R9 复合单位展开
    ))));
    verify(batchService.create("analyst", new CreateBatchReq(cd.getId(), "FUEL", "2025-07", "B-2025-07-FUEL-CD", List.of(
        rawRow(cd, l1, "COAL", "80", "t", "WET")                  // R3 无适用依据→证据不足
    ))));
    verify(batchService.create("analyst", new CreateBatchReq(cd.getId(), "PROCUREMENT", "2025-07", "B-2025-07-PROC-CD", List.of(
        new RowInput(cd.getId(), l2.getId(), "PURCH_ALU", new BigDecimal("450"), "V-7001", "kg", null) // R6
    ))));
    verify(batchService.create("analyst", new CreateBatchReq(sz.getId(), "ELECTRICITY", "2025-07", "B-2025-07-ELEC-SZ", List.of(
        rawRow(sz, l1, "GRID_ELEC", "100000", "kWh", null),       // R7 强度分母为空
        rawRow(sz, l2, "GRID_ELEC", "30", "t", null)              // R8 量纲不相容
    ))));

    // ---------- 运行 → 冻结报告（含待核项，生成单元格追溯） ----------
    CalcRun run = runService.createOfficialRun("2025-07", "analyst");
    runService.execute(run.getId());
    disclosureService.createReport("analyst", "2025年7月测量换算验收报告", "2025-07", run.getId());
    log.info("测量换算样例初始化完成：运行 {}", run.getRunNo());
  }

  private Long evidence(String no, String type, String title, String ref, String issuer,
                        String issued, String from, String to, Long plantId,
                        String energyType, String prop, String value, String unit,
                        Integer sigFigs, String uncertainty) {
    MeasurementEvidence ev = conversionService.createEvidence("analyst", new CreateEvidenceReq(
        no, type, title, ref, issuer, issued, from, to, plantId, energyType, prop,
        new BigDecimal(value), unit, sigFigs, new BigDecimal(uncertainty), null));
    return ev.getId();
  }

  private void baseAndApprove(String code, String name, String energyType, Long plantId,
                              String periodFrom, String periodTo,
                              String sourceUnit, String sourceBasis, String targetUnit, String targetBasis,
                              String edgeKind, String factor, String uncertainty,
                              Long evidenceId, String condKey, String condValue, boolean parallelConflict) {
    var b = conversionService.createBase("analyst", new CreateConversionBaseReq(
        code, name, energyType, plantId, periodFrom, periodTo, sourceUnit, sourceBasis,
        targetUnit, targetBasis, edgeKind, new BigDecimal(factor), 4,
        new BigDecimal(uncertainty), evidenceId, condKey, condValue, parallelConflict));
    conversionService.approveBase(b.getId(), "auditor");
  }

  private void verify(SourceBatch batch) {
    batchService.verify(batch.getId(), "auditor");
  }

  private RowInput rawRow(Plant plant, ProductLine line, String energyType, String quantity,
                          String unit, String basis) {
    return new RowInput(plant.getId(), line != null ? line.getId() : null, energyType,
        new BigDecimal(quantity), null, unit, basis);
  }
}
