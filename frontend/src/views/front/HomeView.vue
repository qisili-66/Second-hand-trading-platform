<script setup>
import { computed, onMounted, ref, watch } from 'vue'
import { useRouter } from 'vue-router'
import { ElMessage, ElMessageBox } from 'element-plus'
import { ChatDotRound, Close, Goods, Promotion } from '@element-plus/icons-vue'
import ProductGridCard from '../../components/product/ProductGridCard.vue'
import { agentApi, itemApi } from '../../services/api'
import {
  agentTurnSummary,
  agentTurnTime,
  agentTurnTitle,
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
const agentResult = ref(null)
const agentError = ref('')
const agentHistory = ref([])
const hasAgentHistory = computed(() => agentHistory.value.length > 0)
const agentModeLabel = computed(() => agentResult.value?.mode === 'fallback' || agentResult.value?.mode === 'basic-filter' ? '基础筛选' : '模型增强推荐')
const agentTimeline = computed(() => agentResult.value?.timeline || agentResult.value?.steps || [])

function timelineLabel(step) {
  return { search_items: '已查询商品', seller_summary: '已查询卖家信用', order_status: '已查询本人订单', user_preferences: '已查询偏好', item_realtime: '已核验商品状态', agent_service: 'Agent 服务' }[step.tool] || '已完成安全查询'
}

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

async function loadAgentHistory() {
  if (!authStore.isLoggedIn) {
    agentHistory.value = []
    return
  }
  try {
    const response = await agentApi.runs()
    const rows = response.data || response || []
    agentHistory.value = Array.isArray(rows)
      ? rows.map((row) => ({
          id: row.runId,
          mode: 'buyer',
          message: row.message || '',
          result: row.result || {},
          createdAt: row.createdAt || '',
        }))
      : []
  } catch (error) {
    agentHistory.value = []
  }
}

function defaultAgentPrompt() {
  return '我想买一个考研用的 iPad，预算 1500 左右，最好校本部面交。'
}

function restoreAgentTurn(record) {
  agentOpen.value = true
  agentMode.value = record.mode
  agentInput.value = record.message
  agentResult.value = record.result
  agentError.value = ''
}

async function clearAgentTurns() {
  try {
    await agentApi.clearRuns()
    agentHistory.value = []
    ElMessage.success('已清空 Agent 记录')
  } catch (error) {
    ElMessage.error(error.message || '清空 Agent 记录失败')
  }
}

function openAgent() {
  agentOpen.value = true
  agentMode.value = 'buyer'
  agentResult.value = null
  agentError.value = ''
  agentInput.value = defaultAgentPrompt()
}

function resetAgentMode() {
  agentMode.value = ''
  agentInput.value = ''
  agentResult.value = null
  agentError.value = ''
}

async function submitAgent() {
  if (!agentMode.value) return
  if (agentMode.value === 'buyer' && !ensureAgentLogin('使用可信导购 Agent')) return
  if (!agentInput.value.trim()) {
    ElMessage.warning('先告诉 Agent 你的需求')
    return
  }

  agentLoading.value = true
  agentError.value = ''
  try {
    const payload = { message: agentInput.value.trim() }
    const response = await agentApi.buyerRun(payload)
    agentResult.value = response.data || response
    await loadAgentHistory()
    agentInput.value = ''
  } catch (error) {
    agentResult.value = null
    agentError.value = error.message || 'Agent 暂时没有响应，请确认后端和 AI 服务已启动。'
  } finally {
    agentLoading.value = false
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
  ElMessageBox.confirm(`${actionText}需要先登录。`, '需要登录', {
    confirmButtonText: '前往登录',
    cancelButtonText: '取消',
    type: 'warning',
  }).then(() => router.push('/login')).catch(() => {})
  return false
}

watch(() => authStore.user?.userId || authStore.user?.id, () => {
  loadAgentHistory()
})

onMounted(() => {
  fetchRecommendedProducts()
  loadAgentHistory()
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
              <span>Campus Trade Agent</span>
              <strong>{{ agentMode === 'buyer' ? '可信买家导购' : '开始找商品' }}</strong>
            </div>
            <div class="agent-head-actions">
              <button v-if="hasAgentHistory" class="agent-text-btn" type="button" @click="clearAgentTurns">清空</button>
              <button v-if="agentMode" class="agent-text-btn" type="button" @click="resetAgentMode">切换</button>
            </div>
          </header>

          <div v-if="!agentMode" class="agent-choice-grid">
            <button type="button" @click="openAgent">
              <el-icon><Goods /></el-icon>
              <strong>我要淘货</strong>
              <span>说预算、用途、校区，查询在售商品与可信信息。</span>
            </button>
          </div>

          <section v-if="!agentMode && hasAgentHistory" class="agent-history-list" aria-label="Agent 最近记录">
            <button v-for="record in agentHistory" :key="record.id" type="button" @click="restoreAgentTurn(record)">
              <span>淘货 · {{ agentTurnTime(record) }}</span>
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
                placeholder="例如：想买考研用 iPad，预算 1500，校本部面交"
              />
              <el-button :loading="agentLoading" type="primary" @click="submitAgent">
                <el-icon><Promotion /></el-icon>
                发送
              </el-button>
            </div>

            <p v-if="agentError" class="agent-error">{{ agentError }}</p>

            <section v-if="agentResult?.agent === 'buyer'" class="agent-result-card">
              <div class="agent-result-status">
                <el-tag :type="agentModeLabel === '基础筛选' ? 'warning' : 'success'" effect="plain">{{ agentModeLabel }}</el-tag>
                <small v-if="agentResult.runId">本次记录已保存，可在历史中查看</small>
              </div>
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
                  <el-button text @click="searchSimilarFromAgent(item)">搜相似</el-button>
                </div>
              </article>
              <section v-if="agentTimeline.length" class="agent-timeline" aria-label="本次安全查询步骤">
                <strong>本次安全查询</strong>
                <div v-for="(step, index) in agentTimeline" :key="`${step.tool}-${index}`" class="agent-timeline-row">
                  <span :class="['agent-step-dot', step.status === 'FAILED' ? 'is-failed' : '']"></span>
                  <span>{{ timelineLabel(step) }}</span>
                  <small>{{ step.status === 'FAILED' ? '未完成，已安全降级' : `${step.durationMs || 0} ms` }}</small>
                </div>
              </section>
            </section>
          </div>
        </div>
      </transition>
    </aside>
  </main>
</template>
