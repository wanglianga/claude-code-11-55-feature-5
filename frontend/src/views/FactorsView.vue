<template>
  <div class="page-card">
    <h3 class="page-title">排放因子目录（地域 × 适用期 × 审批版本）</h3>
    <div class="toolbar">
      <el-button type="primary" @click="factorDlg.visible = true">新增因子</el-button>
      <span class="hint">批准新版本会替换适用期重叠的旧版本；已被运行绑定的旧版本将触发影响评估</span>
    </div>

    <el-table :data="factors" size="small" border row-key="factor.id" v-loading="loading">
      <el-table-column type="expand">
        <template #default="{ row }">
          <el-table :data="row.versions" size="small" border style="margin: 4px 24px">
            <el-table-column label="版本" width="60">
              <template #default="{ row: v }">v{{ v.versionNo }}</template>
            </el-table-column>
            <el-table-column label="因子值" width="130">
              <template #default="{ row: v }">{{ v.value }} kgCO2e/{{ row.factor.unit }}</template>
            </el-table-column>
            <el-table-column label="适用期" width="200">
              <template #default="{ row: v }">{{ v.validFrom }} ~ {{ v.validTo || '长期' }}</template>
            </el-table-column>
            <el-table-column label="状态" width="90">
              <template #default="{ row: v }"><StatusTag kind="factor" :value="v.status" /></template>
            </el-table-column>
            <el-table-column label="创建" width="140">
              <template #default="{ row: v }">{{ v.createdBy }} · {{ fmtTime(v.createdAt) }}</template>
            </el-table-column>
            <el-table-column label="批准" width="140">
              <template #default="{ row: v }">{{ v.approvedBy ? v.approvedBy + ' · ' + fmtTime(v.approvedAt) : '-' }}</template>
            </el-table-column>
            <el-table-column label="操作" width="180">
              <template #default="{ row: v }">
                <el-button v-if="v.status === 'DRAFT'" size="small" link type="success"
                           @click="approve(v)">批准</el-button>
                <el-button v-if="v.status === 'APPROVED' || v.status === 'DRAFT'" size="small" link
                           type="danger" @click="retire(v)">停用</el-button>
                <el-button size="small" link type="primary" @click="showUsage(v)">使用追踪</el-button>
              </template>
            </el-table-column>
          </el-table>
        </template>
      </el-table-column>
      <el-table-column prop="factor.code" label="编码" width="150" />
      <el-table-column prop="factor.name" label="名称" min-width="180" />
      <el-table-column label="范围" width="80">
        <template #default="{ row }">{{ row.factor.scope }}</template>
      </el-table-column>
      <el-table-column label="能源类型" width="100">
        <template #default="{ row }">{{ store.energyLabel(row.factor.energyType) }}</template>
      </el-table-column>
      <el-table-column prop="factor.region" label="地域" width="100" />
      <el-table-column label="当前批准版本" width="200">
        <template #default="{ row }">
          <span v-if="row.currentApproved">
            v{{ row.currentApproved.versionNo }} · {{ row.currentApproved.value }} kgCO2e/{{ row.factor.unit }}
          </span>
          <el-tag v-else type="warning" size="small">无批准版本</el-tag>
        </template>
      </el-table-column>
      <el-table-column label="操作" width="110">
        <template #default="{ row }">
          <el-button size="small" link type="primary" @click="openVersionDlg(row.factor)">新增版本</el-button>
        </template>
      </el-table-column>
    </el-table>

    <el-dialog v-model="factorDlg.visible" title="新增因子" width="480px" destroy-on-close>
      <el-form label-width="90px">
        <el-form-item label="编码"><el-input v-model="factorDlg.code" placeholder="如 ELEC-CNEAST" /></el-form-item>
        <el-form-item label="名称"><el-input v-model="factorDlg.name" placeholder="如 华东电网电力排放因子" /></el-form-item>
        <el-form-item label="能源类型">
          <el-select v-model="factorDlg.energyType" style="width:100%">
            <el-option v-for="t in store.energyTypes" :key="t.code" :label="`${t.label}（${t.scope}）`" :value="t.code" />
          </el-select>
        </el-form-item>
        <el-form-item label="地域"><el-input v-model="factorDlg.region" placeholder="如 CN-EAST" /></el-form-item>
      </el-form>
      <template #footer>
        <el-button @click="factorDlg.visible = false">取消</el-button>
        <el-button type="primary" @click="createFactor">创建</el-button>
      </template>
    </el-dialog>

    <el-dialog v-model="versionDlg.visible" :title="`新增版本 - ${versionDlg.factor?.code || ''}`"
               width="480px" destroy-on-close>
      <el-form label-width="90px">
        <el-form-item label="因子值">
          <el-input-number v-model="versionDlg.value" :min="0.000001" :precision="6"
                           :controls="false" style="width:200px" />
          <span class="hint"> kgCO2e/{{ versionDlg.factor?.unit }}</span>
        </el-form-item>
        <el-form-item label="适用期起"><el-input v-model="versionDlg.validFrom" placeholder="YYYY-MM-DD" /></el-form-item>
        <el-form-item label="适用期止"><el-input v-model="versionDlg.validTo" placeholder="YYYY-MM-DD，可空" /></el-form-item>
      </el-form>
      <template #footer>
        <el-button @click="versionDlg.visible = false">取消</el-button>
        <el-button type="primary" @click="createVersion">创建草稿</el-button>
      </template>
    </el-dialog>

    <el-dialog v-model="usage.visible" title="因子版本使用追踪" width="560px">
      <template v-if="usage.data">
        <el-descriptions :column="2" border size="small">
          <el-descriptions-item label="因子">{{ usage.data.factor?.code }}</el-descriptions-item>
          <el-descriptions-item label="版本">v{{ usage.data.version?.versionNo }}</el-descriptions-item>
          <el-descriptions-item label="状态">
            <StatusTag kind="factor" :value="usage.data.version?.status" />
          </el-descriptions-item>
          <el-descriptions-item label="因子值">{{ usage.data.version?.value }}</el-descriptions-item>
        </el-descriptions>
        <h4>绑定该版本的核算运行</h4>
        <el-table :data="usage.data.runs" size="small" border>
          <el-table-column prop="runNo" label="运行编号" width="140" />
          <el-table-column prop="period" label="期间" width="80" />
          <el-table-column label="类型" width="80">
            <template #default="{ row }"><StatusTag kind="purpose" :value="row.purpose" /></template>
          </el-table-column>
          <el-table-column label="状态">
            <template #default="{ row }"><StatusTag kind="run" :value="row.status" /></template>
          </el-table-column>
        </el-table>
        <el-empty v-if="!usage.data.runs.length" description="尚无运行绑定该版本" :image-size="60" />
      </template>
    </el-dialog>
  </div>
