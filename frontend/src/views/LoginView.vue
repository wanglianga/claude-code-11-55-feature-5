<template>
  <div class="login-wrap">
    <el-card class="login-card">
      <h2>碳排放披露数据血缘<br />与版本影响管理平台</h2>
      <el-form @submit.prevent="doLogin">
        <el-form-item>
          <el-input v-model="username" placeholder="用户名" size="large" autofocus />
        </el-form-item>
        <el-form-item>
          <el-input v-model="password" type="password" placeholder="密码" size="large"
                    show-password @keyup.enter="doLogin" />
        </el-form-item>
        <el-button type="primary" size="large" style="width:100%" :loading="loading" @click="doLogin">
          登 录
        </el-button>
      </el-form>
      <el-divider>测试账号</el-divider>
      <div class="accounts">
        <div v-for="a in accounts" :key="a.u" @click="fill(a)">
          <el-tag size="small" effect="plain">{{ a.role }}</el-tag> {{ a.u }} / {{ a.p }}
        </div>
      </div>
    </el-card>
  </div>
</template>

<script setup>
import { ref } from 'vue'
import { useRouter } from 'vue-router'
import { ElMessage } from 'element-plus'
import api from '../api'
import { store } from '../store'

const router = useRouter()
const username = ref('')
const password = ref('')
const loading = ref(false)
const accounts = [
  { u: 'admin', p: 'admin123', role: '管理员' },
  { u: 'analyst', p: 'analyst123', role: '分析员' },
  { u: 'auditor', p: 'auditor123', role: '审核员' },
  { u: 'finance', p: 'finance123', role: '财务披露' }
]

function fill(a) {
  username.value = a.u
  password.value = a.p
}

async function doLogin() {
  if (!username.value || !password.value) {
    ElMessage.warning('请输入用户名和密码')
    return
  }
  loading.value = true
  try {
    const resp = await api.post('/auth/login', { username: username.value, password: password.value })
    store.setAuth(resp.data.token, resp.data.user)
    await store.loadMeta()
    router.push('/')
  } catch (e) {
    // 拦截器已提示
  } finally {
    loading.value = false
  }
}
</script>

<style scoped>
.login-wrap { height: 100vh; display: flex; align-items: center; justify-content: center;
  background: linear-gradient(135deg, #0f2b46 0%, #1a5276 100%); }
.login-card { width: 420px; }
h2 { text-align: center; color: #0f2b46; font-size: 20px; line-height: 1.5; margin-top: 6px; }
.accounts { display: flex; flex-direction: column; gap: 6px; font-size: 13px; color: #606266; }
.accounts > div { cursor: pointer; padding: 4px 8px; border-radius: 4px; }
.accounts > div:hover { background: #f0f5fa; }
</style>
