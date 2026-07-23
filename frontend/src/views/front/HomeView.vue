<script setup>
import { computed, onMounted, ref, watch } from 'vue'
import { useRouter } from 'vue-router'
import { ElMessage, ElMessageBox } from 'element-plus'
import { ChatDotRound, Close, EditPen, Goods, Promotion } from '@element-plus/icons-vue'
import ProductGridCard from '../../components/product/ProductGridCard.vue'
import { agentApi, chatApi, itemApi, orderApi, swapApi, userApi, wantedApi } from '../../services/api'
import {
  agentHistoryUserId,
  agentTurnSummary,
  agentTurnTime,
  agentTurnTitle,
  clearAgentDraft,
  clearAgentHistory,
  readAgentDraft,
  readAgentHistory,
  saveAgentDraft,
  saveAgentTurn,
} from '../../services/agentHistory'
import { normalizeItemPage } from '../../services/normalizers'
import { useAuthStore } from '../../stores/auth'

const router = useRouter()
const authStore = useAuthStore()
const activeTab = ref('latest')
const products = ref([])
const loading = ref(false)
const agentOpen = ref(false)
const agentMode = ref('')
const agentInput = ref('')
const agentLoading = ref(false)
const agentActionLoading = ref('')
const agentResult = ref(null)
const agentError = ref('')
const agentHistory = ref([])
const agentUserId = computed(() => agentHistoryUserId(authStore.user))
const hasAgentHistory = computed(() => agentHistory.value.length > 0)

const recommendedProducts = computed(() => {
  if (activeTab.value === 'hot') {
    return [...products.value].sort((a, b) => b.hot - a.hot).slice(0, 4)
  }

  if (activeTab.value === 'near') {
    return products.value.filter((product) => product.campus === '校本部').slice(0, 4)
  }

  return products.value.slice(0, 4)
})

const campusStats = computed(() => [
  { label: '正在流转', value: products.value.length || 0, unit: '件' },
  { label: '同校区优先', value: recommendedProducts.value.length || 0, unit: '组' },
  { label: '发布成本', value: 0, unit: '元' },
])

async function fetchRecommendedProducts() {
  loading.value = true
  try {
    const response = await itemApi.list({ page: 1, pageSize: 8, sort: 'latest' })
    products.value = normalizeItemPage(response).list
  } catch (error) {
    products.value = []
    if (!String(error.message || '').includes('timeout')) {
      console.warn('首页商品列表加载失败', error.message || error)
    }
  } finally {
    loading.value = false
  }
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
      if (action === 'cancel') router.push('/register')
    })
}

function goItemList() {
  router.push('/items')
}

function loadAgentHistory() {
  agentHistory.value = readAgentHistory(agentUserId.value)
}

function defaultAgentPrompt(mode) {
  return mode === 'buyer'
    ? '我想买一个考研用的 iPad，预算 1500 左右，最好校本部面交。'
    : '出一个宿舍小冰箱，八成新，毕业搬宿舍用不上了。'
}

function loadAgentDraft() {
  const draft = readAgentDraft(agentUserId.value)
  if (!draft) return false
  agentMode.value = draft.mode
  agentInput.value = draft.message
  agentResult.value = null
  agentError.value = ''
  return true
}

function restoreAgentTurn(record) {
  agentOpen.value = true
  agentMode.value = record.mode
  agentInput.value = record.message
  agentResult.value = record.result
  agentError.value = ''
}

function clearAgentTurns() {
  agentHistory.value = clearAgentHistory(agentUserId.value)
  ElMessage.success('已清空 Agent 记录')
}

function openAgent(mode) {
  agentOpen.value = true
  agentMode.value = mode
  agentResult.value = null
  agentError.value = ''
  const draft = readAgentDraft(agentUserId.value)
  agentInput.value = draft?.mode === mode && draft.message ? draft.message : defaultAgentPrompt(mode)
}

function resetAgentMode() {
  agentMode.value = ''
  agentInput.value = ''
  agentResult.value = null
  agentError.value = ''
  clearAgentDraft(agentUserId.value)
}

