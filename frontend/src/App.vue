<template>
  <router-view v-if="!store.user" />
  <el-container v-else class="layout">
    <el-aside width="220px" class="aside">
      <div class="logo">碳披露血缘平台</div>
      <el-menu :default-active="$route.path" router background-color="#0f2b46"
               text-color="#b8c7d9" active-text-color="#ffffff">
        <el-menu-item index="/"><el-icon><DataAnalysis /></el-icon>仪表盘</el-menu-item>
        <el-menu-item index="/sources"><el-icon><Upload /></el-icon>来源批次</el-menu-item>
        <el-menu-item index="/factors"><el-icon><Collection /></el-icon>因子目录</el-menu-item>
        <el-menu-item index="/runs"><el-icon><VideoPlay /></el-icon>核算运行</el-menu-item>
        <el-menu-item index="/conversion"><el-icon><Switch /></el-icon>测量换算</el-menu-item>
        <el-menu-item index="/impacts"><el-icon><Warning /></el-icon>影响评估</el-menu-item>
        <el-menu-item index="/reports"><el-icon><Document /></el-icon>披露报告</el-menu-item>
      </el-menu>
    </el-aside>
    <el-container>
      <el-header class="header">
        <span class="title">制造集团碳排放披露数据血缘与版本影响管理平台</span>
        <div class="user">
          <el-tag size="small" effect="plain">{{ roleLabel }}</el-tag>
          <span>{{ store.user.displayName }}（{{ store.user.username }}）</span>
          <el-button size="small" @click="logout">退出</el-button>
        </div>
      </el-header>
      <el-main class="main">
        <router-view />
      </el-main>
    </el-container>
  </el-container>
</template>

<script setup>
import { computed, onMounted } from 'vue'
import { useRouter } from 'vue-router'
import { DataAnalysis, Upload, Collection, VideoPlay, Warning, Document, Switch } from '@element-plus/icons-vue'
import { store } from './store'

const router = useRouter()
const roleLabel = computed(() => ({
  ADMIN: '管理员', ANALYST: '分析员', AUDITOR: '审核员', FINANCE: '财务披露'
}[store.user?.role] || store.user?.role))

function logout() {
  store.logout()
  router.push('/login')
}

onMounted(() => { if (store.user) store.loadMeta() })
</script>

<style>
body { margin: 0; font-family: 'Helvetica Neue', Arial, 'PingFang SC', 'Microsoft YaHei', sans-serif; }
.layout { height: 100vh; }
.aside { background: #0f2b46; }
.logo { color: #fff; font-weight: 600; padding: 18px 16px; font-size: 16px; }
.aside .el-menu { border-right: none; }
.header { display: flex; align-items: center; justify-content: space-between;
  border-bottom: 1px solid #e4e7ed; background: #fff; }
.header .title { font-size: 15px; color: #303133; font-weight: 600; }
.header .user { display: flex; align-items: center; gap: 10px; font-size: 13px; color: #606266; }
.main { background: #f5f7fa; }
.page-card { background: #fff; border-radius: 6px; padding: 16px 20px; margin-bottom: 14px;
  box-shadow: 0 1px 3px rgba(0,0,0,0.06); }
.page-title { font-size: 16px; font-weight: 600; margin: 0 0 12px; color: #303133; }
.toolbar { display: flex; gap: 10px; align-items: center; margin-bottom: 12px; flex-wrap: wrap; }
.diff-pos { color: #d03050; font-weight: 600; }
.diff-neg { color: #18a058; font-weight: 600; }
</style>
