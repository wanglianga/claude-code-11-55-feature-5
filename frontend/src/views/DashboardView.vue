<template>
  <div>
    <div class="stat-row" v-if="data">
      <el-card class="stat" shadow="never">
        <div class="label">最新发布披露总量</div>
        <div class="value">{{ data.latestPublished ? fmtNum(data.latestPublished.totalTco2e, 2) : '-' }}
          <span class="unit">tCO2e</span></div>
        <div class="sub" v-if="data.latestPublished">
          {{ data.latestPublished.reportTitle }} · v{{ data.latestPublished.versionNo }} · {{ data.latestPublished.period }}
        </div>
      </el-card>
      <el-card class="stat" shadow="never" v-if="data.latestPublished">
        <div class="label">范围一 / 范围二 / 范围三</div>
        <div class="scopes">
          <span>{{ fmtNum(data.latestPublished.totalScope1, 2) }}</span>/
          <span>{{ fmtNum(data.latestPublished.totalScope2, 2) }}</span>/
          <span>{{ fmtNum(data.latestPublished.totalScope3, 2) }}</span>
        </div>
        <div class="sub">tCO2e（披露口径）</div>
      </el-card>
      <el-card class="stat" shadow="never">
        <div class="label">待处理影响评估</div>
        <div class="value warn">{{ data.counts.openImpacts }}</div>
        <div class="sub">来源/因子变化触发</div>
      </el-card>
      <el-card class="stat" shadow="never">
        <div class="label">待核报告 / 待核指标</div>
        <div class="value danger">{{ data.counts.reconcileFailedReports }} / {{ data.counts.pendingFactorResults }}</div>
        <div class="sub">勾稽不平或缺少适用因子</div>
      </el-card>
    </div>

    <div class="grid">
      <div class="page-card" v-if="data && data.latestPublished">
        <h3 class="page-title">披露口径 · 分工厂排放（tCO2e）</h3>
        <div class="bars">
          <div v-for="p in data.latestPublished.byPlant" :key="p.plantId" class="bar-row">
            <span class="bar-label">{{ p.plantName }}</span>
            <div class="bar-track">
              <div class="bar-fill" :style="{ width: barWidth(p.value) }"></div>
            </div>
            <span class="bar-value">{{ fmtNum(p.value, 2) }}</span>
          </div>
        </div>
      </div>

      <div class="page-card">
        <h3 class="page-title">最近核算运行</h3>
        <el-table :data="data?.recentRuns || []" size="small">
          <el-table-column prop="runNo" label="运行编号" width="130" />
          <el-table-column prop="period" label="期间" width="80" />
          <el-table-column label="类型" width="70">
            <template #default="{ row }"><StatusTag kind="purpose" :value="row.purpose" /></template>
          </el-table-column>
          <el-table-column label="状态" width="90">
            <template #default="{ row }"><StatusTag kind="run" :value="row.status" /></template>
          </el-table-column>
          <el-table-column label="完成时间">
            <template #default="{ row }">{{ fmtTime(row.completedAt) }}</template>
          </el-table-column>
        </el-table>
      </div>

      <div class="page-card">
        <h3 class="page-title">最近影响评估</h3>
        <el-table :data="data?.recentImpacts || []" size="small">
          <el-table-column label="触发" width="100">
            <template #default="{ row }"><StatusTag kind="trigger" :value="row.triggerType" /></template>
          </el-table-column>
          <el-table-column prop="period" label="期间" width="80" />
          <el-table-column prop="summary" label="摘要" show-overflow-tooltip />
          <el-table-column label="状态" width="90">
            <template #default="{ row }"><StatusTag kind="impact" :value="row.status" /></template>
          </el-table-column>
        </el-table>
      </div>
    </div>
  </div>
</template>

<script setup>
import { onMounted, ref } from 'vue'
import api from '../api'
import { fmtNum, fmtTime } from '../store'
import StatusTag from '../components/StatusTag.vue'

const data = ref(null)
const maxPlant = ref(1)

function barWidth(v) {
  return Math.max(2, (Number(v) / maxPlant.value) * 100) + '%'
}

onMounted(async () => {
  const resp = await api.get('/dashboard/summary')
  data.value = resp.data
  const vals = (resp.data.latestPublished?.byPlant || []).map(p => Number(p.value))
  if (vals.length) maxPlant.value = Math.max(...vals)
})
</script>

<style scoped>
.stat-row { display: grid; grid-template-columns: repeat(4, 1fr); gap: 14px; margin-bottom: 14px; }
.stat .label { font-size: 13px; color: #909399; }
.stat .value { font-size: 26px; font-weight: 700; color: #0f2b46; margin: 6px 0 2px; }
.stat .value.warn { color: #e6a23c; }
.stat .value.danger { color: #f56c6c; }
.stat .unit { font-size: 12px; color: #909399; font-weight: 400; }
.stat .sub { font-size: 12px; color: #909399; }
.stat .scopes { font-size: 18px; font-weight: 600; color: #0f2b46; margin: 10px 0 4px; display: flex; gap: 6px; }
.grid { display: grid; grid-template-columns: 1fr; gap: 0; }
.bars { padding: 8px 0; }
.bar-row { display: flex; align-items: center; gap: 10px; margin: 10px 0; }
.bar-label { width: 90px; font-size: 13px; color: #606266; text-align: right; }
.bar-track { flex: 1; background: #eef2f7; border-radius: 4px; height: 20px; }
.bar-fill { background: linear-gradient(90deg, #1a5276, #2e86c1); height: 20px; border-radius: 4px; }
.bar-value { width: 110px; font-size: 13px; font-weight: 600; color: #303133; }
</style>