async function submitAgent() {
  if (!agentMode.value) return
  if (!agentInput.value.trim()) {
    ElMessage.warning('先告诉 Agent 你的需求')
    return
  }

  agentLoading.value = true
  agentError.value = ''
  try {
    const payload = {
      message: agentInput.value.trim(),
      userId: authStore.user?.userId || authStore.user?.id || null,
    }
    const response =
      agentMode.value === 'buyer' ? await agentApi.buyer(payload) : await agentApi.seller(payload)
    agentResult.value = response.data || response
    agentHistory.value = saveAgentTurn(agentUserId.value, {
      mode: agentMode.value,
      message: payload.message,
      result: agentResult.value,
    })
    agentInput.value = ''
    clearAgentDraft(agentUserId.value)
  } catch (error) {
    agentResult.value = null
    agentError.value = error.message || 'Agent 暂时没有响应，请确认后端和 AI 服务已启动。'
  } finally {
    agentLoading.value = false
  }
}

async function copyAgentText(text) {
  if (!text) return
  try {
    await navigator.clipboard.writeText(text)
    ElMessage.success('已复制')
  } catch (error) {
    ElMessage.warning('浏览器暂不允许复制，请手动选中文案')
  }
}

function goAgentItem(item) {
  if (!item?.item_id) return
  router.push(`/items/${item.item_id}`)
}

function searchSimilarFromAgent(item = {}) {
  const query = {}
  const keyword = item.title || agentResult.value?.parsed_need?.keyword
  if (keyword) query.keyword = keyword
  if (item.campus || agentResult.value?.parsed_need?.campus) query.campus = item.campus || agentResult.value.parsed_need.campus
  if (agentResult.value?.parsed_need?.budget) query.maxPrice = agentResult.value.parsed_need.budget
  router.push({ path: '/items', query })
}

function ensureAgentLogin(actionText) {
  if (authStore.isLoggedIn) return true
  ElMessageBox.confirm(`${actionText}需要先登录，登录后 Agent 会继续把草稿带到对应流程。`, '需要登录', {
    confirmButtonText: '前往登录',
    cancelButtonText: '取消',
    type: 'warning',
  }).then(() => router.push('/login')).catch(() => {})
  return false
}

function inferPriceFromRange(value = '') {
  const numbers = String(value).match(/\d+(?:\.\d+)?/g)?.map(Number).filter(Number.isFinite) || []
  if (numbers.length === 0) return null
  if (numbers.length === 1) return numbers[0]
  return Math.round((numbers[0] + numbers[1]) / 2)
}

async function runAgentAction(key, action) {
  if (agentActionLoading.value) return
  agentActionLoading.value = key
  try {
    await action()
  } catch (error) {
    if (error !== 'cancel' && error !== 'close') {
      ElMessage.error(error.message || 'Agent 执行动作失败')
    }
  } finally {
    agentActionLoading.value = ''
  }
}

async function confirmAgentAction(title, message) {
  await ElMessageBox.confirm(message, title, {
    confirmButtonText: '确认执行',
    cancelButtonText: '取消',
    type: 'warning',
  })
}

async function agentSendChat(item = {}) {
  const itemId = item.item_id || item.itemId || item.id
  if (!itemId) return
  if (!ensureAgentLogin('Agent 代发私聊')) return
  await runAgentAction(`chat-${itemId}`, async () => {
    await confirmAgentAction('确认由 Agent 发送私聊？', `Agent 将基于《${item.title || '该商品'}》创建会话，并发送推荐的咨询草稿。`)
    const response = await chatApi.create({ itemId })
    const chat = response.data || response
    await chatApi.sendMessage(chat.chatId || chat.id, {
      messageType: 'TEXT',
      content: item.chat_draft || `同学你好，我对《${item.title || '这件商品'}》感兴趣，想了解一下成色、配件和方便面交的时间。`,
    })
    ElMessage.success('Agent 已发送私聊草稿')
    router.push({ path: '/chats', query: { chatId: chat.chatId || chat.id } })
  })
}

