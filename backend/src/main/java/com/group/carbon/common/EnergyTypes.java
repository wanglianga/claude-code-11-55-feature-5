package com.group.carbon.common;

import java.util.LinkedHashMap;
import java.util.Map;

/** 能源/活动类型元数据：决定范围归属与计量单位。 */
public final class EnergyTypes {
  private EnergyTypes() {}

  public record Meta(String scope, String unit, String label, String category) {}

  public static final Map<String, Meta> MAP = new LinkedHashMap<>();
  static {
    MAP.put("NATURAL_GAS", new Meta("SCOPE1", "m3",  "天然气",   "FUEL"));
    MAP.put("DIESEL",      new Meta("SCOPE1", "L",   "柴油",     "FUEL"));
    MAP.put("COAL",        new Meta("SCOPE1", "GJ",  "煤炭",     "FUEL"));
    MAP.put("FUEL_OIL",    new Meta("SCOPE1", "L",   "燃料油",   "FUEL"));
    MAP.put("GRID_ELEC",   new Meta("SCOPE2", "kWh", "外购电力", "ELECTRICITY"));
    MAP.put("PURCH_STEEL", new Meta("SCOPE3", "t",   "采购钢材", "PROCUREMENT"));
    MAP.put("PURCH_ALU",   new Meta("SCOPE3", "t",   "采购铝材", "PROCUREMENT"));
  }

  public static Meta metaOf(String energyType) {
    Meta m = MAP.get(energyType);
    if (m == null) throw BizException.badRequest("未知能源类型: " + energyType);
    return m;
  }

  public static String scopeOf(String energyType) { return metaOf(energyType).scope(); }
  public static String unitOf(String energyType)  { return metaOf(energyType).unit(); }
}
