# 碳排放披露数据血缘与版本影响管理平台

面向制造集团的碳排放披露管理平台：按工厂、能源类型、产品线汇总范围一/二/三排放，
对每一项 tCO2e 指标回答"用了哪份表计数据、哪版排放因子、为什么没随最新资料改变"。

- **来源批次**：工厂上传燃料/用电/采购活动数据，审核员核验；采购批次按来源行+供应商凭证检测冲突，已核算采购量不可被后续上传覆盖
- **因子目录**：按地域、适用期维护排放因子版本，审批生效；平台只使用地域与期间匹配的批准因子，缺因子时指标列为**待核**，不用集团平均数兜底
- **核算运行**：启动时快照因子清单（运行中替换因子不影响本次运行），活动量×因子生成带血缘的排放结果
- **披露模块**：运行结果冻结为报告版本；产品线合计与工厂明细不勾稽时报告保持**待核**，差额不能归零；已发布版本内容不可改写
- **影响服务**：来源补录/修订/缺失、因子修订时自动重算并对比受影响指标与已发布报告，生成重述建议与差异说明，经审批后以新版本重述，披露历史完整保留
- **测量换算与派生指标证明**：燃料采购吨、锅炉标况立方米、干基低位热值等不同口径之间，以带量纲、条件与区间误差的有向换算边逐边求值；换算依据保存原始单位、物质/能源属性、适用工厂与期间、校准/化验证据、有效数字与不确定范围；输出**可直接转换/需人工桥接/量纲不相容/证据不足**四类证明，披露表能量、排放、强度单元格附可复算换算路径与范围；湿基/干基、标况/工况、净热值/低位热值不凭单位名称互换，相反热值依据产生的冲突保留不取平均，冻结后补证只产生带差异说明的新版本

## 技术栈与架构

```
┌────────────┐   HTTP    ┌─────────────────────────┐      ┌──────────────┐
│ Vue3 前端   │ ───────▶ │ SpringBoot 后端 (Java)   │ ───▶ │ PostgreSQL   │
│ 血缘下钻/   │  /api/*  │ 批次校验·运行编排·版本状态 │      │ 来源/因子/运行 │
│ 影响对比/   │          │ 影响评估·披露审批          │      │ 快照/披露证据 │
│ 披露审批    │          └─────────────────────────┘      └──────────────┘
└────────────┘
```

- 前端：Vue 3 + Vite + Element Plus + Vue Router（hash 模式）
- 后端：Spring Boot 3.5（Web / Data JPA / Validation），HMAC 令牌认证，角色权限（管理员/分析员/审核员/财务披露）
- 数据库：PostgreSQL（表结构见 `backend/src/main/resources/schema.sql`）

## 一、Docker Compose 一键启动（推荐）

```bash
docker compose up --build
```

- 前端：http://localhost （nginx，反向代理 /api 到后端）
- 后端：http://localhost:8080/api/health
- 数据库：PostgreSQL 18（compose 内部 `postgres:5432`，库/用户/密码均为 `ccdev`）

首次启动时后端自动建表并写入样例数据（工厂、表计/燃料/采购批次、因子、2025-05 已发布报告）。

## 二、本地开发启动

前置：本机 PostgreSQL 可用（`127.0.0.1:5432`，库/用户/密码 `ccdev`）。

```bash
# 1. 后端（端口 8080；启动时自动建表+写入种子数据）
cd backend
mvn package -DskipTests
java -jar target/carbon-platform.jar

# 2. 前端（二选一）
#  a) 开发模式：vite 开发服务器，端口 5173，代理 /api 到 8080
cd frontend && npm install && npm run dev        # 打开 http://localhost:5173
#  b) 一体化模式：构建后由后端直接托管
cd frontend && npm install && npm run build      # 然后打开 http://localhost:8080
```

> 后端以 `file:./frontend/dist/` 与 `file:../frontend/dist/` 作为可选静态资源目录，
> 在仓库根目录或 backend/ 目录启动 jar 均可托管已构建的前端。

## 三、测试账号

| 账号 | 密码 | 角色 | 主要权限 |
| --- | --- | --- | --- |
| `admin` | `admin123` | 管理员 | 全部操作 |
| `analyst` | `analyst123` | 分析员 | 上传/修订批次、新建因子版本、启动运行、创建报告 |
| `auditor` | `auditor123` | 审核员 | 核验/驳回/作废批次、批准因子版本 |
| `finance` | `finance123` | 财务披露 | 审批/发布报告、接受/驳回重述建议 |

越权操作会被拒绝（403），例如分析员不能核验批次。

## 四、验证方式

### 4.1 自动化验收回放（已实际执行，96 项全部通过）

```bash
# 前置：后端已启动且数据库为初始种子状态
python3 scripts/verify.py                 # 默认 http://127.0.0.1:8080
# 如需重置到种子状态后重跑：
bash scripts/reset-db.sh                  # 清空数据库
# 重启后端（自动重建表+种子数据），再执行 verify.py
```

回放场景（对应验收要求）：

