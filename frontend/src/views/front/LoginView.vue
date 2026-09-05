<script setup>
import { reactive, ref } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import { ElMessage } from 'element-plus'
import { Lock, User } from '@element-plus/icons-vue'
import { authApi } from '../../services/api'
import { useAuthStore } from '../../stores/auth'

const router = useRouter()
const route = useRoute()
const authStore = useAuthStore()
const loginTab = ref(route.query.tab === 'admin' ? 'admin' : 'user')
const userLoading = ref(false)
const adminLoading = ref(false)

const loginForm = reactive({
  account: '',
  password: '',
})

const adminForm = reactive({
  account: 'admin',
  password: 'admin123456',
})

function loginRedirect(defaultPath) {
  const redirect = String(route.query.redirect || '')
  return redirect.startsWith('/') && !redirect.startsWith('//') ? redirect : defaultPath
}

function userRedirect() {
  const target = loginRedirect('/profile')
  return target.startsWith('/admin') ? '/profile' : target
}

function adminRedirect() {
  const target = loginRedirect('/admin')
  return target.startsWith('/admin') ? target : '/admin'
}

async function loginByPassword() {
  if (!loginForm.account.trim() || !loginForm.password.trim()) {
    ElMessage.warning('请输入学号/工号和密码')
    return
  }

  userLoading.value = true
  try {
    const response = await authApi.login({
      account: loginForm.account.trim(),
      password: loginForm.password,
    })
    authStore.login({
      role: 'user',
      account: loginForm.account.trim(),
      accessToken: response.data.accessToken,
      user: response.data.user,
    })
    ElMessage.success('登录成功')
    router.push(userRedirect())
  } catch (error) {
    ElMessage.error(error.message || '登录失败')
  } finally {
    userLoading.value = false
  }
}

async function adminLogin() {
  if (!adminForm.account.trim() || !adminForm.password.trim()) {
    ElMessage.warning('请输入管理员账号和密码')
    return
  }

  adminLoading.value = true
  try {
    const response = await authApi.adminLogin({
      account: adminForm.account.trim(),
      password: adminForm.password,
    })
    authStore.login({
      role: 'admin',
      account: adminForm.account.trim(),
      accessToken: response.data.accessToken,
      admin: response.data.admin,
    })
    ElMessage.success('管理员登录成功')
    router.push(adminRedirect())
  } catch (error) {
    ElMessage.error(error.message || '管理员账号或密码错误')
  } finally {
    adminLoading.value = false
  }
}
</script>

<template>
  <main class="page-wrap auth-page simple-auth-page">
    <section class="auth-layout auth-layout-single">
      <el-card class="auth-card simple-auth-card" shadow="never">
        <div class="simple-auth-title">
          <p>Campus Trade Agent</p>
          <h1>登录</h1>
        </div>

        <el-tabs v-model="loginTab" stretch class="simple-auth-tabs">
          <el-tab-pane label="普通用户" name="user">
            <el-form label-position="top" class="simple-auth-form">
              <el-form-item label="学号/工号">
                <el-input
                  v-model="loginForm.account"
                  :prefix-icon="User"
                  placeholder="张益达 / ZYD2026001"
                  size="large"
                />
              </el-form-item>
              <el-form-item label="密码">
                <el-input
                  v-model="loginForm.password"
                  :prefix-icon="Lock"
                  placeholder="请输入密码"
                  show-password
                  size="large"
                  @keyup.enter="loginByPassword"
                />
              </el-form-item>
              <el-button
                type="primary"
                size="large"
                class="full-button"
                :loading="userLoading"
                @click="loginByPassword"
              >
                登录
              </el-button>
            </el-form>
          </el-tab-pane>

          <el-tab-pane label="管理员" name="admin">
            <el-alert
              title="演示账号：admin / admin123456"
              type="warning"
              show-icon
              :closable="false"
            />
            <el-form class="simple-auth-form admin-form" label-position="top">
              <el-form-item label="管理员账号">
                <el-input v-model="adminForm.account" size="large" />
              </el-form-item>
              <el-form-item label="管理员密码">
                <el-input v-model="adminForm.password" show-password size="large" @keyup.enter="adminLogin" />
              </el-form-item>
              <el-button
                type="primary"
                size="large"
                class="full-button"
                :loading="adminLoading"
                @click="adminLogin"
              >
                进入后台
              </el-button>
            </el-form>
          </el-tab-pane>
        </el-tabs>

        <div class="auth-switch-line">
          <span>还没账号？</span>
          <RouterLink to="/register">前往注册</RouterLink>
        </div>
      </el-card>
    </section>
  </main>
</template>