</template>

<script setup>
import { onMounted, reactive, ref } from 'vue'
import { ElMessage, ElMessageBox } from 'element-plus'
import api from '../api'
import { store, fmtTime } from '../store'
import StatusTag from '../components/StatusTag.vue'

const factors = ref([])
const loading = ref(false)
const factorDlg = reactive({ visible: false, code: '', name: '', energyType: '', region: '' })
const versionDlg = reactive({ visible: false, factor: null, value: 1, validFrom: '', validTo: '' })
const usage = reactive({ visible: false, data: null })

async function load() {
  loading.value = true
  try {
    const resp = await api.get('/factors')
    factors.value = resp.data
  } finally {
    loading.value = false
  }
}

async function createFactor() {
  await api.post('/factors', {
    code: factorDlg.code, name: factorDlg.name,
    energyType: factorDlg.energyType, region: factorDlg.region
  })
  ElMessage.success('因子已创建')
  factorDlg.visible = false
  load()
}

function openVersionDlg(factor) {
  Object.assign(versionDlg, { visible: true, factor, value: 1, validFrom: '', validTo: '' })
}

async function createVersion() {
  await api.post(`/factors/${versionDlg.factor.id}/versions`, {
    value: versionDlg.value, validFrom: versionDlg.validFrom,
    validTo: versionDlg.validTo || null
  })
  ElMessage.success('版本草稿已创建，待批准')
  versionDlg.visible = false
  load()
}

async function approve(v) {
  await ElMessageBox.confirm(
    `批准 v${v.versionNo} 将替换适用期重叠的旧批准版本；若旧版本已被运行绑定，将自动生成影响评估。继续？`,
    '批准因子版本', { type: 'warning' })
  await api.post(`/factors/versions/${v.id}/approve`)
  ElMessage.success('已批准')
  load()
}

async function retire(v) {
  await ElMessageBox.confirm(`确认停用 v${v.versionNo}？`, '停用版本', { type: 'warning' })
  await api.post(`/factors/versions/${v.id}/retire`)
  ElMessage.success('已停用')
  load()
}

async function showUsage(v) {
  const resp = await api.get(`/factors/versions/${v.id}/usage`)
  usage.data = resp.data
  usage.visible = true
}

onMounted(load)
</script>

<style scoped>
.hint { font-size: 12px; color: #909399; }
h4 { margin: 14px 0 8px; }
</style>