| 场景 | 内容 | 关键断言 |
| --- | --- | --- |
| S0 | 种子基线 | 2025-05 已发布报告总量 2503.18 tCO2e（范围一/二/三 = 70.92/917.26/1515）；运行绑定 6 个因子快照；血缘可下钻到批次行+因子版本 |
| S1 | 来源补录 | 补录柴油 1500L 核验后自动生成影响评估（苏州/精密结构件/SCOPE1 +4.02）与重述建议；接受→冻结 v2→审批→发布；v1 标记"已重述"，历史保留 |
| S2 | 因子修订 | 运行启动后批准新版电力因子（0.5566→0.5420）：本次运行仍用 v1（绑定快照），新运行用 v2；自动生成对 2025-05 已发布报告的影响评估（苏州 SCOPE2 合计 -16.06）与重述 |
| S3 | 并发采购更正 | 修订试图把已核算凭证 V-1001 采购量 500→520：核验被拒（409）并标记冲突，原数量未被覆盖；合规修订（不动已消耗行+新增凭证行）通过；两工厂同时修订同一批次，第二份被拒（409） |
| S4 | 勾稽不平 | 含未分摊公共用电（54.2 tCO2e）与缺因子待核指标的报告保持待核（RECONCILE_FAILED），审批/发布均被拒（409）；修复分摊+批准因子后重算、冻结、审批、发布 |
| S5 | 已发布报告发现来源缺失 | 作废已消耗的 2025-05 燃料批次：自动生成影响评估（苏州 SCOPE1 -70.92）与重述建议；已发布 v3 内容不被覆盖；接受重述→v4 发布，v1~v3 均为"已重述"，披露历史完整 |
| S6 | 测量换算与派生指标（37 项断言） | 2025-07 九段活动读数：工况天然气经"工况m3→标况Nm3→GJ"链闭合（引用校准+化验证据、带不确定范围）；柴油跨期密度修订只取 2025-07 新密度；煤炭含水率化验缺失→湿基→干基边中途截断（证据不足）；燃料油仅人工桥接说明→需人工桥接；电量误按吨登记→量纲不相容；万Nm3 复合单位展开、kg→t 前缀换算；电力/采购强度分母为空；冻结后补含水率化验→两条相反热值链并存（120t→2312.4 vs 2718.48 GJ），冲突保留不取平均，冻结 v2 附差异说明，v1 当时范围保留 |

### 4.2 页面手动验证

登录 http://localhost:8080 （或 5173）后：

1. **仪表盘**：最新发布披露总量、范围一/二/三、待处理影响评估数、待核报告/指标数、分工厂排放
2. **核算运行 → 详情 → 血缘**：任一排放结果可下钻到来源批次行（含供应商凭证、核验人）与因子版本（值、适用期、批准人）
3. **影响评估 → 详情**：受影响指标旧值/新值/差异对比、受影响报告链接、重述建议处置
4. **披露报告 → 详情**：版本历史（已发布/已重述/被取代）、披露条目、勾稽核对表、重述记录
5. **来源批次**：上传/核验/修订/作废；修订已消耗采购批次时可见 409 冲突提示；上传时可按表计原始单位+计量基准（WET/DRY/STD/OP/LHV）登记
6. **因子目录**：新增版本→批准（替换旧版）；使用追踪展示绑定该版本的运行
7. **测量换算**（新菜单）：换算证明四类状态与冲突计数、逐边路径抽屉（每步系数/条件/证据/截断原因）、派生指标（能量 GJ 2 位、排放 tCO2e 3 位、强度 tCO2e/GJ 4 位，各自带范围）、换算依据/有向边与证据台账的登记与批准；披露报告详情页新增"披露单元格追溯"表，展示每个能量/排放/强度单元格的可复算路径、范围、证据与冻结后补证差异说明

## 五、关键业务规则实现

