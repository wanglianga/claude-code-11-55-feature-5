#!/usr/bin/env python3
# -*- coding: utf-8 -*-
"""
验收回放脚本：碳排放披露数据血缘与版本影响管理平台
回放场景：
  S0 基线：种子数据（2025-05 已发布报告）核对
  S1 来源补录 → 影响评估 → 重述建议 → 接受 → 重新发布（历史保留）
  S2 因子修订：运行中替换因子，当前运行仍绑定启动清单；新运行用新版
  S3 并发采购更正：已核算采购量不可覆盖；同批次并发修订冲突
  S4 勾稽不平：报告保持待核，审批/发布被拒；修复后放行
  S5 已发布报告发现来源缺失：生成重述建议与差异说明，不覆盖已发布报告
  S6 测量换算与派生指标：四类换算状态、逐边路径、不确定范围、冲突保留、
     跨期密度修订、复合单位、强度分母为空、冻结后补证产生新版本
前置：后端已启动（默认 http://127.0.0.1:8080），数据库为初始种子状态。
"""
import json
import sys
import time
import urllib.error
import urllib.request

BASE = sys.argv[1] if len(sys.argv) > 1 else 'http://127.0.0.1:8080'
API = BASE + '/api'

PASSED, FAILED = [], []


def req(method, path, body=None, token=None):
    url = API + path
    data = json.dumps(body).encode('utf-8') if body is not None else None
    r = urllib.request.Request(url, data=data, method=method)
    r.add_header('Content-Type', 'application/json')
    if token:
        r.add_header('X-Auth-Token', token)
    try:
        with urllib.request.urlopen(r, timeout=30) as resp:
            raw = resp.read().decode('utf-8')
            return resp.status, (json.loads(raw) if raw else {})
    except urllib.error.HTTPError as e:
        raw = e.read().decode('utf-8')
        try:
            return e.code, json.loads(raw)
        except json.JSONDecodeError:
            return e.code, {'error': raw}


def check(name, cond, detail=''):
    if cond:
        PASSED.append(name)
        print(f'  [PASS] {name}')
    else:
        FAILED.append((name, detail))
        print(f'  [FAIL] {name} :: {detail}')


def login(username, password):
    s, b = req('POST', '/auth/login', {'username': username, 'password': password})
    if s != 200:
        print(f'登录失败 {username}: {b}')
        sys.exit(1)
    return b['token']


def wait_run(token, run_id, timeout=40):
    for _ in range(timeout * 2):
        s, b = req('GET', f'/runs/{run_id}', token=token)
        if s == 200 and b['run']['status'] in ('COMPLETED', 'FAILED'):
            return b['run']
        time.sleep(0.5)
    raise RuntimeError(f'运行 {run_id} 超时未完成')


def find_impact(token, trigger_type, period, ref_keyword=None):
    s, impacts = req('GET', '/impacts', token=token)
    for im in impacts:
        if im['triggerType'] == trigger_type and im['period'] == period:
            if ref_keyword and ref_keyword not in im['triggerRef']:
                continue
            return im
    return None


def report_of(token, period):
    s, reports = req('GET', '/reports', token=token)
    for r in reports:
        if r['period'] == period:
            return r
    return None


