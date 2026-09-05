<script setup>
import { computed, onMounted, reactive, ref, watch } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import { ElMessage, ElMessageBox } from 'element-plus'
import ProductGridCard from '../../components/product/ProductGridCard.vue'
import { campuses, categoryNames, fallbackCategories } from '../../data/options'
import { categoryApi, chatApi, itemApi, swapApi, userApi, wantedApi } from '../../services/api'
import { normalizeExchange, normalizeExchangePage, normalizeItem, normalizeItemPage, normalizePurchasePage } from '../../services/normalizers'
import { useAuthStore } from '../../stores/auth'

const route = useRoute()
const router = useRouter()
const authStore = useAuthStore()
const activeTab = ref(route.meta.tab || 'wanted')
const wantedItems = ref([])
const exchanges = ref([])
const seasonProducts = ref([])
const categoryOptions = ref(fallbackCategories)
const loading = ref(false)
const swapDialogVisible = ref(false)
const swapSubmitting = ref(false)
const myItemsLoading = ref(false)
const myItems = ref([])
const swapMatches = ref([])
const matchExchangeId = ref(null)

const swapForm = reactive({
  itemId: null,
  targetCategory: '',
  expectedTitle: '',
  description: '',
  campus: '',
})

const activeExchangeMap = computed(() => new Map(exchanges.value.map((exchange) => [String(exchange.id), exchange])))
const selectedSwapItem = computed(() => myItems.value.find((item) => String(item.id) === String(swapForm.itemId)))

watch(
  () => route.meta.tab,
  (tab) => {
    activeTab.value = tab || 'wanted'
  },
)

function changeTab(tab) {
  const paths = {
    wanted: '/wanted',
    swap: '/swap',
    season: '/season',
  }
  router.push(paths[tab])
}

function requireLogin(actionText) {
  if (authStore.isLoggedIn) return true

  ElMessageBox.confirm(`还没登录，${actionText}需要先登录或注册。`, '需要登录', {
    confirmButtonText: '前往登录',
    cancelButtonText: '前往注册',
    distinguishCancelAndClose: true,
    type: 'warning',
  })
    .then(() => router.push('/login'))
    .catch((action) => {
      if (action === 'cancel') router.push('/register')
    })
  return false
}

function goPublish() {
  if (!requireLogin('发布闲置')) return
  router.push('/items/publish')
}

async function fetchCategories() {
  try {
    categoryOptions.value = categoryNames(await categoryApi.list())
  } catch (error) {
    categoryOptions.value = fallbackCategories
    console.error(error)
  }
}

function publishWanted(draft = null) {
  if (!requireLogin('发布求购')) return
  ElMessageBox.prompt('请输入要求购的物品名称。', draft ? '确认 Agent 求购草稿' : '发布求购', {
    confirmButtonText: '发布',
    cancelButtonText: '取消',
    inputPlaceholder: '例如：求购二手自行车',
    inputValue: draft?.title || '',
  }).then(async ({ value }) => {
    if (!value?.trim()) {
      ElMessage.warning('求购物品不能为空')
      return
    }
    await wantedApi.create({
      title: value.trim(),
      description: draft?.description || '',
      campus: draft?.campus || authStore.user?.campus || '校本部',
      budgetMin: draft?.budget_min || null,
      budgetMax: draft?.budget_max || null,
    })
    ElMessage.success('求购已发布')
    loadBazaar()
  }).catch(() => {})
}

async function contactItem(item, draft = '') {
  if (!requireLogin('联系发布人')) return
  const itemId = item?.itemId || item?.id
  if (!itemId) {
    ElMessage.warning('缺少商品信息，暂时不能联系')
    return
  }

  try {
    const response = await chatApi.create({ itemId })
    const chat = response.data || response
    if (draft) {
      await chatApi.sendMessage(chat.chatId || chat.id, { content: draft })
    }
    router.push('/chats')
  } catch (error) {
    ElMessage.error(error.message || '会话创建失败')
  }
}

function contactUser(item) {
  if (!requireLogin('联系发布人')) return
  ElMessage.success(`已打开与 ${item.user} 的咨询会话`)
  router.push('/chats')
}