- **因子绑定快照**：运行创建时把当时已批准且覆盖该期间的因子版本写入 `run_factor_bindings`，核算只读快照；因子在运行中被替换只影响之后的新运行（部分唯一索引保证同一期间同时只有一个进行中的正式运行）
- **待核而非平均数**：因子匹配严格按 工厂地域+能源类型+适用期间；匹配不到批准因子时结果标记 `PENDING_FACTOR`（排放量留空），报告勾稽状态为 `PENDING_FACTOR`，审批/发布被拒
- **采购量保护**：修订批次核验时，原批次中已被核算消耗（存在血缘记录）的采购行必须按凭证号保持数量不变，否则整单拒绝并标记冲突；同一批次同时只允许一个进行中的修订
- **勾稽**：冻结时按"工厂明细合计 vs 产品线合计"核对，未分摊差异记入 `reconciliations`；`MISMATCH`/`PENDING_FACTOR` 状态下审批与发布均被拒，无"差额归零"入口
- **不可改写与重述**：已发布版本内容不更新；来源缺失/因子修订触发影响评估（候选运行 vs 已发布版本逐指标对比），重述建议被接受后冻结新版本，发布时旧版本标记 `RESTATED`，版本链完整可查
- **测量换算证明（特性 5）**：
  - 每次单位转换是带量纲、条件与区间误差的有向边（`conversion_edges`），核算时在边图上 DFS 逐边求值，带循环检测；边缺少校准/化验证据时路径**中途截断**，同量纲 SI 前缀（t↔kg、m3↔L 等）走零不确定度虚拟边，湿基/干基、标况/工况、LHV/NCV 不同基准不得隐式穿越
  - 四类证明状态：`CONVERTIBLE` 可直接转换 / `MANUAL_BRIDGE` 需人工桥接 / `DIMENSION_INCOMPATIBLE` 量纲不相容 / `INSUFFICIENT_EVIDENCE` 证据不足；换算未闭合时排放结果标记 `PENDING_CONVERSION`，即使因子齐备也不计算排放量
  - 规范转换模板与图路径共用同一份经批准依据（`conversion_bases`），模板不是图缓存、图路径也不是模板的事后核查；新化验/边界变更先登记依据并批准，罕见单位到来时明确暴露覆盖空洞
  - 同一读数被两条链解释为不同能量时写入 `conversion_conflicts` 并全部保留，不以平均值消除；披露冻结后补到的化验只生成带差异说明的新版本（`disclosure_cell_traces.frozen_note`），原版本保留当时采用的范围
  - 派生指标（`derived_metrics`）与披露单元格（`disclosure_cell_traces`）按各自单位与小数位展示：能量 GJ 2 位、排放量 tCO2e 3 位、强度 tCO2e/GJ 4 位；强度分母为空（电力/采购/零能量）明确标记 `EMPTY_DENOMINATOR`，不强行计算

## 六、API 概览（/api，除登录外均需 `X-Auth-Token`）

| 模块 | 端点 |
| --- | --- |
| 认证 | `POST /auth/login`、`GET /auth/me`、`GET /health` |
| 元数据 | `GET /meta/plants`、`GET /meta/product-lines`、`GET /meta/energy-types` |
| 批次 | `GET/POST /batches`、`GET /batches/{id}`、`POST /batches/{id}/verify|reject|revise|void` |
| 因子 | `GET/POST /factors`、`POST /factors/{id}/versions`、`POST /factors/versions/{vid}/approve|retire`、`GET /factors/versions/{vid}/usage` |
| 运行 | `GET/POST /runs`、`GET /runs/{id}`、`GET /results/{rid}/lineage` |
| 披露 | `GET/POST /reports`、`GET /reports/{id}`、`POST /reports/{id}/freeze|approve|publish`、`GET /reports/versions/{vid}` |
| 影响 | `GET /impacts`、`GET /impacts/{id}`、`POST /impacts/restatements/{id}/accept|reject` |
| 测量换算 | `GET/POST /conversion/evidence`、`GET/POST /conversion/bases`、`POST /conversion/bases/{id}/approve`、`GET /conversion/runs/{runId}/conversions|summary|conflicts|metrics`、`GET /conversion/conversions/{id}`、`GET /conversion/conflicts`、`GET /conversion/versions/{versionId}/traces` |
| 仪表盘 | `GET /dashboard/summary` |

## 七、目录结构

```
├── backend/                 # SpringBoot 后端
│   ├── Dockerfile
│   ├── src/main/resources/schema.sql       # 表结构（幂等，含换算 7 张新表）
│   └── src/main/java/com/group/carbon/     # config/security/model/repo/service/controller
│       ├── common/MeasurementUnits.java    # 量纲/计量基准/单位登记
│       └── service/ConversionService.java  # 图换算引擎：逐边求值、截断、冲突、派生指标、单元格追溯
├── frontend/                # Vue3 前端
│   ├── Dockerfile  nginx.conf
│   └── src/views/           # 仪表盘/来源/因子/运行/测量换算/影响/披露页面
├── scripts/
│   ├── verify.py            # 验收回放（S0~S6，96 项断言）
│   └── reset-db.sh          # 重置本地数据库
└── docker-compose.yml       # 一键启动（postgres + backend + frontend）
```

## 八、验证记录（实际执行过的验证）

以下验证均在本开发容器内实际执行通过：

- `mvn package -DskipTests`：后端编译打包通过（Spring Boot 3.5.7，Java 25 运行时，编译目标 21）
- `npm install && npm run build`：前端构建通过（vite 6，产物 dist/，含测量换算工作台）
- 后端启动后自动建表+种子数据：`/api/health` → UP；种子报告总量 2503.18 tCO2e 与手工核算一致；2025-07 换算验收运行自动生成（5 可转换/1 人工桥接/1 量纲不相容/2 证据不足）
- `python3 scripts/verify.py`：**96 项断言全部通过**（S0~S5 原有 59 项零回归 + S6 测量换算 37 项）
- 后端托管前端：`GET /`（根路径转发 index.html）、`GET /index.html` 与 `GET /assets/*.js` 均 200
- Docker Compose 配置：本容器无 docker CLI，`docker compose up` 未实际执行；Dockerfile/compose 已经静态核验（镜像标签、构建上下文、健康检查、依赖顺序、nginx 代理路径），请在有 Docker 的环境执行 `docker compose up --build` 验证