def run_s6(admin, analyst, auditor, finance):
    print('\n[S6] 测量换算与派生指标证明')
    # 2025-07 正式运行（种子自动生成）
    s, runs = req('GET', '/runs', token=admin)
    run_jul = next(r for r in runs if r['period'] == '2025-07' and r['purpose'] == 'OFFICIAL')
    check('S6 2025-07 换算验收运行已完成', run_jul['status'] == 'COMPLETED')
    rid = run_jul['id']

    # 6.0 四类状态计数
    s, summ = req('GET', f'/conversion/runs/{rid}/summary', token=admin)
    check('S6 四类状态：可转换5/人工桥接1/量纲不相容1/证据不足2',
          summ['CONVERTIBLE'] == 5 and summ['MANUAL_BRIDGE'] == 1
          and summ['DIMENSION_INCOMPATIBLE'] == 1 and summ['INSUFFICIENT_EVIDENCE'] == 2,
          str(summ))
    check('S6 初始状态无换算冲突', summ['conflictCount'] == 0, str(summ))

    s, convs = req('GET', f'/conversion/runs/{rid}/conversions', token=admin)
    def find_conv(energy, unit, qty):
        return next((c for c in convs
                     if c['energyType'] == energy and c['sourceUnit'] == unit
                     and abs(float(c['sourceQty']) - qty) < 1e-6), None)

    # 6.1 工况天然气：标况/工况不可凭名称互换，经校准+化验链闭合到 GJ
    ng = find_conv('NATURAL_GAS', 'm3', 50000)
    check('S6 工况天然气可直接转换', ng and ng['convStatus'] == 'CONVERTIBLE', str(ng))
    check('S6 天然气物理能量 ≈ 2067.183 GJ（50000×1.062×0.03893）',
          ng and abs(float(ng['targetQty']) - 2067.183) < 0.01, str(ng and ng['targetQty']))
    check('S6 能量带不确定上下界',
          ng and ng['lowerQty'] is not None and float(ng['lowerQty']) < float(ng['targetQty']) < float(ng['upperQty']))
    path = json.loads(ng['pathJson'])
    all_steps = [st for p in path['paths'] for st in p['steps']]
    ev_nos = {st.get('evidenceNo') for st in all_steps if st.get('evidenceNo')}
    check('S6 换算链引用校准证书与化验报告证据',
          'EV-CAL-NG-TEMP' in ev_nos and 'EV-LAB-NG-SZ' in ev_nos, str(ev_nos))
    bases_seen = {(st['fromUnit'], st['toUnit']) for st in all_steps}
    check('S6 路径逐边：工况m3→标况Nm3→GJ',
          ('m3', 'Nm3') in bases_seen and ('Nm3', 'GJ') in bases_seen, str(bases_seen))

    # 6.2 跨期密度修订：2025-07 必须使用新密度 0.000842，旧密度 0.000835 不适用
    diesel = find_conv('DIESEL', 'L', 900)
    check('S6 柴油跨期密度修订链可直接转换', diesel and diesel['convStatus'] == 'CONVERTIBLE')
    dpath = json.loads(diesel['pathJson'])
    dtxt = json.dumps(dpath, ensure_ascii=False)
    check('S6 2025-07 采用新密度 0.000842（旧密度 0.000835 不适用本期间）',
          '0.000842' in dtxt and '0.000835' not in dtxt, dtxt[:200])
    check('S6 柴油物理能量 ≈ 34.0978 GJ',
          abs(float(diesel['targetQty']) - 34.097779) < 0.01, str(diesel['targetQty']))

    # 6.3 湿度缺失：苏州煤炭湿基→干基边无证据，中途截断
    coal_sz = find_conv('COAL', 't', 120)
    check('S6 湿度缺失→证据不足并中途截断',
          coal_sz and coal_sz['convStatus'] == 'INSUFFICIENT_EVIDENCE'
          and '截断' in (coal_sz.get('truncateReason') or ''), str(coal_sz))
    coal_cd = find_conv('COAL', 't', 80)
    check('S6 成都煤炭无适用依据→证据不足',
          coal_cd and coal_cd['convStatus'] == 'INSUFFICIENT_EVIDENCE', str(coal_cd))

    # 6.4 人工桥接
    fo = find_conv('FUEL_OIL', 'LN', 6000)
    check('S6 仅人工桥接说明→MANUAL_BRIDGE', fo and fo['convStatus'] == 'MANUAL_BRIDGE', str(fo))

    # 6.5 复合单位展开 + 采购 kg→t
    wan = find_conv('NATURAL_GAS', '万Nm3', 5.2)
    check('S6 复合单位万Nm3经COMPOUND边展开后可转换',
          wan and wan['convStatus'] == 'CONVERTIBLE'
          and abs(float(wan['canonicalQty']) - 48964.188) < 0.01, str(wan and wan['canonicalQty']))
    alu = find_conv('PURCH_ALU', 'kg', 450)
    check('S6 采购铝材 450kg → 0.45t 同量纲前缀换算',
          alu and alu['convStatus'] == 'CONVERTIBLE' and abs(float(alu['canonicalQty']) - 0.45) < 1e-6,
          str(alu))

    # 6.6 量纲不相容 + 强度分母为空
    elec_bad = find_conv('GRID_ELEC', 't', 30)
    check('S6 电量误按质量单位登记→量纲不相容',
          elec_bad and elec_bad['convStatus'] == 'DIMENSION_INCOMPATIBLE', str(elec_bad))
    elec_ok = find_conv('GRID_ELEC', 'kWh', 100000)
    check('S6 电力读数恒等可转换', elec_ok and elec_ok['convStatus'] == 'CONVERTIBLE')

    # 6.7 换算证明详情接口
    s, cd = req('GET', f"/conversion/conversions/{ng['id']}", token=admin)
    check('S6 换算证明详情含来源行与路径', s == 200 and cd['sourceRow']['id'] == ng['sourceRowId'])

    # 6.8 派生指标：能量/排放/强度三列单位与小数位、空分母
    s, metrics = req('GET', f'/conversion/runs/{rid}/metrics', token=admin)
    ng_energy = next(m for m in metrics if m['energyType'] == 'NATURAL_GAS'
                     and m['plantId'] == 1 and m['metricKind'] == 'ENERGY')
    check('S6 能量指标 GJ、2 位小数口径并带范围',
          ng_energy['metricUnit'] == 'GJ' and ng_energy['displayDecimals'] == 2
          and ng_energy['lowerValue'] is not None, str(ng_energy))
    ng_int = next(m for m in metrics if m['energyType'] == 'NATURAL_GAS'
                  and m['plantId'] == 1 and m['metricKind'] == 'INTENSITY')
    check('S6 强度 tCO2e/GJ、4 位小数且可计算',
          ng_int['metricStatus'] == 'OK' and ng_int['displayDecimals'] == 4
          and ng_int['metricValue'] is not None, str(ng_int))
    empty_den = [m for m in metrics if m['metricKind'] == 'INTENSITY'
                 and m['metricStatus'] == 'EMPTY_DENOMINATOR']
    check('S6 电力/采购强度分母为空→EMPTY_DENOMINATOR（不强行算强度）',
          len(empty_den) >= 2, str([(m['energyType'], m['metricStatus']) for m in metrics]))
    coal_metrics = [m for m in metrics if m['energyType'] == 'COAL']
    check('S6 未闭合换算的派生指标保持待核',
          all(m['metricStatus'] in ('PENDING_CONVERSION', 'PENDING_FACTOR') for m in coal_metrics))

    # 6.9 披露单元格追溯
    rep7 = report_of(admin, '2025-07')
    s, rd7 = req('GET', f"/reports/{rep7['id']}", token=admin)
    v1j = rd7['versions'][0]
    check('S6 2025-07 报告含待核项保持待核',
          rd7['report']['status'] == 'RECONCILE_FAILED' and v1j['pendingCount'] == 4,
          str(rd7['report']['status']))
    traces = rd7['cellTraces']
    statuses = {(t['cellType'], t['traceStatus']) for t in traces}
    check('S6 单元格追溯四类状态齐全（TRACEABLE/PENDING/EMPTY_DENOMINATOR）',
          ('EMISSION', 'TRACEABLE') in statuses and ('ENERGY', 'PENDING') in statuses
          and ('INTENSITY', 'EMPTY_DENOMINATOR') in statuses, str(statuses))
    tr = next(t for t in traces if t['energyType'] == 'NATURAL_GAS' and t['cellType'] == 'ENERGY')
    check('S6 能量单元格附范围与证据摘要',
          tr['lowerValue'] is not None and tr['upperValue'] is not None
          and 'EV-' in (tr['evidenceSummary'] or ''), str(tr))
    tj = json.loads(tr['tracePathJson'])
    check('S6 单元格路径可复算：含来源行、换算证明与因子版本',
          len(tj.get('conversionIds', [])) > 0 and 'factorVersionId' in tj, str(tj)[:200])

    # 6.10 权限：分析员不能批准换算依据
    s, b = req('POST', '/conversion/bases', {
        'code': 'CB-PERM-TEST', 'name': '权限测试依据', 'energyType': 'DIESEL', 'plantId': 1,
        'periodFrom': '2025-07', 'sourceUnit': 'L', 'targetUnit': 'm3',
        'edgeKind': 'PROPERTY', 'factorValue': 0.001, 'uncertaintyPct': 1.0}, token=analyst)
    check('S6 分析员可创建换算依据草稿', s == 200, str(b))
    s, b2 = req('POST', f"/conversion/bases/{b['id']}/approve", token=analyst)
    check('S6 分析员批准依据被拒绝(403)', s == 403, f's={s}')

    # 6.11 冻结后补证：含水率化验补到 → 新核算版本，原披露保留当时范围
    s, ev = req('POST', '/conversion/evidence', {
        'evidenceNo': 'EV-LAB-COAL-MOIST-S6', 'evidenceType': 'LAB_ASSAY',
        'title': '煤炭含水率化验（披露冻结后补证）', 'sourceRef': 'LAB-S6-M01',
        'issuer': '集团中心化验室', 'issuedDate': '2025-07-18', 'effectiveFrom': '2025-07-01',
        'plantId': 1, 'energyType': 'COAL', 'measuredProp': 'MOISTURE',
        'nominalValue': 0.06, 'unit': 'fraction', 'sigFigs': 2, 'uncertaintyPct': 3.0}, token=analyst)
    check('S6 冻结后补登含水率化验证据', s == 200, str(ev))
    s, nb = req('POST', '/conversion/bases', {
        'code': 'CB-COAL-WET-DRY-S6', 'name': '煤炭湿基→干基（补证 0.94）', 'energyType': 'COAL',
        'plantId': 1, 'periodFrom': '2025-07', 'sourceUnit': 't', 'sourceBasis': 'WET',
        'targetUnit': 't', 'targetBasis': 'DRY', 'edgeKind': 'BASIS_SWITCH',
        'factorValue': 0.94, 'uncertaintyPct': 3.0, 'evidenceId': ev['id']}, token=analyst)
    s, ap = req('POST', f"/conversion/bases/{nb['id']}/approve", token=auditor)
    check('S6 审核员批准补证依据', s == 200 and ap['status'] == 'APPROVED', str(ap))

    s, nr = req('POST', '/runs', {'period': '2025-07'}, token=analyst)
    nr = wait_run(admin, nr['id'])
    s, summ2 = req('GET', f"/conversion/runs/{nr['id']}/summary", token=admin)
    check('S6 补证后：苏州煤炭闭合（CONVERTIBLE=6）且保留 1 条冲突',
          summ2['CONVERTIBLE'] == 6 and summ2['conflictCount'] == 1
          and summ2['INSUFFICIENT_EVIDENCE'] == 1, str(summ2))
    s, conflicts = req('GET', f"/conversion/runs/{nr['id']}/conflicts", token=admin)
    cc = next((x for x in conflicts if x['energyType'] == 'COAL'), None)
    check('S6 两份相反热值依据：同一读数解释为不同能量并保留（不取平均）',
          cc and abs(float(cc['qtyA']) - 2312.4) < 0.1 and abs(float(cc['qtyB']) - 2718.48) < 0.1,
          str(cc and (cc['qtyA'], cc['qtyB'])))

    # 冻结新版本：原 v1 保留当时范围，新 v2 记录差异说明
    s, fv = req('POST', f"/reports/{rep7['id']}/freeze",
                {'runId': nr['id'], 'note': '冻结后补证重算（含水率化验）'}, token=analyst)
    check('S6 补证后冻结新版本 v2', s == 200 and fv['versionNo'] == 2, str(fv))
    s, rd7b = req('GET', f"/reports/{rep7['id']}", token=admin)
    v1_after, v2_after = rd7b['versions'][1], rd7b['versions'][0]
    check('S6 原披露 v1 保留（仍为冻结态、内容未被覆盖）',
          v1_after['versionNo'] == 1 and v1_after['status'] == 'SUPERSEDED', str(v1_after['status']))
    s, vd2 = req('GET', f"/reports/versions/{v2_after['id']}", token=admin)
    coal_energy_traces = [t for t in vd2['cellTraces']
                          if t['energyType'] == 'COAL' and t['cellType'] == 'ENERGY' and t['plantId'] == 1]
    check('S6 新核算版本煤炭能量单元格标记 CONFLICT 并附冻结差异说明',
          coal_energy_traces and coal_energy_traces[0]['traceStatus'] == 'CONFLICT'
          and coal_energy_traces[0]['frozenNote'] is not None,
          str(coal_energy_traces[:1]))
    s, vd1 = req('GET', f"/reports/versions/{v1_after['id']}", token=admin)
    v1_coal = [t for t in vd1['cellTraces'] if t['energyType'] == 'COAL' and t['cellType'] == 'ENERGY']
    check('S6 v1 当时范围保留：煤炭能量仍为待核无值（未被补证回填）',
          all(t['displayedValue'] is None and t['traceStatus'] == 'PENDING' for t in v1_coal),
          str([(t['traceStatus'], t['displayedValue']) for t in v1_coal]))


