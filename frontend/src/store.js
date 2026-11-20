import { reactive } from 'vue'
import api from './api'

/** 全局状态：登录用户 + 元数据缓存 + 状态文案映射 */
export const store = reactive({
  token: localStorage.getItem('token') || '',
  user: JSON.parse(localStorage.getItem('user') || 'null'),
  plants: [],
  lines: [],
  energyTypes: [],

  setAuth(token, user) {
    this.token = token
    this.user = user
    localStorage.setItem('token', token)
    localStorage.setItem('user', JSON.stringify(user))
  },
  logout() {
    this.token = ''
    this.user = null
    localStorage.removeItem('token')
    localStorage.removeItem('user')
  },
  async loadMeta() {
    if (this.plants.length) return
    const [plants, lines, types] = await Promise.all([
      api.get('/meta/plants'),
      api.get('/meta/product-lines'),
      api.get('/meta/energy-types')
    ])
    this.plants = plants.data
    this.lines = lines.data
    this.energyTypes = types.data
  },
  plantName(id) {
    if (id == null) return '-'
    const p = this.plants.find(x => x.id === id)
    return p ? p.name : `#${id}`
  },
  lineName(id) {
    if (id == null) return '未分摊'
    const l = this.lines.find(x => x.id === id)
    return l ? l.name : `#${id}`
  },
  energyLabel(code) {
    const t = this.energyTypes.find(x => x.code === code)
    return t ? t.label : code
  }
})

export const STATUS_MAPS = {
  batch: {
    UPLOADED: ['已上传', 'info'], VERIFIED: ['已核验', 'success'], REJECTED: ['已驳回', 'danger'],
    CONFLICT: ['冲突', 'warning'], SUPERSEDED: ['被取代', 'info'], VOID: ['已作废', 'danger']
  },
  factor: {
    DRAFT: ['草稿', 'info'], APPROVED: ['已批准', 'success'],
    REPLACED: ['被替换', 'warning'], RETIRED: ['已停用', 'info']
  },
  run: {
    RUNNING: ['运行中', 'warning'], COMPLETED: ['已完成', 'success'], FAILED: ['失败', 'danger']
  },
  result: {
    OK: ['正常', 'success'], PENDING_FACTOR: ['待核(缺因子)', 'warning'],
    PENDING_CONVERSION: ['待核(换算未闭合)', 'danger']
  },
  report: {
    DRAFT: ['草稿', 'info'], PENDING_REVIEW: ['待复核', 'warning'],
    RECONCILE_FAILED: ['待核(勾稽不平)', 'danger'], APPROVED: ['已审批', 'success'],
    PUBLISHED: ['已发布', 'success']
  },
  version: {
    FROZEN: ['已冻结', 'info'], PUBLISHED: ['已发布', 'success'],
    SUPERSEDED: ['被取代', 'info'], RESTATED: ['已重述', 'warning']
  },
  reconcile: {
    OK: ['勾稽一致', 'success'], MISMATCH: ['勾稽差异', 'danger'], PENDING_FACTOR: ['含待核指标', 'warning']
  },
  trigger: {
    SOURCE_SUPPLEMENT: ['来源补录', 'primary'], SOURCE_REVISION: ['来源修订', 'primary'],
    SOURCE_MISSING: ['来源缺失', 'danger'], FACTOR_REVISION: ['因子修订', 'warning'],
    CONVERSION_REVISION: ['换算依据修订', 'warning']
  },
  impact: {
    OPEN: ['待处理', 'warning'], ACKNOWLEDGED: ['已受理', 'info'], RESOLVED: ['已解决', 'success']
  },
  restatement: {
    PROPOSED: ['待处理', 'warning'], ACCEPTED: ['已接受', 'info'],
    REJECTED: ['已驳回', 'danger'], RESOLVED: ['已重述', 'success']
  },
  purpose: {
    OFFICIAL: ['正式', 'primary'], CANDIDATE: ['候选', 'info']
  },
  conversion: {
    CONVERTIBLE: ['可直接转换', 'success'],
    MANUAL_BRIDGE: ['需人工桥接', 'warning'],
    DIMENSION_INCOMPATIBLE: ['量纲不相容', 'danger'],
    INSUFFICIENT_EVIDENCE: ['证据不足', 'danger']
  },
  metric: {
    OK: ['正常', 'success'], PENDING_CONVERSION: ['换算待核', 'danger'],
    PENDING_FACTOR: ['因子待核', 'warning'], EMPTY_DENOMINATOR: ['分母为空', 'info']
  },
  trace: {
    TRACEABLE: ['可追溯', 'success'], PENDING: ['待核', 'warning'],
    EMPTY_DENOMINATOR: ['分母为空', 'info'], CONFLICT: ['冲突保留', 'danger']
  },
  evidence: {
    CALIBRATION: ['校准证书', 'primary'], LAB_ASSAY: ['化验报告', 'success'],
    DENSITY_REVISION: ['密度修订', 'warning'], MANUAL_BRIDGE: ['人工桥接', 'info']
  },
  edge: {
    UNIT_PREFIX: ['单位前缀', 'info'], BASIS_SWITCH: ['基准切换', 'warning'],
    PROPERTY: ['物性换算', 'primary'], COMPOUND: ['复合单位', 'success']
  },
  base: {
    DRAFT: ['草稿', 'info'], APPROVED: ['已批准', 'success'], SUPERSEDED: ['已取代', 'warning']
  }
}

export function fmtNum(v, digits = 3) {
  if (v == null) return '-'
  return Number(v).toLocaleString('zh-CN', { maximumFractionDigits: digits })
}

export function fmtTime(v) {
  if (!v) return '-'
  return String(v).replace('T', ' ').substring(0, 19)
}