async function agentCreateOrder(item = {}) {
  const itemId = item.item_id || item.itemId || item.id
  if (!itemId) return
  if (!ensureAgentLogin('Agent 预约商品')) return
  await runAgentAction(`order-${itemId}`, async () => {
    await confirmAgentAction('确认由 Agent 创建预约？', `Agent 将为《${item.title || '该商品'}》创建订单预约，后续仍需要卖家接单和你确认交易。`)
    const response = await orderApi.create({
      itemId,
      tradeMode: 'OFFLINE',
      message: item.chat_draft || `我想预约《${item.title || '这件商品'}》，方便的话请确认成色和面交时间。`,
    })
    const order = response.data || response
    ElMessage.success('Agent 已创建订单预约')
    router.push({ path: '/orders', query: { orderId: order.orderId || order.id } })
  })
}

async function agentPublishWanted(draft) {
  if (!draft) return
  if (!ensureAgentLogin('Agent 发布求购')) return
  await runAgentAction('wanted', async () => {
    await confirmAgentAction('确认由 Agent 发布求购？', `Agent 将直接发布求购《${draft.title}》，发布后可在求购广场查看和关闭。`)
    const response = await wantedApi.create({
      title: draft.title,
      description: draft.description || '',
      campus: draft.campus || authStore.user?.campus || '校本部',
      budgetMin: draft.budget_min || null,
      budgetMax: draft.budget_max || null,
    })
    agentResult.value.created_wanted = response.data || response
    ElMessage.success('Agent 已发布求购')
    router.push('/wanted')
  })
}

async function agentPublishSwap(draft) {
  if (!draft) return
  if (!ensureAgentLogin('Agent 发布换物')) return
  await runAgentAction('swap', async () => {
    const itemsResponse = await userApi.getMyItems({ page: 1, pageSize: 100 })
    const myOnSaleItems = normalizeItemPage(itemsResponse).list.filter((item) => item.status === 'ON_SALE')
    if (myOnSaleItems.length !== 1) {
      useSwapDraft(draft)
      ElMessage.info(myOnSaleItems.length === 0 ? '你还没有可用于换物的在售商品，先去发布一件闲置。' : '检测到多件在售商品，请在换物页选择拿来交换的商品。')
      return
    }
    await confirmAgentAction('确认由 Agent 发布换物？', `Agent 将使用《${myOnSaleItems[0].title}》发布换物需求：${draft.expected_title || draft.title}。`)
    const response = await swapApi.create({
      itemId: myOnSaleItems[0].id,
      expectedTitle: draft.expected_title || draft.title,
      targetCategory: draft.target_category || draft.category || null,
      description: draft.description || '',
      campus: draft.campus || myOnSaleItems[0].campus || authStore.user?.campus || '校本部',
    })
    agentResult.value.created_swap = response.data || response
    ElMessage.success('Agent 已发布换物需求')
    router.push('/swap')
  })
}

async function agentPublishItem(result) {
  const draft = result?.draft
  if (!draft) return
  if (!ensureAgentLogin('Agent 发布商品')) return
  await runAgentAction('publish-item', async () => {
    const price = inferPriceFromRange(draft.price_range)
    if (price === null) {
      ElMessage.warning('Agent 草稿缺少可识别价格，请先填入发布页确认。')
      usePublishDraft(result)
      return
    }
    await confirmAgentAction('确认由 Agent 直接发布商品？', `Agent 将以 ${price} 元发布《${draft.title}》。当前没有图片会先无图上架，你也可以取消后去发布页补图。`)
    const response = await itemApi.create({
      title: draft.title,
      description: draft.description,
      price,
      originalPrice: null,
      condition: draft.condition,
      category: draft.category,
      campus: draft.campus_suggestion || authStore.user?.campus || '校本部',
      dormitory: draft.trade_place_suggestion || '',
      tradeModes: ['面交'],
      swapSupported: Boolean(draft.swap_supported),
      status: '上架',
      imageUrls: [],
    })
    const item = response.data || response
    agentResult.value.created_item = item
    ElMessage.success('Agent 已发布商品')
    router.push(`/items/${item.itemId || item.id}`)
  })
}

