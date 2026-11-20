<template>
  <div class="page-card">
    <h3 class="page-title">来源批次（活动数据上传与核验）</h3>
    <div class="toolbar">
      <el-input v-model="filters.period" placeholder="期间 YYYY-MM" style="width:130px" clearable @change="load" />
      <el-select v-model="filters.status" placeholder="状态" style="width:130px" clearable @change="load">
        <el-option v-for="(v, k) in batchStatuses" :key="k" :label="v[0]" :value="k" />
      </el-select>
      <el-select v-model="filters.category" placeholder="类别" style="width:130px" clearable @change="load">
        <el-option label="燃料" value="FUEL" /><el-option label="电力" value="ELECTRICITY" />
        <el-option label="采购" value="PROCUREMENT" />
      </el-select>
      <el-button @click="load">查询</el-button>
      <el-button type="primary" @click="openUpload">上传批次</el-button>
    </div>

    <el-table :data="batches" size="small" border v-loading="loading">
      <el-table-column prop="batchNo" label="批次号" width="170" />
      <el-table-column label="工厂" width="100">
        <template #default="{ row }">{{ store.plantName(row.plantId) }}</template>
      </el-table-column>
      <el-table-column label="类别" width="80">
        <template #default="{ row }">{{ categoryLabel(row.category) }}</template>
      </el-table-column>
      <el-table-column prop="period" label="期间" width="80" />
      <el-table-column prop="rowCount" label="行数" width="60" />
      <el-table-column label="状态" width="110">
        <template #default="{ row }"><StatusTag kind="batch" :value="row.status" /></template>
      </el-table-column>
      <el-table-column prop="uploadedBy" label="上传人" width="80" />
      <el-table-column label="上传时间" width="150">
        <template #default="{ row }">{{ fmtTime(row.uploadedAt) }}</template>
      </el-table-column>
      <el-table-column prop="verifiedBy" label="核验人" width="80" />
      <el-table-column label="操作" width="260" fixed="right">
        <template #default="{ row }">
          <el-button size="small" link type="primary" @click="openDetail(row)">详情</el-button>
          <el-button v-if="row.status === 'UPLOADED'" size="small" link type="success"
                     @click="verify(row)">核验</el-button>
          <el-button v-if="row.status === 'UPLOADED'" size="small" link type="danger"
                     @click="reject(row)">驳回</el-button>
          <el-button v-if="row.status === 'VERIFIED'" size="small" link type="warning"
                     @click="openRevise(row)">修订</el-button>
          <el-button v-if="row.status === 'VERIFIED'" size="small" link type="danger"
                     @click="openVoid(row)">作废</el-button>
        </template>
      </el-table-column>
    </el-table>

    <!-- 上传 / 修订对话框 -->
    <el-dialog v-model="dialog.visible" :title="dialog.title" width="780px" destroy-on-close>
      <el-form label-width="90px">
        <el-form-item label="工厂" v-if="!dialog.revising">
          <el-select v-model="dialog.plantId" style="width:220px">
            <el-option v-for="p in store.plants" :key="p.id" :label="p.name" :value="p.id" />
          </el-select>
        </el-form-item>
        <el-form-item label="类别" v-if="!dialog.revising">
          <el-select v-model="dialog.category" style="width:220px" @change="dialog.rows = [newRow()]">
            <el-option label="燃料（范围一）" value="FUEL" />
            <el-option label="电力（范围二）" value="ELECTRICITY" />
            <el-option label="采购（范围三）" value="PROCUREMENT" />
          </el-select>
        </el-form-item>
        <el-form-item label="期间" v-if="!dialog.revising">
          <el-input v-model="dialog.period" placeholder="YYYY-MM，如 2025-06" style="width:220px" />
        </el-form-item>
        <el-form-item label="数据行">
          <el-table :data="dialog.rows" size="small" border style="width:100%">
            <el-table-column label="工厂" width="110" v-if="dialog.category === 'PROCUREMENT'">
              <template #default="{ row }">
                <el-select v-model="row.plantId" placeholder="归属工厂" clearable>
                  <el-option v-for="p in store.plants" :key="p.id" :label="p.name" :value="p.id" />
                </el-select>
              </template>
            </el-table-column>
            <el-table-column label="产品线" width="120">
              <template #default="{ row }">
                <el-select v-model="row.productLineId" placeholder="未分摊" clearable>
                  <el-option v-for="l in store.lines" :key="l.id" :label="l.name" :value="l.id" />
                </el-select>
              </template>
            </el-table-column>
            <el-table-column label="能源类型" width="140">
              <template #default="{ row }">
                <el-select v-model="row.energyType" placeholder="选择">
                  <el-option v-for="t in energyOptions" :key="t.code" :label="t.label" :value="t.code" />
                </el-select>
              </template>
            </el-table-column>
            <el-table-column label="数量" width="150">
              <template #default="{ row }">
                <el-input-number v-model="row.quantity" :min="0.001" :precision="3"
                                 :controls="false" style="width:130px" />
              </template>
            </el-table-column>
            <el-table-column label="原始单位" width="110">
              <template #default="{ row }">
                <el-input v-model="row.unit" placeholder="空=规范单位" />
              </template>
            </el-table-column>
            <el-table-column label="计量基准" width="110">
              <template #default="{ row }">
                <el-select v-model="row.basis" placeholder="未声明" clearable size="small">
                  <el-option label="湿基 WET" value="WET" />
                  <el-option label="干基 DRY" value="DRY" />
                  <el-option label="标况 STD" value="STD" />
                  <el-option label="工况 OP" value="OP" />
                  <el-option label="低位热值 LHV" value="LHV" />
                </el-select>
              </template>
            </el-table-column>
            <el-table-column label="供应商凭证" v-if="dialog.category === 'PROCUREMENT'">
              <template #default="{ row }">
                <el-input v-model="row.voucherNo" placeholder="如 V-1001" />
              </template>
            </el-table-column>
            <el-table-column width="60">
              <template #default="{ $index }">
                <el-button link type="danger" size="small" @click="dialog.rows.splice($index, 1)">删</el-button>
              </template>
            </el-table-column>
          </el-table>
        </el-form-item>
        <el-form-item>
          <el-button size="small" @click="dialog.rows.push(newRow())">+ 添加行</el-button>
        </el-form-item>
      </el-form>
      <template #footer>
        <el-button @click="dialog.visible = false">取消</el-button>
        <el-button type="primary" :loading="dialog.saving" @click="submitDialog">提交</el-button>
      </template>
    </el-dialog>

    <!-- 详情抽屉 -->
    <el-drawer v-model="detail.visible" :title="`批次详情 ${detail.batch?.batchNo || ''}`" size="640px">
      <template v-if="detail.batch">
        <el-descriptions :column="2" border size="small">
          <el-descriptions-item label="状态">
            <StatusTag kind="batch" :value="detail.batch.status" />
          </el-descriptions-item>
          <el-descriptions-item label="修订号">R{{ detail.batch.revisionNo }}</el-descriptions-item>
          <el-descriptions-item label="上传">{{ detail.batch.uploadedBy }} · {{ fmtTime(detail.batch.uploadedAt) }}</el-descriptions-item>
          <el-descriptions-item label="核验">{{ detail.batch.verifiedBy || '-' }} · {{ fmtTime(detail.batch.verifiedAt) }}</el-descriptions-item>
          <el-descriptions-item label="作废/驳回原因" :span="2">{{ detail.batch.voidReason || '-' }}</el-descriptions-item>
        </el-descriptions>
        <h4>数据行</h4>
        <el-table :data="detail.rows" size="small" border>
          <el-table-column prop="rowNo" label="#" width="45" />
          <el-table-column label="工厂" width="90">
            <template #default="{ row }">{{ store.plantName(row.plantId) }}</template>
          </el-table-column>
          <el-table-column label="产品线" width="90">
            <template #default="{ row }">{{ store.lineName(row.productLineId) }}</template>
          </el-table-column>
          <el-table-column label="能源" width="90">
            <template #default="{ row }">{{ store.energyLabel(row.energyType) }}</template>
          </el-table-column>
          <el-table-column label="数量" width="120">
            <template #default="{ row }">{{ fmtNum(row.quantity) }} {{ row.unit }}</template>
          </el-table-column>
          <el-table-column label="计量基准" width="84">
            <template #default="{ row }">{{ row.basis || '未声明' }}</template>
          </el-table-column>
          <el-table-column prop="voucherNo" label="凭证" width="90">
            <template #default="{ row }">{{ row.voucherNo || '-' }}</template>
          </el-table-column>
          <el-table-column label="已核算" width="70">
            <template #default="{ row }">
              <el-tag v-if="detail.consumed.includes(row.id)" type="warning" size="small">已消耗</el-tag>
              <span v-else>-</span>
            </template>
          </el-table-column>
        </el-table>
        <template v-if="detail.revisions.length">
          <h4>修订历史</h4>
          <el-table :data="detail.revisions" size="small" border>
            <el-table-column prop="batchNo" label="批次号" width="170" />
            <el-table-column label="状态" width="90">
              <template #default="{ row }"><StatusTag kind="batch" :value="row.status" /></template>
            </el-table-column>
            <el-table-column prop="uploadedBy" label="提交人" width="80" />
            <el-table-column label="时间">
              <template #default="{ row }">{{ fmtTime(row.uploadedAt) }}</template>
            </el-table-column>
          </el-table>
        </template>
      </template>
    </el-drawer>

    <!-- 作废对话框 -->
    <el-dialog v-model="voidDlg.visible" title="作废批次（来源缺失）" width="460px">
      <el-alert type="warning" :closable="false" show-icon
                title="若批次数据已被核算消耗，作废将触发影响评估并对已发布报告生成重述建议" />
      <el-input v-model="voidDlg.reason" type="textarea" rows="3" placeholder="作废原因（必填）"
                style="margin-top:12px" />
      <template #footer>
        <el-button @click="voidDlg.visible = false">取消</el-button>
        <el-button type="danger" :loading="voidDlg.saving" @click="doVoid">确认作废</el-button>
      </template>
    </el-dialog>
  </div>
