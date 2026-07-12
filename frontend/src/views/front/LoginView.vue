<script setup>
import { reactive, ref } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import { ElMessage } from 'element-plus'
import { Lock, Message, School, User } from '@element-plus/icons-vue'
import { authApi } from '../../services/api'
import { useAuthStore } from '../../stores/auth'

const router = useRouter()
const route = useRoute()
const authStore = useAuthStore()
const loginTab = ref(route.query.tab === 'admin' ? 'admin' : 'user')
const userLoginMode = ref('password')
const userLoading = ref(false)
const adminLoading = ref(false)

const loginForm = reactive({
  account: '',
  password: '',
  email: '',
  code: '',
})

const adminForm = reactive({
  account: 'admin',
  password: 'admin123456',
})

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
    router.push('/profile')
  } catch (error) {
    ElMessage.error(error.message || '登录失败')
  } finally {
    userLoading.value = false
  }
}

function loginByEmail() {
  if (!loginForm.email.trim() || !loginForm.code.trim()) {
    ElMessage.warning('请输入校园邮箱和验证码')
    return
  }

  ElMessage.warning('邮箱验证码登录暂未接入，请使用密码登录')
}

function sendCode() {
  if (!loginForm.email.trim()) {
    ElMessage.warning('请先输入校园邮箱')
    return
  }
  ElMessage.success('验证码已发送，请查看校园邮箱')
}

function ssoLogin() {
  ElMessage.warning('SSO 登录暂未接入，请使用密码登录')
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
    router.push('/admin')
  } catch (error) {
    ElMessage.error(error.message || '管理员账号或密码错误')
  } finally {
    adminLoading.value = false
  }
}
</script>

<template>
  <main class="page-wrap auth-page">
    <section class="auth-hero">
      <p class="section-kicker">校园实名 · 安全交易</p>
      <h1>登录校园二手闲置网</h1>
      <p>登录后可以发布商品、收藏商品、咨询卖家和管理订单。</p>
    </section>

    <section class="auth-layout auth-layout-single">
      <el-card class="auth-card" shadow="never">
        <el-tabs v-model="loginTab" stretch>
          <el-tab-pane label="普通用户登录" name="user">
            <el-tabs v-model="userLoginMode" class="inner-tabs">
              <el-tab-pane label="密码登录" name="password">
                <el-form label-position="top">
                  <el-form-item label="学号/工号">
                    <el-input v-model="loginForm.account" :prefix-icon="User" placeholder="请输入学号或工号" />
                  </el-form-item>
                  <el-form-item label="密码">
                    <el-input
                      v-model="loginForm.password"
                      :prefix-icon="Lock"
                      placeholder="请输入密码"
                      show-password
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

              <el-tab-pane label="邮箱验证码" name="email">
                <el-form label-position="top">
                  <el-form-item label="校园邮箱">
                    <el-input v-model="loginForm.email" :prefix-icon="Message" placeholder="name@school.edu.cn" />
                  </el-form-item>
                  <el-form-item label="验证码">
                    <div class="inline-control">
                      <el-input v-model="loginForm.code" placeholder="6 位验证码" @keyup.enter="loginByEmail" />
                      <el-button @click="sendCode">获取验证码</el-button>
                    </div>
                  </el-form-item>
                  <el-button type="primary" size="large" class="full-button" @click="loginByEmail">
                    邮箱登录
                  </el-button>
                </el-form>
              </el-tab-pane>
            </el-tabs>

            <el-divider>快捷入口</el-divider>
            <el-button size="large" class="full-button" :icon="School" @click="ssoLogin">
              SSO 校园统一身份登录
            </el-button>
          </el-tab-pane>

          <el-tab-pane label="管理员登录" name="admin">
            <el-alert
              title="演示账号：admin / admin123456"
              type="warning"
              show-icon
              :closable="false"
            />
            <el-form class="admin-form" label-position="top">
              <el-form-item label="管理员账号">
                <el-input v-model="adminForm.account" />
              </el-form-item>
              <el-form-item label="管理员密码">
                <el-input v-model="adminForm.password" show-password @keyup.enter="adminLogin" />
              </el-form-item>
              <el-button
                type="primary"
                size="large"
                class="full-button"
                :loading="adminLoading"
                @click="adminLogin"
              >
                进入后台管理面板
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
