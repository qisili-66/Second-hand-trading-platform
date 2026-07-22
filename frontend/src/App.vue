<script setup>
import { computed, onMounted, onUnmounted, ref, watch } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import { ElMessage, ElMessageBox } from 'element-plus'
import { Bell, DataAnalysis, House, Plus, QuestionFilled, Search, User } from '@element-plus/icons-vue'
import NotificationPanel from './components/NotificationPanel.vue'
import { userApi } from './services/api'
import { connectWebSocket, disconnectWebSocket } from './services/websocket'
import { useAuthStore } from './stores/auth'

const router = useRouter()
const route = useRoute()
const authStore = useAuthStore()
const keyword = ref('')
const notifications = ref([])
const unreadNotifications = computed(() => notifications.value.filter((notice) => !notice.readAt).length)
const isHome = computed(() => route.path === '/')
const isAuthPage = computed(() => route.path === '/login' || route.path === '/register')
const isAdminRoute = computed(() => route.path.startsWith('/admin'))
const showMobileAppChrome = computed(() => !isAuthPage.value && !isAdminRoute.value)
const mobilePageTitle = computed(() => {
  if (route.path === '/') return '校园市集'
  if (route.path.startsWith('/items/publish')) return '发布闲置'
  if (route.path.startsWith('/items/')) return '商品详情'
  if (route.path === '/items' || route.path === '/search') return '逛闲置'
  if (route.path === '/orders') return '我的交易'
  if (route.path === '/profile') return '个人中心'
  if (route.path === '/chats') return '校园消息'
  if (route.path === '/wanted') return '求购专区'
  if (route.path === '/swap') return '换物专区'
  if (route.path === '/season') return '毕业季'
  if (route.path === '/help') return '帮助中心'
  return '校园二手'
})

function normalizeNotice(row = {}) {
  return {
    id: row.notificationId || row.id || `${row.type || 'SYSTEM'}-${row.createdAt || Date.now()}`,
    title: row.title || '系统通知',
    content: row.content || '',
    type: row.type || 'SYSTEM',
    readAt: row.readAt || '',
    createdAt: String(row.createdAt || '').replace('T', ' ').slice(0, 16),
  }
}

function upsertNotification(row = {}) {
  const notice = normalizeNotice(row)
  notifications.value = [
    notice,
    ...notifications.value.filter((item) => String(item.id) !== String(notice.id)),
  ].slice(0, 20)
}

async function loadNotifications() {
  if (!authStore.isLoggedIn || authStore.isAdmin) {
    notifications.value = []
    return
  }
  try {
    const response = await userApi.getMyNotifications({ page: 1, pageSize: 20 })
    notifications.value = (response.data?.list || []).map(normalizeNotice)
  } catch (error) {
    notifications.value = []
    console.error(error)
  }
}

function goSearch() {
  router.push({
    path: '/items',
    query: {
      keyword: keyword.value,
    },
  })
}

function goPublish() {
  if (authStore.isLoggedIn) {
    router.push('/items/publish')
    return
  }

  ElMessageBox.confirm('还没登录，登录或注册后才能免费发布商品。', '需要登录', {
    confirmButtonText: '前往登录',
    cancelButtonText: '前往注册',
    distinguishCancelAndClose: true,
    type: 'warning',
  })
    .then(() => router.push('/login'))
    .catch((action) => {
      if (action === 'cancel') {
        router.push('/register')
      }
    })
}

function logout() {
  disconnectWebSocket()
  authStore.logout()
  notifications.value = []
  ElMessage.success('已退出登录')
  router.push('/')
}

function connectRealtime() {
  if (!authStore.isLoggedIn || authStore.isAdmin) return
  connectWebSocket({
    onNotification: upsertNotification,
    onBroadcast: (payload) => {
      upsertNotification({
        ...payload,
        type: 'SYSTEM',
        title: payload.title || '平台公告',
      })
    },
    onMessage: () => {
      ElMessage.info('收到新的聊天消息')
    },
  })
}

onMounted(() => {
  loadNotifications()
  connectRealtime()
})

onUnmounted(disconnectWebSocket)

watch(
  () => [authStore.isLoggedIn, authStore.isAdmin],
  () => {
    loadNotifications()
    disconnectWebSocket()
    connectRealtime()
  },
)

watch(
  () => route.fullPath,
  () => loadNotifications(),
)
</script>