</template>

<script setup>
import { computed, onMounted, reactive, ref } from 'vue'
import { ElMessage, ElMessageBox } from 'element-plus'
import api from '../api'
import { store, fmtNum, fmtTime, STATUS_MAPS } from '../store'
import StatusTag from '../components/StatusTag.vue'

const batches = ref([])
const loading = ref(false)
const filters = reactive({ period: '', status: '', category: '' })
const batchStatuses = STATUS_MAPS.batch

const dialog = reactive({
  visible: false, revising: false, saving: false, title: '',
  batchId: null, plantId: null, category: 'FUEL', period: '', rows: []
})
const detail = reactive({ visible: false, batch: null, rows: [], consumed: [], revisions: [] })
const voidDlg = reactive({ visible: false, saving: false, batch: null, reason: '' })

const energyOptions = computed(() =>
  store.energyTypes.filter(t => t.category === dialog.category))

function categoryLabel(c) {
  return { FUEL: '燃料', ELECTRICITY: '电力', PROCUREMENT: '采购' }[c] || c
}

function newRow() {
  return { plantId: null, productLineId: null, energyType: null, quantity: 1,
           voucherNo: '', unit: '', basis: null }
}

async function load() {
  loading.value = true
  try {
    const params = {}
    if (filters.period) params.period = filters.period
    if (filters.status) params.status = filters.status
    if (filters.category) params.category = filters.category
    const resp = await api.get('/batches', { params })
    batches.value = resp.data
  } finally {
    loading.value = false
  }
}