function useWantedDraft(draft) {
  if (!draft) return
  sessionStorage.setItem('campus-agent-wanted-draft', JSON.stringify(draft))
  if (!authStore.isLoggedIn) {
    router.push({ path: '/login', query: { redirect: '/wanted' } })
    return
  }
  router.push({ path: '/wanted', query: { fromAgent: 'buyer' } })
}

function useSwapDraft(draft) {
  if (!draft) return
  sessionStorage.setItem('campus-agent-swap-draft', JSON.stringify(draft))
  if (!authStore.isLoggedIn) {
    router.push({ path: '/login', query: { redirect: '/swap' } })
    return
  }
  router.push({ path: '/swap', query: { fromAgent: 'buyer' } })
}

function usePublishDraft(result) {
  if (!result?.draft) return
  sessionStorage.setItem('campus-agent-publish-draft', JSON.stringify(result.draft))
  if (!authStore.isLoggedIn) {
    router.push({ path: '/login', query: { redirect: '/items/publish' } })
    return
  }
  router.push({ path: '/items/publish', query: { fromAgent: 'seller' } })
}

watch(agentUserId, () => {
  loadAgentHistory()
  if (!loadAgentDraft()) {
    agentMode.value = ''
    agentInput.value = ''
    agentResult.value = null
    agentError.value = ''
  }
})

watch([agentMode, agentInput], () => {
  if (agentMode.value && agentInput.value.trim()) {
    saveAgentDraft(agentUserId.value, { mode: agentMode.value, message: agentInput.value })
    return
  }
  clearAgentDraft(agentUserId.value)
})

onMounted(() => {
  fetchRecommendedProducts()
  loadAgentHistory()
  loadAgentDraft()
})
</script>

