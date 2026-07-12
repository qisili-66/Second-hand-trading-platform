<script setup>
import { computed, ref, watch } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import { ElMessage } from 'element-plus'
import { Goods, Picture, Promotion } from '@element-plus/icons-vue'
import { chatApi } from '../../services/api'
import { normalizeChat, normalizeMessage } from '../../services/normalizers'
import { useAuthStore } from '../../stores/auth'

const route = useRoute()
const router = useRouter()
const authStore = useAuthStore()
const activeId = ref(route.query.chatId ? Number(route.query.chatId) : null)
const input = ref('')
const contacts = ref([])
const localMessages = ref([])
const loading = ref(false)

const currentUserId = computed(() => authStore.user?.userId)
const activeContact = computed(() => contacts.value.find((contact) => contact.id === activeId.value) || null)
const sensitiveHint = computed(() => {
  const words = ['私下转账', '押金', '先付款', '脱离平台']
  return words.find((word) => input.value.includes(word))
})

watch(
  () => authStore.isLoggedIn,
  (loggedIn) => {
    if (loggedIn) fetchChats()
  },
  { immediate: true },
)

watch(activeId, (chatId) => {
  if (chatId) {
    router.replace({ path: '/chats', query: { chatId } })
    fetchMessages(chatId)
  } else {
    localMessages.value = []
  }
})

async function fetchChats() {
  loading.value = true
  try {
    const response = await chatApi.list()
    contacts.value = Array.isArray(response.data)
      ? response.data.map((chat) => normalizeChat(chat, currentUserId.value))
      : []
    if (!activeId.value && contacts.value.length > 0) {
      activeId.value = contacts.value[0].id
    } else if (activeId.value) {
      fetchMessages(activeId.value)
    }
  } catch (error) {
    contacts.value = []
    ElMessage.error(error.message || '聊天加载失败')
  } finally {
    loading.value = false
  }
}

async function fetchMessages(chatId) {
  try {
    const response = await chatApi.messages(chatId)
    const list = response.data?.list || []
    localMessages.value = list.map((message) => normalizeMessage(message, currentUserId.value))
  } catch (error) {
    localMessages.value = []
    ElMessage.error(error.message || '消息加载失败')
  }
}

async function sendText() {
  if (!input.value.trim()) {
    ElMessage.warning('请输入消息内容')
    return
  }

  if (sensitiveHint.value) {
    ElMessage.warning('消息包含敏感词，请修改后再发送')
    return
  }

  try {
    await chatApi.sendMessage(activeId.value, {
      messageType: 'TEXT',
      content: input.value.trim(),
    })
    input.value = ''
    await fetchMessages(activeId.value)
  } catch (error) {
    ElMessage.error(error.message || '消息发送失败')
  }
}

function sendImage() {
  ElMessage.info('图片消息接口已支持 imageUrl，前端上传存储接入后即可发送')
}

async function sendProductCard() {
  if (!activeContact.value) {
    ElMessage.info('暂无可发送的商品会话')
    return
  }

  try {
    await chatApi.sendMessage(activeId.value, {
      messageType: 'ITEM',
      content: `商品卡片：${activeContact.value.item.title}`,
      itemId: activeContact.value.item.id,
    })
    await fetchMessages(activeId.value)
  } catch (error) {
    ElMessage.error(error.message || '商品卡片发送失败')
  }
}
</script>

<template>
  <main class="page-wrap chat-page">
    <el-card v-if="!authStore.isLoggedIn" class="auth-required-card" shadow="never">
      <h1>还没登录</h1>
      <p>登录或注册后才能进入 IM 聊天，联系买家或卖家。</p>
      <div class="hero-actions">
        <el-button type="primary" size="large" @click="router.push('/login')">前往登录</el-button>
        <el-button size="large" @click="router.push('/register')">前往注册</el-button>
      </div>
    </el-card>

    <section v-else class="chat-shell" v-loading="loading">
      <aside class="chat-contacts">
        <h2>消息</h2>
        <button
          v-for="contact in contacts"
          :key="contact.id"
          :class="{ active: contact.id === activeId }"
          type="button"
          @click="activeId = contact.id"
        >
          <el-badge :value="contact.unread" :hidden="!contact.unread">
            <el-avatar>{{ contact.name.slice(0, 1) }}</el-avatar>
          </el-badge>
          <span>
            <strong>{{ contact.name }}</strong>
            <small>{{ contact.last || '暂无消息' }}</small>
          </span>
        </button>
      </aside>

      <section v-if="activeContact" class="chat-window">
        <header class="chat-header">
          <div>
            <h1>{{ activeContact.name }}</h1>
            <p>{{ activeContact.item.title }}</p>
          </div>
          <el-tag type="warning">平台会话</el-tag>
        </header>

        <div class="chat-product-card">
          <el-image :src="activeContact.item.image" fit="cover" />
          <div>
            <strong>{{ activeContact.item.title }}</strong>
            <p>请在平台内完成沟通和交易</p>
          </div>
          <el-button type="primary" plain :icon="Goods" @click="sendProductCard">发送商品卡</el-button>
        </div>

        <div class="message-list">
          <div
            v-for="message in localMessages"
            :key="message.id"
            class="message-bubble"
            :class="message.from"
          >
            <p>{{ message.text }}</p>
            <small>{{ message.time }}</small>
          </div>
        </div>

        <footer class="chat-input">
          <el-alert
            v-if="sensitiveHint"
            :title="`检测到敏感词“${sensitiveHint}”，建议在平台内完成沟通与交易。`"
            type="warning"
            :closable="false"
            show-icon
          />
          <div class="chat-toolbar">
            <el-button :icon="Picture" @click="sendImage">图片</el-button>
            <el-button :icon="Goods" @click="sendProductCard">商品卡</el-button>
          </div>
          <div class="chat-send-row">
            <el-input
              v-model="input"
              type="textarea"
              :rows="3"
              placeholder="输入消息，避免发送联系方式、押金、私下转账等风险内容"
              @keyup.enter.exact="sendText"
            />
            <el-button type="primary" size="large" :icon="Promotion" @click="sendText">发送</el-button>
          </div>
        </footer>
      </section>
      <section v-else class="chat-window chat-empty-window">
        <el-empty description="暂无聊天消息" />
      </section>
    </section>
  </main>
</template>