def main():
    print(f'== 目标: {BASE}')
    admin = login('admin', 'admin123')
    analyst = login('analyst', 'analyst123')
    auditor = login('auditor', 'auditor123')
    finance = login('finance', 'finance123')

    # ---------- S0 基线 ----------
    print('\n[S0] 基线：种子数据核对')
    s, dash = req('GET', '/dashboard/summary', token=admin)
    pub = dash.get('latestPublished') or {}
    check('S0 已发布 2025-05 报告', pub.get('period') == '2025-05', str(pub))
    check('S0 披露总量 2503.18 tCO2e',
          pub and abs(float(pub['totalTco2e']) - 2503.18) < 0.01, str(pub.get('totalTco2e')))
    check('S0 范围一/二/三 = 70.92/917.26/1515',
          pub and abs(float(pub['totalScope1']) - 70.92) < 0.01
          and abs(float(pub['totalScope2']) - 917.26) < 0.01
          and abs(float(pub['totalScope3']) - 1515) < 0.01,
          str(pub))
    s, runs = req('GET', '/runs', token=admin)
    run_may = next((r for r in runs if r['period'] == '2025-05' and r['purpose'] == 'OFFICIAL'), None)
    check('S0 2025-05 正式运行已完成', run_may and run_may['status'] == 'COMPLETED')
    s, detail = req('GET', f"/runs/{run_may['id']}", token=admin)
    check('S0 运行绑定 6 个批准因子快照', len(detail['bindings']) == 6, str(len(detail['bindings'])))
    res0 = next(r for r in detail['results'] if r['energyType'] == 'GRID_ELEC' and r['plantId'] == 1
                and r['productLineId'] == 1)
    s, lin = req('GET', f"/results/{res0['id']}/lineage", token=admin)
    check('S0 血缘：结果可下钻到因子版本与来源行',
          lin['factorVersion']['factorCode'] == 'ELEC-CNEAST'
          and lin['sources'][0]['batchNo'] == 'B-2025-05-ELEC-SZ', json.dumps(lin)[:200])

    # ---------- S1 来源补录 ----------
    print('\n[S1] 来源补录 → 影响评估 → 重述')
    s, b = req('POST', '/batches', {
        'plantId': 1, 'category': 'FUEL', 'period': '2025-05',
        'rows': [{'productLineId': 1, 'energyType': 'DIESEL', 'quantity': 1500}]}, token=analyst)
    check('S1 补录批次已上传', s == 200, str(b))
    batch_s1 = b['id']
    s, b = req('POST', f'/batches/{batch_s1}/verify', token=auditor)
    check('S1 补录批次核验通过', s == 200 and b['status'] == 'VERIFIED', str(b))
    im = find_impact(admin, 'SOURCE_SUPPLEMENT', '2025-05')
    check('S1 自动生成来源补录影响评估', im is not None)
    s, imd = req('GET', f"/impacts/{im['id']}", token=admin)
    item = next((i for i in imd['items'] if i['scope'] == 'SCOPE1'), None)
    check('S1 影响指标：苏州/精密结构件/SCOPE1 +4.02',
          item and abs(float(item['diff']) - 4.02) < 0.001
          and abs(float(item['oldValue']) - 70.92) < 0.001, json.dumps(imd['items'])[:300])
    check('S1 影响项关联已发布报告', item and item['affectedReportId'] is not None)
    rest = imd['restatements'][0] if imd['restatements'] else None
    check('S1 自动生成重述建议(待处理)', rest and rest['status'] == 'PROPOSED', str(imd['restatements']))
    rep = report_of(admin, '2025-05')
    s, b = req('POST', f"/impacts/restatements/{rest['id']}/accept", token=finance)
    check('S1 接受重述 → 冻结 v2', s == 200 and b['newVersion']['versionNo'] == 2, str(b))
    s, b = req('POST', f"/reports/{rep['id']}/approve", token=finance)
    check('S1 重述版本审批通过', s == 200 and b['status'] == 'APPROVED', str(b))
    s, b = req('POST', f"/reports/{rep['id']}/publish", token=finance)
    check('S1 重述版本发布', s == 200 and b['status'] == 'PUBLISHED', str(b))
    s, rd = req('GET', f"/reports/{rep['id']}", token=admin)
    v1, v2 = rd['versions'][1], rd['versions'][0]
    check('S1 披露历史：v1 已重述 / v2 已发布',
          v1['status'] == 'RESTATED' and v2['status'] == 'PUBLISHED',
          f"v1={v1['status']} v2={v2['status']}")
    check('S1 v2 范围一 = 74.94（70.92+4.02）',
          abs(float(v2['totalScope1']) - 74.94) < 0.01, str(v2['totalScope1']))

    # ---------- S2 因子修订（运行中替换） ----------
    print('\n[S2] 因子修订：运行中替换因子仍绑定启动清单')
    s, run1 = req('POST', '/runs', {'period': '2025-06'}, token=analyst)
    check('S2 2025-06 正式运行启动', s == 200 and run1['status'] == 'RUNNING', str(run1))
    s, factors = req('GET', '/factors', token=admin)
    elec_east = next(f for f in factors if f['factor']['code'] == 'ELEC-CNEAST')
    s, v2draft = req('POST', f"/factors/{elec_east['factor']['id']}/versions",
                     {'value': 0.5420, 'validFrom': '2025-01-01', 'validTo': '2025-12-31'},
                     token=analyst)
    check('S2 新版电力因子草稿创建', s == 200, str(v2draft))
    s, b = req('POST', f"/factors/versions/{v2draft['id']}/approve", token=auditor)
    check('S2 运行中批准 v2（替换 v1）', s == 200 and b['status'] == 'APPROVED', str(b))
    run1 = wait_run(admin, run1['id'])
    s, d1 = req('GET', f"/runs/{run1['id']}", token=admin)
    vals = {r['factorValue'] for r in d1['results'] if r['energyType'] == 'GRID_ELEC'
            and r['plantId'] == 1 and r['status'] == 'OK'}
    check('S2 运行中替换因子：本次运行仍用启动时 v1=0.5566',
          vals == {0.5566} or vals == {'0.5566'}, str(vals))
    bound = {bnd['factorCode']: bnd['value'] for bnd in d1['bindings']}
    check('S2 绑定清单快照保留 v1 值', float(bound.get('ELEC-CNEAST', 0)) == 0.5566, str(bound))
    s, run2 = req('POST', '/runs', {'period': '2025-06'}, token=analyst)
    run2 = wait_run(admin, run2['id'])
    s, d2 = req('GET', f"/runs/{run2['id']}", token=admin)
    vals2 = {r['factorValue'] for r in d2['results'] if r['energyType'] == 'GRID_ELEC'
             and r['plantId'] == 1 and r['status'] == 'OK'}
    check('S2 新运行使用替换后 v2=0.5420', vals2 == {0.542} or vals2 == {'0.542'}, str(vals2))
    im_f = find_impact(admin, 'FACTOR_REVISION', '2025-05')
    check('S2 因子修订触发对 2025-05 已发布报告的影响评估', im_f is not None)
    s, imfd = req('GET', f"/impacts/{im_f['id']}", token=admin)
    diff_s2 = sum(float(i['diff']) for i in imfd['items']
                  if i['scope'] == 'SCOPE2' and i['plantId'] == 1)
    check('S2 影响差异：苏州 SCOPE2 合计 -16.06（0.5566→0.5420 × 110万kWh）',
          abs(diff_s2 + 16.06) < 0.01, f'diff={diff_s2}')
    rest_f = imfd['restatements'][0] if imfd['restatements'] else None
    check('S2 生成重述建议', rest_f and rest_f['status'] == 'PROPOSED')
    # 接受因子重述 → v3
    s, b = req('POST', f"/impacts/restatements/{rest_f['id']}/accept", token=finance)
    check('S2 接受重述 → 冻结 v3', s == 200 and b['newVersion']['versionNo'] == 3, str(b))
    rep = report_of(admin, '2025-05')
    req('POST', f"/reports/{rep['id']}/approve", token=finance)
    s, b = req('POST', f"/reports/{rep['id']}/publish", token=finance)
    check('S2 v3 发布', s == 200 and b['status'] == 'PUBLISHED', str(b))

    # ---------- S3 并发采购更正 ----------
    print('\n[S3] 并发采购更正：已核算采购量不可覆盖')
    s, batches = req('GET', '/batches', token=admin, )
    proc = next(b for b in batches if b['batchNo'] == 'B-2025-06-PROC-GP')
    s, pd = req('GET', f"/batches/{proc['id']}", token=admin)
    check('S3 采购批次行已被核算消耗', len(pd['consumedRowIds']) == 2, str(pd['consumedRowIds']))
    # 3a: 修订试图改小/改大已消耗凭证数量 → 409
    s, rev1 = req('POST', f"/batches/{proc['id']}/revise", {
        'plantId': 1, 'category': 'PROCUREMENT', 'period': '2025-06',
        'rows': [
            {'plantId': 1, 'productLineId': 1, 'energyType': 'PURCH_STEEL', 'quantity': 520, 'voucherNo': 'V-1001'},
            {'plantId': 2, 'productLineId': 1, 'energyType': 'PURCH_STEEL', 'quantity': 300, 'voucherNo': 'V-1002'}]},
        token=analyst)
    check('S3 修订批次已提交(待核验)', s == 200, str(rev1))
    s, b = req('POST', f"/batches/{rev1['id']}/verify", token=auditor)
    check('S3 覆盖已核算采购量被拒绝(409)', s == 409, f's={s} {b}')
    s, b = req('GET', f"/batches/{rev1['id']}", token=admin)
    check('S3 违规修订标记为冲突', b['batch']['status'] == 'CONFLICT', str(b['batch']['status']))
    s, b = req('GET', f"/batches/{proc['id']}", token=admin)
    row_v1001 = next(r for r in b['rows'] if r['voucherNo'] == 'V-1001')
    check('S3 原采购量未被覆盖（V-1001 仍 500）',
          float(row_v1001['quantity']) == 500.0, str(row_v1001['quantity']))
    # 3b: 合规修订（不动已消耗行，新增凭证行）→ 通过
    s, rev2 = req('POST', f"/batches/{proc['id']}/revise", {
        'plantId': 1, 'category': 'PROCUREMENT', 'period': '2025-06',
        'rows': [
            {'plantId': 1, 'productLineId': 1, 'energyType': 'PURCH_STEEL', 'quantity': 500, 'voucherNo': 'V-1001'},
            {'plantId': 2, 'productLineId': 1, 'energyType': 'PURCH_STEEL', 'quantity': 300, 'voucherNo': 'V-1002'},
            {'plantId': 2, 'productLineId': 2, 'energyType': 'PURCH_STEEL', 'quantity': 80, 'voucherNo': 'V-1003'}]},
        token=analyst)
    s, b = req('POST', f"/batches/{rev2['id']}/verify", token=auditor)
    check('S3 合规修订核验通过', s == 200 and b['status'] == 'VERIFIED', str(b))
    s, b = req('GET', f"/batches/{proc['id']}", token=admin)
    check('S3 原批次被取代', b['batch']['status'] == 'SUPERSEDED', str(b['batch']['status']))
    # 3c: 同一批次并发修订 → 第二个 409
    cur = rev2['id']
    s, ra = req('POST', f"/batches/{cur}/revise", {
        'plantId': 1, 'category': 'PROCUREMENT', 'period': '2025-06',
        'rows': [
            {'plantId': 1, 'productLineId': 1, 'energyType': 'PURCH_STEEL', 'quantity': 500, 'voucherNo': 'V-1001'},
            {'plantId': 2, 'productLineId': 1, 'energyType': 'PURCH_STEEL', 'quantity': 300, 'voucherNo': 'V-1002'},
            {'plantId': 2, 'productLineId': 2, 'energyType': 'PURCH_STEEL', 'quantity': 80, 'voucherNo': 'V-1003'}]},
        token=analyst)
    check('S3 工厂A修订提交成功', s == 200, str(ra))
    s, rb = req('POST', f"/batches/{cur}/revise", {
        'plantId': 2, 'category': 'PROCUREMENT', 'period': '2025-06',
        'rows': [
            {'plantId': 1, 'productLineId': 1, 'energyType': 'PURCH_STEEL', 'quantity': 500, 'voucherNo': 'V-1001'},
            {'plantId': 2, 'productLineId': 1, 'energyType': 'PURCH_STEEL', 'quantity': 300, 'voucherNo': 'V-1002'},
            {'plantId': 2, 'productLineId': 2, 'energyType': 'PURCH_STEEL', 'quantity': 80, 'voucherNo': 'V-1003'}]},
        token=analyst)
    check('S3 工厂B并发修订同一批次被拒绝(409)', s == 409, f's={s} {rb}')
    req('POST', f"/batches/{ra['id']}/reject", {'reason': '清理演示数据'}, token=auditor)

    # ---------- S4 勾稽不平保持待核 ----------
    print('\n[S4] 勾稽不平：报告保持待核，不得归零')
    s, b = req('POST', '/reports', {
        'title': '2025年6月集团碳排放披露报告', 'period': '2025-06', 'runId': run2['id']},
        token=analyst)
    check('S4 2025-06 报告创建', s == 200, str(b))
    rep6 = b
    check('S4 报告保持待核(RECONCILE_FAILED)', b['status'] == 'RECONCILE_FAILED', str(b['status']))
    s, rd6 = req('GET', f"/reports/{rep6['id']}", token=admin)
    ver6 = rd6['versions'][0]
    check('S4 勾稽差异=未分摊公共用电 54.2 tCO2e',
          ver6['reconcileStatus'] == 'MISMATCH' and abs(float(ver6['reconcileDiff']) - 54.2) < 0.01,
          f"{ver6['reconcileStatus']} diff={ver6['reconcileDiff']}")
    check('S4 含 1 项缺因子待核指标（成都天然气）', ver6['pendingCount'] == 1, str(ver6['pendingCount']))
    s, b = req('POST', f"/reports/{rep6['id']}/approve", token=finance)
    check('S4 待核报告审批被拒绝(409)', s == 409, f's={s}')
    s, b = req('POST', f"/reports/{rep6['id']}/publish", token=finance)
    check('S4 待核报告发布被拒绝(409)', s == 409, f's={s}')
    # 修复1：批准西南天然气因子 → 消除待核指标
    s, factors = req('GET', '/factors', token=admin)
    ng_west = next(f for f in factors if f['factor']['code'] == 'NG-CNWEST')
    draft_v = next(v for v in ng_west['versions'] if v['status'] == 'DRAFT')
    s, b = req('POST', f"/factors/versions/{draft_v['id']}/approve", token=auditor)
    check('S4 西南天然气因子批准', s == 200, str(b))
    # 修复2：修订电力批次，把公共用电分摊到产品线
    elec6 = next(x for x in req('GET', '/batches', token=admin)[1]
                 if x['batchNo'] == 'B-2025-06-ELEC-SZ')
    s, rev_e = req('POST', f"/batches/{elec6['id']}/revise", {
        'plantId': 1, 'category': 'ELECTRICITY', 'period': '2025-06',
        'rows': [
            {'productLineId': 1, 'energyType': 'GRID_ELEC', 'quantity': 800000},
            {'productLineId': 2, 'energyType': 'GRID_ELEC', 'quantity': 500000}]},
        token=analyst)
    s, b = req('POST', f"/batches/{rev_e['id']}/verify", token=auditor)
    check('S4 公共用电分摊修订核验通过', s == 200 and b['status'] == 'VERIFIED', str(b))
    # 修复3：重新运行并冻结新版本
    s, run3 = req('POST', '/runs', {'period': '2025-06'}, token=analyst)
    run3 = wait_run(admin, run3['id'])
    s, d3 = req('GET', f"/runs/{run3['id']}", token=admin)
    pend3 = [r for r in d3['results'] if r['status'] == 'PENDING_FACTOR']
    check('S4 重算后无待核指标', len(pend3) == 0, str(len(pend3)))
    s, b = req('POST', f"/reports/{rep6['id']}/freeze", {'runId': run3['id'], 'note': '修复后重冻结'},
               token=analyst)
    check('S4 修复后冻结 v2 勾稽一致', s == 200 and b['reconcileStatus'] == 'OK', str(b))
    s, b = req('POST', f"/reports/{rep6['id']}/approve", token=finance)
    check('S4 修复后审批通过', s == 200 and b['status'] == 'APPROVED', str(b))
    s, b = req('POST', f"/reports/{rep6['id']}/publish", token=finance)
    check('S4 修复后发布成功', s == 200 and b['status'] == 'PUBLISHED', str(b))

    # ---------- S5 已发布报告发现来源缺失 ----------
    print('\n[S5] 已发布报告发现来源缺失 → 重述建议，不覆盖已发布报告')
    s, rd = req('GET', f"/reports/{rep['id']}", token=admin)
    pub_v = rd['versions'][0]
    check('S5 当前发布版本 v3（范围一 74.94）',
          pub_v['versionNo'] == 3 and abs(float(pub_v['totalScope1']) - 74.94) < 0.01,
          f"v{pub_v['versionNo']} s1={pub_v['totalScope1']}")
    fuel_may = next(x for x in req('GET', '/batches', token=admin)[1]
                    if x['batchNo'] == 'B-2025-05-FUEL-SZ')
    s, b = req('POST', f"/batches/{fuel_may['id']}/void",
               {'reason': '审计发现电表/气表原始记录缺失'}, token=auditor)
    check('S5 来源批次作废', s == 200 and b['status'] == 'VOID', str(b))
    im_m = find_impact(admin, 'SOURCE_MISSING', '2025-05')
    check('S5 自动生成来源缺失影响评估', im_m is not None)
    s, immd = req('GET', f"/impacts/{im_m['id']}", token=admin)
    item_m = next((i for i in immd['items'] if i['scope'] == 'SCOPE1'), None)
    check('S5 差异说明：苏州 SCOPE1 -70.92（缺失燃料数据）',
          item_m and abs(float(item_m['diff']) + 70.92) < 0.01, json.dumps(immd['items'])[:300])
    rest_m = immd['restatements'][0] if immd['restatements'] else None
    check('S5 生成重述建议(待处理)', rest_m and rest_m['status'] == 'PROPOSED')
    # 已发布报告内容未被覆盖
    s, rd2 = req('GET', f"/reports/{rep['id']}", token=admin)
    still = next(v for v in rd2['versions'] if v['id'] == pub_v['id'])
    check('S5 已发布 v3 内容未被覆盖（仍 74.94 / PUBLISHED）',
          still['status'] == 'PUBLISHED' and abs(float(still['totalScope1']) - 74.94) < 0.01,
          f"{still['status']} {still['totalScope1']}")
    s, b = req('POST', f"/impacts/restatements/{rest_m['id']}/accept", token=finance)
    check('S5 接受重述 → 冻结 v4', s == 200 and b['newVersion']['versionNo'] == 4, str(b))
    req('POST', f"/reports/{rep['id']}/approve", token=finance)
    s, b = req('POST', f"/reports/{rep['id']}/publish", token=finance)
    check('S5 v4 发布', s == 200 and b['status'] == 'PUBLISHED', str(b))
    s, rd3 = req('GET', f"/reports/{rep['id']}", token=admin)
    statuses = {v['versionNo']: v['status'] for v in rd3['versions']}
    v4 = rd3['versions'][0]
    check('S5 披露历史完整：v1-v3 已重述，v4 已发布',
          statuses.get(1) == 'RESTATED' and statuses.get(2) == 'RESTATED'
          and statuses.get(3) == 'RESTATED' and statuses.get(4) == 'PUBLISHED', str(statuses))
    check('S5 v4 范围一 = 4.02（仅剩补录柴油）',
          abs(float(v4['totalScope1']) - 4.02) < 0.01, str(v4['totalScope1']))

    # ---------- S6 测量换算与派生指标 ----------
    run_s6(admin, analyst, auditor, finance)

    # ---------- 汇总 ----------
    print('\n========================================')
    print(f'通过 {len(PASSED)} 项，失败 {len(FAILED)} 项')
    if FAILED:
        for name, detail in FAILED:
            print(f'  失败: {name} :: {detail}')
        sys.exit(1)
    print('全部验收场景回放通过 ✔')


if __name__ == '__main__':
    main()