function openUpload() {
  Object.assign(dialog, {
    visible: true, revising: false, saving: false, title: '上传来源批次',
    batchId: null, plantId: store.plants[0]?.id, category: 'FUEL', period: '', rows: [newRow()]
  })
}

async function openRevise(row) {
  const resp = await api.get(`/batches/${row.id}`)
  const rows = resp.data.rows.map(r => ({
    plantId: r.plantId, productLineId: r.productLineId, energyType: r.energyType,
    quantity: Number(r.quantity), voucherNo: r.voucherNo || '',
    unit: r.unit || '', basis: r.basis || null
  }))
  Object.assign(dialog, {
    visible: true, revising: true, saving: false,
    title: `修订批次 ${row.batchNo}（${categoryLabel(row.category)} · ${row.period}）`,
    batchId: row.id, plantId: row.plantId, category: row.category, period: row.period,
    rows: rows.length ? rows : [newRow()]
  })
}

async function submitDialog() {
  dialog.saving = true
  try {
    const payload = {
      plantId: dialog.plantId, category: dialog.category, period: dialog.period,
      rows: dialog.rows.map(r => ({
        plantId: r.plantId, productLineId: r.productLineId, energyType: r.energyType,
        quantity: r.quantity, voucherNo: r.voucherNo || null,
        unit: r.unit || null, basis: r.basis || null
      }))
    }
    if (dialog.revising) {
      await api.post(`/batches/${dialog.batchId}/revise`, payload)
      ElMessage.success('修订已提交，待核验')
    } else {
      await api.post('/batches', payload)
      ElMessage.success('批次已上传，待核验')
    }
    dialog.visible = false
    load()
  } catch (e) {
    // 拦截器已提示（如并发修订冲突 409）
  } finally {
    dialog.saving = false
  }
}

async function openDetail(row) {
  const resp = await api.get(`/batches/${row.id}`)
  detail.batch = resp.data.batch
  detail.rows = resp.data.rows
  detail.consumed = resp.data.consumedRowIds
  detail.revisions = resp.data.revisions
  detail.visible = true
}

async function verify(row) {
  await ElMessageBox.confirm(`确认核验批次 ${row.batchNo}？核验后数据进入核算范围。`, '核验确认')
  await api.post(`/batches/${row.id}/verify`)
  ElMessage.success('已核验')
  load()
}

async function reject(row) {
  const { value } = await ElMessageBox.prompt('驳回原因', '驳回批次', { inputValue: '' })
  await api.post(`/batches/${row.id}/reject`, { reason: value || '' })
  ElMessage.success('已驳回')
  load()
}

function openVoid(row) {
  Object.assign(voidDlg, { visible: true, saving: false, batch: row, reason: '' })
}

async function doVoid() {
  if (!voidDlg.reason.trim()) {
    ElMessage.warning('请填写作废原因')
    return
  }
  voidDlg.saving = true
  try {
    await api.post(`/batches/${voidDlg.batch.id}/void`, { reason: voidDlg.reason })
    ElMessage.success('已作废；若数据已被核算消耗，影响评估与重述建议已生成')
    voidDlg.visible = false
    load()
  } finally {
    voidDlg.saving = false
  }
}

onMounted(load)
</script>

<style scoped>
h4 { margin: 16px 0 8px; }
</style>
