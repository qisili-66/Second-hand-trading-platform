<script setup>
import { computed, onMounted, ref } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import { ElMessage } from 'element-plus'
import {
  Bell,
  Box,
  CollectionTag,
  DataAnalysis,
  Document,
  Goods,
  House,
  Lock,
  Setting,
  SwitchButton,
  User,
  Warning,
} from '@element-plus/icons-vue'
import { useAuthStore } from '../stores/auth'
import { adminApi } from '../services/api'

const router = useRouter()
const route = useRoute()
const authStore = useAuthStore()
const passwordDialog = ref(false)
const passwordSaving = ref(false)
const notificationLoading = ref(false)
const dashboard = ref({
  pendingReports: 0,
  pendingDisputes: 0,
  pendingOrders: 0,
  pendingVerifiedUsers: 0,
})
const draftNoticeCount = ref(0)
const passwordForm = ref({
  oldPassword: '',
  newPassword: '',
  confirmPassword: '',
})

const menuItems = [
  { path: '/admin', label: '首页数据大盘', icon: DataAnalysis },
  { path: '/admin/users', label: '用户管理', icon: User },
  { path: '/admin/items', label: '商品管理', icon: Goods },
  { path: '/admin/categories', label: '分类管理', icon: CollectionTag },
  { path: '/admin/orders', label: '订单&纠纷管理', icon: Box },
  { path: '/admin/reports', label: '举报审核管理', icon: Warning },
  { path: '/admin/settings', label: '系统配置', icon: Setting },
  { path: '/admin/notices', label: '公告管理', icon: Document },
  { path: '/admin/knowledge', label: 'Agent 知识库', icon: Document },
  { path: '/admin/agent-operations', label: 'Agent 运营指标', icon: DataAnalysis },
]

const notificationItems = computed(() => [
  {
    count: Number(dashboard.value.pendingReports) || 0,
    title: `${Number(dashboard.value.pendingReports) || 0} 条举报待处理`,
    path: '/admin/reports',
    active: Number(dashboard.value.pendingReports) > 0,
  },
  {
    count: Number(dashboard.value.pendingDisputes) || 0,
    title: `${Number(dashboard.value.pendingDisputes) || 0} 条纠纷等待仲裁`,
    path: '/admin/orders',
    active: Number(dashboard.value.pendingDisputes) > 0,
  },
  {
    count: Number(dashboard.value.pendingVerifiedUsers) || 0,
    title: `${Number(dashboard.value.pendingVerifiedUsers) || 0} 个用户待实名认证确认`,
    path: '/admin/users',
    active: Number(dashboard.value.pendingVerifiedUsers) > 0,
  },
  {
    count: draftNoticeCount.value,
    title: `${draftNoticeCount.value} 篇公告草稿待发布`,
    path: '/admin/notices',
    active: draftNoticeCount.value > 0,
  },
])

const activeNotificationCount = computed(() =>
  notificationItems.value.reduce((total, item) => total + item.count, 0),
)

const activeNotificationItems = computed(() => notificationItems.value.filter((item) => item.active))

function resetPasswordForm() {
  passwordForm.value.oldPassword = ''
  passwordForm.value.newPassword = ''
  passwordForm.value.confirmPassword = ''
}

async function loadNotifications() {
  if (!authStore.isAdmin) return
  notificationLoading.value = true
  try {
    const [dashboardResponse, noticesResponse] = await Promise.all([
      adminApi.dashboard(),
      adminApi.notices({ page: 1, pageSize: 100 }),
    ])
    dashboard.value = { ...dashboard.value, ...(dashboardResponse.data || {}) }
    draftNoticeCount.value = (noticesResponse.data?.list || []).filter((notice) => notice.status !== 'PUBLISHED').length
  } catch (error) {
    ElMessage.error(error.message || '后台通知加载失败')
  } finally {
    notificationLoading.value = false
  }
}

function logout() {
  authStore.logout()
  ElMessage.success('管理员已退出')
  router.push({ path: '/login', query: { tab: 'admin' } })
}