async function loadBazaar() {
  loading.value = true
  try {
    const [wantedResponse, exchangeResponse, itemResponse] = await Promise.all([
      wantedApi.list({ page: 1, pageSize: 20 }),
      swapApi.list({ page: 1, pageSize: 20 }),
      itemApi.list({ page: 1, pageSize: 6, sort: 'latest' }),
    ])
    wantedItems.value = normalizePurchasePage(wantedResponse).list
    exchanges.value = normalizeExchangePage(exchangeResponse).list
    seasonProducts.value = normalizeItemPage(itemResponse).list
  } catch (error) {
    ElMessage.error(error.message || '广场数据加载失败')
  } finally {
    loading.value = false
  }
}

async function loadMyItems() {
  if (!authStore.isLoggedIn) return
  myItemsLoading.value = true
  try {
    const response = await userApi.getMyItems({ page: 1, pageSize: 100 })
    myItems.value = normalizeItemPage(response).list.filter((item) => item.status === 'ON_SALE')
  } catch (error) {
    myItems.value = []
    ElMessage.error(error.message || '我的商品加载失败')
  } finally {
    myItemsLoading.value = false
  }
}

function resetSwapForm() {
  swapForm.itemId = null
  swapForm.targetCategory = ''
  swapForm.expectedTitle = ''
  swapForm.description = ''
  swapForm.campus = authStore.user?.campus || '校本部'
  swapMatches.value = []
}

async function openSwapDialog(draft = null) {
  if (!requireLogin('发布换物')) return
  resetSwapForm()
  if (draft) {
    swapForm.targetCategory = draft.target_category || draft.category || ''
    swapForm.expectedTitle = draft.expected_title || draft.title || ''
    swapForm.description = draft.description || ''
    swapForm.campus = draft.campus || swapForm.campus
  }
  swapDialogVisible.value = true
  await loadMyItems()
  if (myItems.value.length === 1) {
    swapForm.itemId = myItems.value[0].id
  }
}

async function submitSwap() {
  if (!swapForm.itemId) {
    ElMessage.warning('请选择拿来交换的商品')
    return
  }
  if (!swapForm.expectedTitle.trim() && !swapForm.targetCategory) {
    ElMessage.warning('请填写想换什么，或选择目标分类')
    return
  }

  swapSubmitting.value = true
  try {
    const response = await swapApi.create({
      itemId: swapForm.itemId,
      expectedTitle: swapForm.expectedTitle.trim() || `${swapForm.targetCategory} 相关物品`,
      targetCategory: swapForm.targetCategory || null,
      description: swapForm.description.trim(),
      campus: swapForm.campus || selectedSwapItem.value?.campus || authStore.user?.campus || '校本部',
    })
    const payload = response.data || response
    const created = normalizeExchange(payload)
    swapMatches.value = (payload.recommendedItems || []).map(normalizeItem)
    ElMessage.success('换物需求已发布')
    await loadBazaar()
    if (created.id) {
      matchExchangeId.value = String(created.id)
    }
  } catch (error) {
    ElMessage.error(error.message || '换物发布失败')
  } finally {
    swapSubmitting.value = false
  }
}

async function showMatches(exchange) {
  if (!exchange?.id) return
  matchExchangeId.value = String(exchange.id)
  try {
    const response = await swapApi.matches(exchange.id)
    const rows = response.data || response || []
    swapMatches.value = Array.isArray(rows) ? rows.map(normalizeItem) : []
    if (swapMatches.value.length === 0) {
      ElMessage.info('暂时没有新的匹配商品')
    }
  } catch (error) {
    ElMessage.error(error.message || '匹配推荐加载失败')
  }
}

async function cancelSwap(exchange) {
  if (!exchange?.id) return
  if (!requireLogin('取消换物')) return
  try {
    await swapApi.cancel(exchange.id)
    ElMessage.success('换物已取消')
    await loadBazaar()
  } catch (error) {
    ElMessage.error(error.message || '取消失败')
  }
}