<template>
  <main class="home-page">
    <section class="campus-hero" aria-labelledby="home-hero-title">
      <div class="hero-orbit hero-orbit-left" aria-hidden="true">
        <div class="dot-hand dot-hand-left"></div>
        <span class="orbit-chip orbit-chip-book">教材</span>
        <span class="orbit-chip orbit-chip-camera">数码</span>
      </div>

      <div class="hero-copy">
        <p class="hero-kicker">Campus Loop Market</p>
        <h1 id="home-hero-title" class="split-title">
          <span>把闲置交给</span>
          <span>明天会遇见的同学</span>
        </h1>
        <p class="hero-subtitle">
          同校区筛选、真实留言、订单留痕。教材、耳机、宿舍小物和毕业季清仓，都先在校园里转一圈。
        </p>
        <div class="hero-actions magnetic-row">
          <el-button class="hero-primary" size="large" type="primary" @click="goItemList">
            逛校园市集
          </el-button>
          <el-button class="hero-secondary" size="large" @click="goPublish">
            发布一件闲置
          </el-button>
          <el-button class="hero-ghost" size="large" text @click="router.push('/wanted')">
            写下求购
          </el-button>
        </div>
      </div>

      <div class="hero-orbit hero-orbit-right" aria-hidden="true">
        <div class="dot-hand dot-hand-right"></div>
        <span class="trust-pill">同校区面交</span>
        <span class="orbit-chip orbit-chip-bike">宿舍好物</span>
      </div>

      <div class="hero-bottom-line">
        <span>轻量发布，保留聊天与订单凭证。</span>
        <span>先看附近，再决定要不要约见。</span>
      </div>
    </section>

    <section class="home-quick-panel" aria-label="校园交易概览">
      <div v-for="item in campusStats" :key="item.label" class="quick-stat-card">
        <strong>{{ item.value }}<small>{{ item.unit }}</small></strong>
        <span>{{ item.label }}</span>
      </div>
      <button class="quick-link-card" type="button" @click="router.push('/orders')">
        <span>我的交易</span>
        <strong>查看订单</strong>
      </button>
      <button class="quick-link-card" type="button" @click="router.push('/swap')">
        <span>换物专区</span>
        <strong>看看谁想交换</strong>
      </button>
    </section>

    <section class="market-section content-section">
      <div class="section-head market-head">
        <div>
          <p class="section-kicker">首页推荐</p>
          <h2>今天可以顺手带走的好物</h2>
        </div>
        <div class="section-actions">
          <el-tabs v-model="activeTab" class="compact-tabs market-tabs">
            <el-tab-pane label="最新发布" name="latest" />
            <el-tab-pane label="热门商品" name="hot" />
            <el-tab-pane label="附近商品" name="near" />
          </el-tabs>
          <el-button class="market-more" type="primary" plain @click="goItemList">全部商品</el-button>
        </div>
      </div>

      <div v-if="recommendedProducts.length > 0" v-loading="loading" class="product-grid home-product-grid">
        <ProductGridCard v-for="product in recommendedProducts" :key="product.id" :product="product" />
      </div>
      <el-empty v-else-if="!loading" description="暂无商品，注册登录后可以发布第一件闲置" />
    </section>

    <aside :class="['campus-agent-widget', { 'is-open': agentOpen }]" aria-label="校园 Agent 助手">
      <button class="agent-fab" type="button" aria-label="打开校园 Agent" @click="agentOpen = !agentOpen">
        <el-icon v-if="agentOpen"><Close /></el-icon>
        <el-icon v-else><ChatDotRound /></el-icon>
        <span>AI</span>
      </button>

      <transition name="agent-pop">
        <div v-if="agentOpen" class="agent-panel">
          <header class="agent-panel-head">
            <div>
              <span>Campus Agent</span>
              <strong>{{ agentMode === 'buyer' ? '淘货 Agent' : agentMode === 'seller' ? '发布 Agent' : '你想先做什么？' }}</strong>
            </div>
            <div class="agent-head-actions">
              <button v-if="hasAgentHistory" class="agent-text-btn" type="button" @click="clearAgentTurns">清空</button>
              <button v-if="agentMode" class="agent-text-btn" type="button" @click="resetAgentMode">切换</button>
            </div>
          </header>

          <div v-if="!agentMode" class="agent-choice-grid">
            <button type="button" @click="openAgent('buyer')">
              <el-icon><Goods /></el-icon>
              <strong>我要淘货</strong>
              <span>说预算、用途、校区，帮你筛商品和砍价。</span>
            </button>
            <button type="button" @click="openAgent('seller')">
              <el-icon><EditPen /></el-icon>
              <strong>我要发布</strong>
              <span>一句话生成标题、描述、分类和风险提醒。</span>
            </button>
          </div>

          <section v-if="!agentMode && hasAgentHistory" class="agent-history-list" aria-label="Agent 最近记录">
            <button v-for="record in agentHistory" :key="record.id" type="button" @click="restoreAgentTurn(record)">
              <span>{{ record.mode === 'buyer' ? '淘货' : '发布' }} · {{ agentTurnTime(record) }}</span>
              <strong>{{ agentTurnTitle(record) }}</strong>
              <small>{{ agentTurnSummary(record) }}</small>
            </button>
          </section>

          <div v-if="agentMode" class="agent-chat-box">
            <div class="agent-prompt-row">
              <el-input
                v-model="agentInput"
                type="textarea"
                :rows="4"
                resize="none"
                maxlength="1000"
                show-word-limit
                :placeholder="agentMode === 'buyer' ? '例如：想买考研用 iPad，预算 1500，校本部面交' : '例如：出宿舍小冰箱，八成新，毕业搬宿舍用不上'"
              />
              <el-button :loading="agentLoading" type="primary" @click="submitAgent">
                <el-icon><Promotion /></el-icon>
                发送
              </el-button>
            </div>

            <p v-if="agentError" class="agent-error">{{ agentError }}</p>

            <section v-if="agentResult?.agent === 'buyer'" class="agent-result-card">
              <p class="agent-summary">{{ agentResult.summary }}</p>
              <div class="agent-tags">
                <span>{{ agentResult.parsed_need?.keyword || '校园闲置' }}</span>
                <span v-if="agentResult.parsed_need?.budget">预算 {{ agentResult.parsed_need.budget }} 元</span>
                <span v-if="agentResult.parsed_need?.campus">{{ agentResult.parsed_need.campus }}</span>
              </div>

              <article v-for="item in agentResult.recommendations" :key="item.item_id || item.title" class="agent-rec-item">
                <div>
                  <strong>{{ item.title }}</strong>
                  <span v-if="item.price">¥{{ item.price }}</span>
                </div>
                <p>{{ item.reason }}</p>
                <small>风险：{{ item.risk }}</small>
                <small>建议砍价：{{ item.bargain_range }}</small>
                <div class="agent-action-row">
                  <el-button v-if="item.item_id" text type="primary" @click="goAgentItem(item)">查看商品</el-button>
                  <el-button v-if="item.item_id" text type="primary" :loading="agentActionLoading === `chat-${item.item_id}`" @click="agentSendChat(item)">Agent 发私聊</el-button>
                  <el-button v-if="item.item_id" text :loading="agentActionLoading === `order-${item.item_id}`" @click="agentCreateOrder(item)">Agent 预约</el-button>
                  <el-button text type="primary" @click="copyAgentText(item.chat_draft)">复制私聊草稿</el-button>
                  <el-button text @click="searchSimilarFromAgent(item)">搜相似</el-button>
                </div>
              </article>

              <article v-if="agentResult.wanted_draft" class="agent-rec-item agent-wanted-draft">
                <div>
                  <strong>{{ agentResult.wanted_draft.title }}</strong>
                  <span>求购草稿</span>
                </div>
                <p>{{ agentResult.wanted_draft.description }}</p>
                <div class="agent-action-row">
                  <el-button text type="primary" @click="useWantedDraft(agentResult.wanted_draft)">去求购广场</el-button>
                  <el-button text type="primary" :loading="agentActionLoading === 'wanted'" @click="agentPublishWanted(agentResult.wanted_draft)">Agent 发布求购</el-button>
                  <el-button text @click="copyAgentText(agentResult.wanted_draft.description)">复制求购草稿</el-button>
                  <el-button text @click="searchSimilarFromAgent()">搜相似商品</el-button>
                </div>
              </article>

              <article v-if="agentResult.swap_draft" class="agent-rec-item agent-wanted-draft">
                <div>
                  <strong>{{ agentResult.swap_draft.title }}</strong>
                  <span>换物草稿</span>
                </div>
                <p>{{ agentResult.swap_draft.description }}</p>
                <div class="agent-action-row">
                  <el-button text type="primary" @click="useSwapDraft(agentResult.swap_draft)">去换物专区</el-button>
                  <el-button text type="primary" :loading="agentActionLoading === 'swap'" @click="agentPublishSwap(agentResult.swap_draft)">Agent 发布换物</el-button>
                  <el-button text @click="copyAgentText(agentResult.swap_draft.description)">复制换物说明</el-button>
                  <el-button text @click="router.push({ path: '/items', query: { keyword: agentResult.swap_draft.expected_title } })">搜可换商品</el-button>
                </div>
              </article>
            </section>

            <section v-if="agentResult?.agent === 'seller'" class="agent-result-card">
              <article class="agent-draft-card">
                <span>发布草稿</span>
                <h3>{{ agentResult.draft.title }}</h3>
                <p>{{ agentResult.draft.description }}</p>
                <div class="agent-tags">
                  <span>{{ agentResult.draft.category }}</span>
                  <span>{{ agentResult.draft.condition }}</span>
                  <span>{{ agentResult.draft.price_range }}</span>
                  <span v-if="agentResult.draft.swap_supported">接受置换</span>
                </div>
                <small>建议地点：{{ agentResult.draft.trade_place_suggestion }}</small>
                <div class="agent-action-row">
                  <el-button text type="primary" @click="usePublishDraft(agentResult)">填入发布页</el-button>
                  <el-button text type="primary" :loading="agentActionLoading === 'publish-item'" @click="agentPublishItem(agentResult)">Agent 直接发布</el-button>
                  <el-button text @click="copyAgentText(agentResult.draft.description)">复制描述</el-button>
                </div>
              </article>

              <ul class="agent-tip-list">
                <li v-for="tip in agentResult.risk_tips" :key="tip">{{ tip }}</li>
              </ul>
            </section>
          </div>
        </div>
      </transition>
    </aside>
  </main>
</template>
