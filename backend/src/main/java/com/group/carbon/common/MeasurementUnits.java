package com.group.carbon.common;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * 计量量纲与单位登记。
 * 湿基/干基、标况/工况、净热值/低位热值不可以仅凭单位名称互换：
 * 单位字符串只负责展示，量纲（dimension）+ 计量基准（basis）才决定换算边能否相连。
 */
public final class MeasurementUnits {
  private MeasurementUnits() {}

  /** 规范量纲：同一量纲之间才能走 UNIT_PREFIX 边；跨量纲必须走带证据的物性/基准边。 */
  public static final String DIM_MASS = "mass";                 // 质量（t/kg/g）
  public static final String DIM_VOLUME_STD = "volume_std";     // 标况体积（Nm3、标况 L）
  public static final String DIM_VOLUME_OP = "volume_op";       // 工况体积（m3 工况、L 工况）
  public static final String DIM_ENERGY_LHV_DRY = "energy_lhv_dry"; // 干基低位热值能量
  public static final String DIM_ENERGY_LHV_WET = "energy_lhv_wet"; // 湿基低位热值能量
  public static final String DIM_ENERGY_NCV = "energy_ncv";     // 净热值能量
  public static final String DIM_ELECTRICITY = "electricity";   // 电量（kWh/MWh）
  public static final String DIM_COMPOUND = "compound";         // 复合单位未展开前

  /** 计量基准。 */
  public static final String BASIS_WET = "WET";   // 湿基
  public static final String BASIS_DRY = "DRY";   // 干基
  public static final String BASIS_STD = "STD";   // 标况
  public static final String BASIS_OP = "OP";     // 工况
  public static final String BASIS_LHV = "LHV";   // 低位热值
  public static final String BASIS_NCV = "NCV";   // 净热值

  /** 单位 → [量纲, 基准]。未登记单位按复合/未知量纲处理，触发量纲不相容而非猜测。 */
  private static final Map<String, String[]> UNIT_REGISTRY = new LinkedHashMap<>();
  /** 同量纲前缀换算：把单位数值换算到该量纲基准展示单位的系数。 */
  private static final Map<String, BigDecimal> PREFIX_FACTOR = new LinkedHashMap<>();
  static {
    UNIT_REGISTRY.put("t",    new String[]{DIM_MASS, null});
    UNIT_REGISTRY.put("kg",   new String[]{DIM_MASS, null});
    UNIT_REGISTRY.put("g",    new String[]{DIM_MASS, null});
    UNIT_REGISTRY.put("Nm3",  new String[]{DIM_VOLUME_STD, BASIS_STD});
    UNIT_REGISTRY.put("m3",   new String[]{DIM_VOLUME_OP, BASIS_OP});
    UNIT_REGISTRY.put("L",    new String[]{DIM_VOLUME_OP, BASIS_OP});
    UNIT_REGISTRY.put("LN",   new String[]{DIM_VOLUME_STD, BASIS_STD}); // 标况升
    UNIT_REGISTRY.put("GJ",   new String[]{DIM_ENERGY_LHV_DRY, BASIS_LHV});
    UNIT_REGISTRY.put("MJ",   new String[]{DIM_ENERGY_LHV_DRY, BASIS_LHV});
    UNIT_REGISTRY.put("kWh",  new String[]{DIM_ELECTRICITY, null});
    UNIT_REGISTRY.put("MWh",  new String[]{DIM_ELECTRICITY, null});

    PREFIX_FACTOR.put("kg", new BigDecimal("0.001"));    // kg → t
    PREFIX_FACTOR.put("g", new BigDecimal("0.000001"));  // g → t
    PREFIX_FACTOR.put("MWh", new BigDecimal("1000"));    // MWh → kWh
    PREFIX_FACTOR.put("MJ", new BigDecimal("0.001"));    // MJ → GJ
    PREFIX_FACTOR.put("L", new BigDecimal("0.001"));     // L → m3
    PREFIX_FACTOR.put("LN", new BigDecimal("0.001"));    // 标况升 → Nm3
  }

  public static boolean isRegistered(String unit) { return UNIT_REGISTRY.containsKey(unit); }

  public static String dimensionOf(String unit) {
    String[] reg = UNIT_REGISTRY.get(unit);
    return reg == null ? DIM_COMPOUND : reg[0];
  }

  public static String basisOf(String unit) {
    String[] reg = UNIT_REGISTRY.get(unit);
    return reg == null ? null : reg[1];
  }

  /** 同量纲单位前缀系数：返回把 unit 数值换算到基准单位的系数（无登记即 1）。 */
  public static BigDecimal prefixFactor(String unit) {
    return PREFIX_FACTOR.getOrDefault(unit, BigDecimal.ONE);
  }

  /** 同量纲下可做 SI 前缀虚拟换算的单位；采用星形拓扑：仅保留与该量纲基准单位相邻的边，避免长尾路径。 */
  public static List<String> sameDimensionUnits(String unit) {
    String dim = dimensionOf(unit);
    String base = baseUnitOf(dim);
    List<String> out = new ArrayList<>();
    UNIT_REGISTRY.forEach((u, reg) -> {
      if (!reg[0].equals(dim) || u.equals(unit)) return;
      // 只生成 任意单位↔基准单位 的边（u 是基准，或当前 unit 是基准时可到同量纲其他单位）
      if (u.equals(base) || unit.equals(base)) out.add(u);
    });
    return out;
  }

  /** 每个量纲的基准展示单位（星形拓扑中心）。 */
  public static String baseUnitOf(String dim) {
    return switch (dim) {
      case DIM_MASS -> "t";
      case DIM_VOLUME_OP -> "m3";
      case DIM_VOLUME_STD -> "Nm3";
      case DIM_ENERGY_LHV_DRY, DIM_ENERGY_LHV_WET, DIM_ENERGY_NCV -> "GJ";
      case DIM_ELECTRICITY -> "kWh";
      default -> null;
    };
  }

  /** 某能源类型的规范目标量纲：燃料统一折算到干基低位热值能量 GJ，电力到 kWh，采购质量到 t。 */
  public static String targetDimensionOf(String energyType) {
    return switch (energyType) {
      case "NATURAL_GAS", "DIESEL", "COAL", "FUEL_OIL" -> DIM_ENERGY_LHV_DRY;
      case "GRID_ELEC" -> DIM_ELECTRICITY;
      default -> DIM_MASS; // PURCH_* 采购物料
    };
  }

  public static String targetUnitOf(String energyType) {
    return switch (energyType) {
      case "NATURAL_GAS", "DIESEL", "COAL", "FUEL_OIL" -> "GJ";
      case "GRID_ELEC" -> "kWh";
      default -> "t";
    };
  }

  /** 派生指标展示单位与小数位：披露表各列口径不同，集中登记防止页面各自取整。 */
  public static String metricUnit(String kind) {
    return switch (kind) {
      case "ENERGY" -> "GJ";
      case "EMISSION" -> "tCO2e";
      default -> "tCO2e/GJ"; // INTENSITY
    };
  }

  public static int metricDecimals(String kind) {
    return switch (kind) {
      case "ENERGY" -> 2;
      case "EMISSION" -> 3;
      default -> 4; // INTENSITY 四位小数
    };
  }
}