async function completeSwap(exchange) {
  if (!exchange?.id) return
  if (!requireLogin('标记换物完成')) return
  try {
    await swapApi.accept(exchange.id)
    ElMessage.success('已标记为换物成功')
    await loadBazaar()
  } catch (error) {
    ElMessage.error(error.message || '操作失败')
  }
}

function isMyExchange(exchange) {
  const userId = authStore.user?.userId || authStore.user?.id
  return userId && String(exchange.userId) === String(userId)
}

function swapMessage(exchange, item) {
  return `同学你好，我看到你发布的《${item.title}》支持置换。我这边想用《${exchange.title}》交换，需求是：${exchange.expectedTitle}。方便的话可以聊聊成色、配件和面交时间。`
}

function exchangeContactMessage(exchange) {
  return `同学你好，我看到你发布的换物《${exchange.title}》，想换：${exchange.expectedTitle}。方便的话可以聊聊成色、配件、补差价和面交时间。`
}

async function contactExchangeOwner(exchange) {
  if (!exchange?.itemId) {
    ElMessage.warning('缺少换物商品信息，暂时不能联系')
    return
  }
  await contactItem({ ...(exchange.item || {}), id: exchange.itemId, itemId: exchange.itemId }, exchangeContactMessage(exchange))
}

onMounted(() => {
  fetchCategories()
  loadBazaar()
})
</script>