<template>
  <el-config-provider>
    <div :class="['site-shell', { 'home-shell': isHome, 'mobile-app-shell': showMobileAppChrome }]">
      <header class="site-header">
        <div class="topbar">
          <RouterLink class="logo" to="/">
            <img class="logo-mark" src="/brand-mark.svg" alt="" aria-hidden="true" />
            <span>校园二手闲置网</span>
          </RouterLink>

          <nav class="top-links" aria-label="顶部导航">
            <RouterLink to="/">
              <el-icon><House /></el-icon>
              首页
            </RouterLink>
            <RouterLink v-if="!authStore.isLoggedIn" to="/login">登录</RouterLink>
            <RouterLink v-if="!authStore.isLoggedIn" to="/register">注册</RouterLink>
            <RouterLink v-if="authStore.isLoggedIn" to="/profile">
              <el-icon><User /></el-icon>
              个人中心
            </RouterLink>
            <RouterLink v-if="authStore.isAdmin" to="/admin">
              <el-icon><DataAnalysis /></el-icon>
              数据后台
            </RouterLink>
            <el-button v-if="authStore.isLoggedIn" link type="primary" @click="logout">
              退出
            </el-button>
            <RouterLink to="/help">
              <el-icon><QuestionFilled /></el-icon>
              帮助
            </RouterLink>
          </nav>
        </div>

        <div class="searchbar">
          <el-input
            v-model="keyword"
            class="global-search"
            size="large"
            clearable
            placeholder="搜索教材、电脑、宿舍好物"
            @keyup.enter="goSearch"
          >
            <template #prefix>
              <el-icon><Search /></el-icon>
            </template>
          </el-input>

          <el-button class="search-button" size="large" type="primary" @click="goSearch">
            搜商品
          </el-button>

          <el-button class="publish-button" size="large" type="primary" @click="goPublish">
            <el-icon><Plus /></el-icon>
            免费发布商品
          </el-button>

          <el-popover placement="bottom-end" width="320" trigger="click">
            <template #reference>
              <el-badge :value="unreadNotifications || notifications.length" :hidden="notifications.length === 0" class="notice-badge">
                <el-button :icon="Bell" circle size="large" aria-label="消息通知" />
              </el-badge>
            </template>
            <NotificationPanel
              :notifications="notifications"
              @view-all="router.push({ path: '/profile', query: { tab: 'notifications' } })"
            />
            <el-button text type="primary" @click="router.push('/chats')">查看聊天消息</el-button>
          </el-popover>
        </div>
      </header>

      <header v-if="showMobileAppChrome" class="mobile-appbar" aria-label="手机端导航">
        <div>
          <span class="mobile-appbar-kicker">Campus Market</span>
          <strong>{{ mobilePageTitle }}</strong>
        </div>
        <div class="mobile-appbar-actions">
          <button type="button" aria-label="搜索商品" @click="router.push('/items')">
            <el-icon><Search /></el-icon>
          </button>
          <el-popover placement="bottom-end" width="300" trigger="click">
            <template #reference>
              <button type="button" aria-label="消息通知">
                <el-badge :value="unreadNotifications || notifications.length" :hidden="notifications.length === 0">
                  <el-icon><Bell /></el-icon>
                </el-badge>
              </button>
            </template>
            <NotificationPanel
              :notifications="notifications"
              @view-all="router.push({ path: '/profile', query: { tab: 'notifications' } })"
            />
          </el-popover>
        </div>
      </header>

      <RouterView />

      <nav v-if="showMobileAppChrome" class="mobile-tabbar" aria-label="手机端底部导航">
        <RouterLink to="/">
          <el-icon><House /></el-icon>
          <span>首页</span>
        </RouterLink>
        <RouterLink to="/items">
          <el-icon><Search /></el-icon>
          <span>闲置</span>
        </RouterLink>
        <button class="mobile-tabbar-publish" type="button" @click="goPublish">
          <el-icon><Plus /></el-icon>
          <span>发布</span>
        </button>
        <RouterLink to="/orders">
          <el-icon><DataAnalysis /></el-icon>
          <span>交易</span>
        </RouterLink>
        <RouterLink :to="authStore.isLoggedIn ? '/profile' : '/login'">
          <el-icon><User /></el-icon>
          <span>我的</span>
        </RouterLink>
      </nav>

      <footer class="site-footer">
        <span>校园二手闲置网，为同校区学生提供轻量、安全、可追溯的闲置交易服务。</span>
        <nav>
          <RouterLink to="/help">用户协议</RouterLink>
          <RouterLink to="/help">帮助中心</RouterLink>
          <RouterLink to="/help">校园客服</RouterLink>
        </nav>
      </footer>
    </div>
  </el-config-provider>
</template>