async function savePassword() {
  if (!passwordForm.value.oldPassword) {
    ElMessage.warning('请输入当前密码')
    return
  }
  if (!passwordForm.value.newPassword || passwordForm.value.newPassword.length < 8) {
    ElMessage.warning('新密码至少 8 位')
    return
  }
  if (passwordForm.value.newPassword !== passwordForm.value.confirmPassword) {
    ElMessage.warning('两次输入的新密码不一致')
    return
  }
  passwordSaving.value = true
  try {
    await adminApi.changePassword({
      oldPassword: passwordForm.value.oldPassword,
      newPassword: passwordForm.value.newPassword,
    })
    passwordDialog.value = false
    resetPasswordForm()
    ElMessage.success('后台密码已修改，请记住新密码')
  } catch (error) {
    ElMessage.error(error.message || '后台密码修改失败')
  } finally {
    passwordSaving.value = false
  }
}

onMounted(loadNotifications)
</script>

<template>
  <div class="admin-shell">
    <aside class="admin-sidebar">
      <RouterLink class="admin-logo" to="/admin">
        <span>管</span>
        <strong>校园二手后台</strong>
      </RouterLink>

      <el-menu
        class="admin-menu"
        :default-active="route.path"
        background-color="#151923"
        text-color="#b8c0cc"
        active-text-color="#ff7800"
        router
      >
        <el-menu-item v-for="item in menuItems" :key="item.path" :index="item.path">
          <el-icon><component :is="item.icon" /></el-icon>
          <span>{{ item.label }}</span>
        </el-menu-item>
      </el-menu>
    </aside>

    <section class="admin-main">
      <header class="admin-header">
        <div>
          <p>管理员控制台</p>
          <h1>{{ route.meta.title || '首页数据大盘' }}</h1>
        </div>

        <div class="admin-actions">
          <RouterLink to="/">
            <el-button :icon="House">返回前台</el-button>
          </RouterLink>
          <el-button v-if="authStore.isAdmin" :icon="Lock" @click="passwordDialog = true">修改后台密码</el-button>
          <el-popover v-if="authStore.isAdmin" placement="bottom-end" width="280" trigger="click" @show="loadNotifications">
            <template #reference>
              <el-badge :value="activeNotificationCount" :hidden="activeNotificationCount === 0">
                <el-button :icon="Bell" circle />
              </el-badge>
            </template>
            <div v-loading="notificationLoading" class="notice-panel">
              <h3>后台通知</h3>
              <ul v-if="activeNotificationItems.length">
                <li v-for="item in activeNotificationItems" :key="item.title" @click="router.push(item.path)">
                  {{ item.title }}
                </li>
              </ul>
              <el-empty v-else description="暂无待办" :image-size="72" />
            </div>
          </el-popover>
          <el-button v-if="authStore.isAdmin" type="primary" :icon="SwitchButton" @click="logout">退出登录</el-button>
        </div>
      </header>

      <main v-if="authStore.isAdmin" class="admin-content">
        <RouterView />
      </main>
      <main v-else class="admin-content">
        <el-card class="auth-required-card" shadow="never">
          <h1>还没登录管理员账号</h1>
          <p>请使用 admin/admin123456 登录后进入后台管理系统。</p>
          <el-button type="primary" size="large" @click="router.push({ path: '/login', query: { tab: 'admin' } })">
            前往管理员登录
          </el-button>
        </el-card>
      </main>
    </section>

    <el-dialog v-model="passwordDialog" title="修改后台密码" width="460">
      <el-form label-position="top">
        <el-form-item label="当前密码">
          <el-input v-model="passwordForm.oldPassword" show-password />
        </el-form-item>
        <el-form-item label="新密码">
          <el-input v-model="passwordForm.newPassword" show-password placeholder="请输入新密码" />
        </el-form-item>
        <el-form-item label="确认新密码">
          <el-input v-model="passwordForm.confirmPassword" show-password placeholder="再次输入新密码" />
        </el-form-item>
      </el-form>
      <template #footer>
        <el-button @click="passwordDialog = false">取消</el-button>
        <el-button type="primary" :loading="passwordSaving" @click="savePassword">保存修改</el-button>
      </template>
    </el-dialog>
  </div>
</template>