<template>
  <main class="page-wrap bazaar-page">
    <section class="season-banner">
      <div>
        <p class="section-kicker">开学 / 毕业季专题</p>
        <h1>急售甩卖、求购补齐、以物换物集中看</h1>
      </div>
      <el-button type="primary" size="large" @click="goPublish">发布闲置</el-button>
    </section>

    <el-tabs v-model="activeTab" v-loading="loading" class="bazaar-tabs" @tab-change="changeTab">
      <el-tab-pane label="求购广场" name="wanted">
        <div class="section-head">
          <div>
            <p class="section-kicker">同学正在找</p>
            <h2>求购列表</h2>
          </div>
          <el-button type="primary" @click="publishWanted">发布求购</el-button>
        </div>
        <div class="wanted-grid">
          <el-card v-for="item in wantedItems" :key="item.id" shadow="hover">
            <h3>{{ item.title }}</h3>
            <p>需求校区：{{ item.campus }}</p>
            <p>预算：{{ item.budget }}</p>
            <div class="card-footer-line">
              <span>{{ item.user }}</span>
              <el-button text type="primary" @click="contactUser(item)">联系 TA</el-button>
            </div>
          </el-card>
          <el-empty v-if="wantedItems.length === 0" description="暂无求购" />
        </div>
      </el-tab-pane>

      <el-tab-pane label="以物换物" name="swap">
        <div class="section-head">
          <div>
            <p class="section-kicker">支持置换</p>
            <h2>以物换物专区</h2>
          </div>
          <el-button type="primary" @click="openSwapDialog()">发布换物</el-button>
        </div>

        <div class="swap-guide-grid">
          <div>
            <strong>1. 选自己的在售商品</strong>
            <span>只有自己发布且仍在售的商品可以拿来换。</span>
          </div>
          <div>
            <strong>2. 写清想换什么</strong>
            <span>可以指定目标分类，也可以写具体物品名。</span>
          </div>
          <div>
            <strong>3. 看匹配后联系对方</strong>
            <span>系统按支持置换、分类、校区和关键词推荐。</span>
          </div>
        </div>

        <div class="wanted-grid swap-grid">
          <el-card v-for="exchange in exchanges" :key="exchange.id" shadow="hover" class="swap-card">
            <div class="swap-card-main">
              <el-image :src="exchange.image" fit="cover" />
              <div>
                <h3>{{ exchange.title }}</h3>
                <p>想换：{{ exchange.expectedTitle }}</p>
                <p>交易校区：{{ exchange.campus || '不限' }}</p>
                <p v-if="exchange.description">补充：{{ exchange.description }}</p>
              </div>
            </div>
            <div class="card-footer-line swap-actions">
              <span>{{ exchange.user }}</span>
              <div>
                <el-button text type="primary" @click="showMatches(exchange)">看匹配</el-button>
                <el-button v-if="isMyExchange(exchange)" text @click="completeSwap(exchange)">标记完成</el-button>
                <el-button v-if="isMyExchange(exchange)" text type="danger" @click="cancelSwap(exchange)">取消</el-button>
                <el-button v-else text type="primary" @click="contactExchangeOwner(exchange)">联系换物人</el-button>
                <el-tag type="warning" effect="plain">置换中</el-tag>
              </div>
            </div>
          </el-card>
          <el-empty v-if="exchanges.length === 0" description="暂无置换" />
        </div>

        <section v-if="swapMatches.length > 0" class="swap-match-panel">
          <div class="section-head compact">
            <div>
              <p class="section-kicker">匹配推荐</p>
              <h2>可以优先联系的可换商品</h2>
            </div>
          </div>
          <div class="swap-match-list">
            <article v-for="item in swapMatches" :key="item.id" class="swap-match-item">
              <el-image :src="item.image" fit="cover" />
              <div>
                <strong>{{ item.title }}</strong>
                <p>{{ item.campus || '不限校区' }} · ￥{{ item.price }}</p>
                <small v-if="item.matchReasons?.length">{{ item.matchReasons.join(' / ') }}</small>
              </div>
              <div class="swap-match-actions">
                <el-button text type="primary" @click="router.push(`/items/${item.id}`)">看详情</el-button>
                <el-button
                  v-if="matchExchangeId && activeExchangeMap.get(matchExchangeId)"
                  text
                  @click="contactItem(item, swapMessage(activeExchangeMap.get(matchExchangeId), item))"
                >
                  联系交换
                </el-button>
              </div>
            </article>
          </div>
        </section>
      </el-tab-pane>

      <el-tab-pane label="毕业季专题" name="season">
        <div class="section-head">
          <div>
            <p class="section-kicker">毕业清仓</p>
            <h2>急售 / 甩卖商品</h2>
          </div>
        </div>
        <div class="product-grid">
          <ProductGridCard v-for="product in seasonProducts" :key="product.id" :product="product" />
        </div>
      </el-tab-pane>
    </el-tabs>

    <el-dialog v-model="swapDialogVisible" title="发布换物" width="640px" destroy-on-close>
      <el-form label-position="top" class="swap-form">
        <el-form-item label="我拿来交换的商品">
          <el-select v-model="swapForm.itemId" v-loading="myItemsLoading" filterable placeholder="选择自己正在出售的商品">
            <el-option
              v-for="item in myItems"
              :key="item.id"
              :label="`${item.title} · ￥${item.price}`"
              :value="item.id"
            />
          </el-select>
          <p v-if="!myItemsLoading && myItems.length === 0" class="form-hint">你还没有可用于置换的在售商品，可以先发布一件闲置。</p>
        </el-form-item>

        <div class="two-column-form">
          <el-form-item label="想换什么">
            <el-input v-model="swapForm.expectedTitle" maxlength="60" show-word-limit placeholder="例如：考研英语资料 / 机械键盘" />
          </el-form-item>
          <el-form-item label="目标分类">
            <el-select v-model="swapForm.targetCategory" clearable placeholder="不限分类">
              <el-option v-for="category in categoryOptions" :key="category" :label="category" :value="category" />
            </el-select>
          </el-form-item>
        </div>

        <el-form-item label="交易校区">
          <el-select v-model="swapForm.campus" placeholder="选择校区">
            <el-option v-for="campus in campuses" :key="campus" :label="campus" :value="campus" />
          </el-select>
        </el-form-item>

        <el-form-item label="补充说明">
          <el-input
            v-model="swapForm.description"
            type="textarea"
            :rows="4"
            maxlength="300"
            show-word-limit
            placeholder="说明可补差价、可接受的成色、配件要求、方便交易时间"
          />
        </el-form-item>
      </el-form>

      <template #footer>
        <el-button @click="swapDialogVisible = false">取消</el-button>
        <el-button @click="goPublish">先发布商品</el-button>
        <el-button type="primary" :loading="swapSubmitting" @click="submitSwap">发布并匹配</el-button>
      </template>
    </el-dialog>
  </main>
</template>
