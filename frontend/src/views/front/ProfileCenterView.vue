<script setup>
import { computed, ref, watch } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import { ElMessage, ElMessageBox } from 'element-plus'
import OrderReview from '../../components/OrderReview.vue'
import ProductListItem from '../../components/product/ProductListItem.vue'
import ReviewList from '../../components/ReviewList.vue'
import { itemApi, orderApi, swapApi, userApi, wantedApi } from '../../services/api'
import { normalizeExchangePage, normalizeItemPage, normalizeOrder, normalizePurchasePage } from '../../services/normalizers'
import { useAuthStore } from '../../stores/auth'

const route = useRoute()
const router = useRouter()
const authStore = useAuthStore()
const activeMenu = ref('selling')
const validItemStatusTabs = ['onSale', 'removed', 'sold', 'drafts']
const activeItemStatus = ref(validItemStatusTabs.includes(route.query.itemStatus) ? route.query.itemStatus : 'onSale')
const myProducts = ref([])
const favoriteProducts = ref([])
const orders = ref([])
const notifications = ref([])
const myPurchases = ref([])
const myExchanges = ref([])
const loadingItems = ref(false)
const reviewDialogVisible = ref(false)
const reviewOrder = ref(null)

const menuItems = [
  { key: 'selling', label: '我的在售商品' },
  { key: 'orders', label: '我的订单' },
  { key: 'favorites', label: '我的收藏' },
  { key: 'notifications', label: '系统通知' },
  { key: 'reviews', label: '我的评价' },
  { key: 'wanted', label: '我的求购' },
  { key: 'swap', label: '以物换物' },
  { key: 'privacy', label: '隐私设置' },
]

if (route.query.tab && menuItems.some((item) => item.key === route.query.tab)) {
  activeMenu.value = route.query.tab
}

const privacy = ref({
  phone: false,
  wechat: true,
})

const title = computed(() => menuItems.find((item) => item.key === activeMenu.value)?.label)
const currentUser = computed(() => authStore.user || {})
const currentUserId = computed(() => currentUser.value.userId || currentUser.value.id)
const displayCreditScore = computed(() => Math.min(Math.max(Number(currentUser.value.creditScore) || 0, 0), 100))
const displayName = computed(() => currentUser.value.nickname || currentUser.value.realName || currentUser.value.account)
const avatarText = computed(() => displayName.value?.slice(0, 1) || '用')
const onSaleProducts = computed(() => myProducts.value.filter((product) => product.status === 'ON_SALE'))
const removedProducts = computed(() => myProducts.value.filter((product) => product.status === 'REMOVED'))
const soldProducts = computed(() => myProducts.value.filter((product) => product.status === 'SOLD' || product.status === 'RESERVED'))
const draftProducts = computed(() => myProducts.value.filter((product) => product.status === 'DRAFT'))
const currentStatusProducts = computed(() => {
  if (activeItemStatus.value === 'removed') return removedProducts.value
  if (activeItemStatus.value === 'sold') return soldProducts.value
  if (activeItemStatus.value === 'drafts') return draftProducts.value
  return onSaleProducts.value
})
const currentStatusEmptyText = computed(() => {
  if (activeItemStatus.value === 'removed') return '暂无已下架商品'
  if (activeItemStatus.value === 'sold') return '暂无已出商品'
  if (activeItemStatus.value === 'drafts') return '暂无草稿'
  return '暂无在售商品'
})
const profileLine = computed(() => {
  const parts = [
    currentUser.value.realName ? `实名：${currentUser.value.realName}` : '',
    currentUser.value.department || '',
    currentUser.value.enrollmentYear ? `${currentUser.value.enrollmentYear} 级` : '',
  ].filter(Boolean)

  return parts.length > 0 ? parts.join(' · ') : '暂无实名资料'
})

watch(
  () => authStore.isLoggedIn,
  (loggedIn) => {
    if (loggedIn) fetchUserItems()
  },
  { immediate: true },
)

watch(
  () => route.query,
  (query) => {
    if (query.tab && menuItems.some((item) => item.key === query.tab)) {
      activeMenu.value = query.tab
    }
    if (validItemStatusTabs.includes(query.itemStatus)) {
      activeItemStatus.value = query.itemStatus
    }
  },
  { immediate: true },
)

function goRegister() {
  router.push('/register')
}

function savePrivacy() {
  ElMessage.success('隐私设置已保存')
}

async function fetchUserItems() {
  if (!authStore.isLoggedIn) return

  loadingItems.value = true
  try {
    await syncProfile()
    const [itemsResponse, favoritesResponse, ordersResponse] = await Promise.all([
      userApi.getMyItems({ page: 1, pageSize: 100 }),
      userApi.getMyFavorites({ page: 1, pageSize: 100 }),
      orderApi.list({ page: 1, pageSize: 100 }),
    ])
    myProducts.value = normalizeItemPage(itemsResponse).list
    favoriteProducts.value = normalizeItemPage(favoritesResponse).list
    orders.value = (ordersResponse.data?.list || []).map(normalizeOrder)
    await fetchBazaarItems()
    await fetchNotifications()
  } catch (error) {
    myProducts.value = []
    favoriteProducts.value = []
    orders.value = []
    notifications.value = []
    myPurchases.value = []
    myExchanges.value = []
    console.error(error)
  } finally {
    loadingItems.value = false
  }
}

async function syncProfile() {
  const response = await userApi.getMe()
  if (!response.data) return
  authStore.updateProfile(response.data)
  privacy.value = {
    phone: Boolean(response.data.phoneVisible),
    wechat: Boolean(response.data.wechatVisible),
  }
}

async function savePrivacyToApi() {
  try {
    const response = await userApi.updateMe({
      phoneVisible: privacy.value.phone,
      wechatVisible: privacy.value.wechat,
    })
    if (response.data) {
      authStore.updateProfile(response.data)
    }
    ElMessage.success('Privacy settings saved')
  } catch (error) {
    ElMessage.error(error.message || 'Privacy settings save failed')
  }
}

async function fetchNotifications() {
  try {
    const response = await userApi.getMyNotifications({ page: 1, pageSize: 100 })
    notifications.value = (response.data?.list || []).map((row) => ({
      id: row.notificationId || row.id,
      title: row.title || '系统通知',
      content: row.content || '',
      type: row.type || 'SYSTEM',
      readAt: row.readAt || '',
      createdAt: String(row.createdAt || '').replace('T', ' ').slice(0, 16),
    }))
  } catch (error) {
    notifications.value = []
    console.error(error)
  }
}

async function fetchBazaarItems() {
  try {
    const [purchasesResponse, exchangesResponse] = await Promise.all([
      userApi.getMyPurchases({ page: 1, pageSize: 100 }),
      userApi.getMyExchanges({ page: 1, pageSize: 100 }),
    ])
    myPurchases.value = normalizePurchasePage(purchasesResponse).list
    myExchanges.value = normalizeExchangePage(exchangesResponse).list
  } catch (error) {
    myPurchases.value = []
    myExchanges.value = []
    console.error(error)
  }
}

function statusText(status) {
  const map = {
    ON_SALE: '上架中',
    REMOVED: '已下架',
    SOLD: '已出',
    RESERVED: '已预约',
    DRAFT: '草稿',
  }
  return map[status] || status || '未知'
}

function statusType(status) {
  if (status === 'ON_SALE') return 'success'
  if (status === 'REMOVED') return 'danger'
  if (status === 'SOLD' || status === 'RESERVED') return 'info'
  return 'warning'
}

function bazaarStatusText(status) {
  const map = {
    OPEN: '进行中',
    CLOSED: '已关闭',
    MATCHED: '已换成',
    CANCELLED: '已取消',
  }
  return map[status] || status || '未知'
}

function bazaarStatusType(status) {
  if (status === 'OPEN') return 'success'
  if (status === 'MATCHED') return 'primary'
  return 'info'
}

function orderStatusText(status) {
  const map = {
    PENDING: '待接单',
    ACCEPTED: '待支付',
    PAYING: '支付中',
    PAID: '已支付',
    COMPLETED: '已完成',
    CANCELLED: '已取消',
  }
  return map[status] || status || '未知'
}

function orderStatusType(status) {
  if (status === 'COMPLETED') return 'success'
  if (status === 'CANCELLED') return 'info'
  if (status === 'PAID') return 'success'
  if (status === 'PAYING') return 'warning'
  return 'primary'
}

function notificationTypeText(type) {
  const map = {
    ORDER: '订单信息',
    COMMENT: '留言信息',
    SYSTEM: '系统信息',
  }
  return map[type] || '系统信息'
}

function notificationTagType(type) {
  if (type === 'ORDER') return 'primary'
  if (type === 'COMMENT') return 'success'
  return 'warning'
}

function isBuyer(order) {
  return String(order.buyerId) === String(currentUserId.value)
}

function isSeller(order) {
  return String(order.sellerId) === String(currentUserId.value)
}

function orderRoleText(order) {
  return isSeller(order) ? '我是卖家' : '我是买家'
}

function orderCounterparty(order) {
  return isSeller(order) ? order.buyerName || '买家' : order.sellerName || '卖家'
}

function canAcceptOrder(order) {
  return isSeller(order) && order.status === 'PENDING'
}

function canPayOrder(order) {
  return isBuyer(order) && ['PENDING', 'ACCEPTED', 'PAYING'].includes(order.status)
}

function canCompleteOrder(order) {
  return ['ACCEPTED', 'PAID'].includes(order.status)
}

function canCancelOrder(order) {
  return !['COMPLETED', 'CANCELLED'].includes(order.status)
}

function canReviewOrder(order) {
  return isBuyer(order) && order.status === 'COMPLETED' && !order.reviewedByBuyer
}

async function publishProduct(product) {
  try {
    await itemApi.onShelf(product.id)
    ElMessage.success('商品已上架')
    await fetchUserItems()
    activeItemStatus.value = 'onSale'
  } catch (error) {
    ElMessage.error(error.message || '商品上架失败')
  }
}

async function offShelfProduct(product) {
  try {
    await itemApi.offShelf(product.id)
    ElMessage.success('商品已下架')
    await fetchUserItems()
    activeItemStatus.value = 'removed'
  } catch (error) {
    ElMessage.error(error.message || '商品下架失败')
  }
}

function deleteProduct(product) {
  ElMessageBox.confirm(`确认删除「${product.title}」？删除后不会出现在个人中心。`, '删除商品', {
    confirmButtonText: '确认删除',
    cancelButtonText: '取消',
    type: 'warning',
  }).then(async () => {
    try {
      await itemApi.delete(product.id)
      ElMessage.success('商品已删除')
      await fetchUserItems()
    } catch (error) {
      ElMessage.error(error.message || '商品删除失败')
    }
  }).catch(() => {})
}

function viewProduct(product) {
  router.push(`/items/${product.id}`)
}

function viewExchangeItem(exchange) {
  const itemId = exchange.itemId || exchange.item?.itemId
  if (itemId) router.push(`/items/${itemId}`)
}

async function closePurchase(purchase) {
  try {
    await wantedApi.close(purchase.id)
    ElMessage.success('求购已关闭')
    await fetchBazaarItems()
  } catch (error) {
    ElMessage.error(error.message || '求购关闭失败')
  }
}

async function markExchangeMatched(exchange) {
  try {
    await swapApi.accept(exchange.id)
    ElMessage.success('置换已标记为已换成')
    await fetchBazaarItems()
  } catch (error) {
    ElMessage.error(error.message || '置换状态更新失败')
  }
}

async function cancelExchange(exchange) {
  try {
    await swapApi.cancel(exchange.id)
    ElMessage.success('置换已取消')
    await fetchBazaarItems()
  } catch (error) {
    ElMessage.error(error.message || '置换取消失败')
  }
}

async function acceptOrder(order) {
  try {
    await orderApi.accept(order.id)
    ElMessage.success('已接单')
    await fetchUserItems()
  } catch (error) {
    ElMessage.error(error.message || '接单失败')
  }
}

async function cancelOrder(order) {
  try {
    const { value } = await ElMessageBox.prompt('请填写取消原因', '取消订单', {
      confirmButtonText: '确认取消',
      cancelButtonText: '返回',
      inputPlaceholder: '例如：时间无法协调',
    })
    await orderApi.cancel(order.id, { reason: value || '用户取消' })
    ElMessage.success('订单已取消')
    await fetchUserItems()
  } catch (error) {
    if (error !== 'cancel') ElMessage.error(error.message || '取消失败')
  }
}

async function completeOrder(order) {
  try {
    await orderApi.complete(order.id)
    ElMessage.success('订单已完成')
    await fetchUserItems()
  } catch (error) {
    ElMessage.error(error.message || '完成失败')
  }
}

async function payOrder(order, provider) {
  try {
    const response = await orderApi.pay(order.id, { provider })
    const payment = response.data
    if (payment.paymentUrl) {
      window.location.href = payment.paymentUrl
      return
    }
    if (payment.qrUrl) {
      ElMessageBox.alert(payment.qrUrl, `${provider} 支付二维码链接`, {
        confirmButtonText: '知道了',
      })
      return
    }
    ElMessage.success('支付单已创建')
    await fetchUserItems()
  } catch (error) {
    ElMessage.error(error.message || '支付创建失败')
  }
}

function viewOrderDetail(order) {
  ElMessageBox.alert(
    `订单号：${order.orderNo}\n身份：${orderRoleText(order)}\n对方：${orderCounterparty(order)}\n状态：${orderStatusText(order.status)}\n交易口令：${order.tradeCode || '-'}\n商品：${order.product.title || '-'}`,
    `订单 ${order.orderNo}`,
    { confirmButtonText: '知道了' },
  )
}

function openReview(order) {
  reviewOrder.value = order
  reviewDialogVisible.value = true
}
</script>

<template>
  <main class="page-wrap profile-page">
    <el-card v-if="!authStore.isLoggedIn" class="auth-required-card" shadow="never">
      <h1>还没登录</h1>
      <p>登录或注册后可以查看个人中心、订单、收藏和隐私设置。</p>
      <div class="hero-actions">
        <el-button type="primary" size="large" @click="router.push('/login')">前往登录</el-button>
        <el-button size="large" @click="goRegister">前往注册</el-button>
      </div>
    </el-card>

    <template v-else>
      <el-card class="profile-hero" shadow="never">
        <div class="profile-user">
          <el-avatar :size="82">{{ avatarText }}</el-avatar>
          <div>
            <h1>{{ displayName }}</h1>
            <p>{{ profileLine }}</p>
            <el-rate :model-value="5" disabled show-score :score-template="`信用分 ${displayCreditScore}`" />
          </div>
        </div>
        <div class="profile-stats">
          <div><strong>{{ onSaleProducts.length }}</strong><span>在售商品</span></div>
          <div><strong>{{ soldProducts.length }}</strong><span>已售商品</span></div>
          <div><strong>{{ orders.length }}</strong><span>相关订单</span></div>
          <div><strong>{{ favoriteProducts.length }}</strong><span>收藏商品</span></div>
        </div>
      </el-card>

      <section class="profile-layout">
        <el-card shadow="never" class="profile-menu-card">
          <el-menu v-model="activeMenu" :default-active="activeMenu" @select="activeMenu = $event">
            <el-menu-item v-for="item in menuItems" :key="item.key" :index="item.key">
              {{ item.label }}
            </el-menu-item>
          </el-menu>
        </el-card>

        <el-card shadow="never" class="profile-content" v-loading="loadingItems">
          <template #header>
            <div class="card-header">
              <span>{{ title }}</span>
              <el-tag v-if="activeMenu === 'selling'" type="warning">上架 / 下架 / 已出 / 草稿</el-tag>
              <el-tag v-else-if="activeMenu === 'orders'" type="primary">买家 / 卖家同步</el-tag>
              <el-tag v-else-if="activeMenu === 'reviews'" type="success">交易评价</el-tag>
            </div>
          </template>

          <div v-if="activeMenu === 'selling'" class="status-board">
            <el-tabs v-model="activeItemStatus">
              <el-tab-pane :label="`上架中（${onSaleProducts.length}）`" name="onSale" />
              <el-tab-pane :label="`已下架（${removedProducts.length}）`" name="removed" />
              <el-tab-pane :label="`已出（${soldProducts.length}）`" name="sold" />
              <el-tab-pane :label="`草稿（${draftProducts.length}）`" name="drafts" />
            </el-tabs>

            <div v-if="currentStatusProducts.length > 0" class="profile-item-list">
              <div v-for="product in currentStatusProducts" :key="product.id" class="profile-item-row">
                <el-image class="profile-item-thumb" :src="product.image" fit="cover" />
                <div class="profile-item-main">
                  <div class="profile-item-title">
                    <strong>{{ product.title }}</strong>
                    <el-tag :type="statusType(product.status)">{{ statusText(product.status) }}</el-tag>
                  </div>
                  <p>{{ product.desc }}</p>
                  <div class="profile-item-meta">
                    <span>￥{{ product.price }}</span>
                    <span>{{ product.category }}</span>
                    <span>{{ product.campus }}</span>
                    <span>{{ product.date }}</span>
                  </div>
                </div>
                <div class="profile-item-actions">
                  <el-button
                    v-if="product.status === 'ON_SALE'"
                    type="primary"
                    plain
                    @click="viewProduct(product)"
                  >
                    查看详情
                  </el-button>
                  <el-button
                    v-if="product.status === 'ON_SALE'"
                    type="warning"
                    plain
                    @click="offShelfProduct(product)"
                  >
                    下架
                  </el-button>
                  <el-button
                    v-if="['drafts', 'removed'].includes(activeItemStatus)"
                    type="primary"
                    @click="publishProduct(product)"
                  >
                    发布上架
                  </el-button>
                  <el-button
                    type="danger"
                    plain
                    @click="deleteProduct(product)"
                  >
                    删除
                  </el-button>
                </div>
              </div>
            </div>

            <el-empty v-else :description="currentStatusEmptyText" />
          </div>

          <div v-else-if="activeMenu === 'orders'" class="order-mini-list">
            <div v-if="orders.length > 0" class="profile-order-list">
              <div v-for="order in orders" :key="order.id" class="mini-order">
                <el-image :src="order.product.image" fit="cover" />
                <div class="mini-order-main">
                  <strong>{{ order.product.title || '商品已删除' }}</strong>
                  <p>订单号：{{ order.orderNo }} · {{ order.createdAt }}</p>
                  <p>{{ orderRoleText(order) }} · 对方：{{ orderCounterparty(order) }}</p>
                </div>
                <div class="mini-order-state">
                  <el-tag :type="orderStatusType(order.status)">{{ orderStatusText(order.status) }}</el-tag>
                  <strong>￥{{ order.amount }}</strong>
                </div>
                <div class="mini-order-actions">
                  <el-button size="small" @click="viewOrderDetail(order)">详情</el-button>
                  <el-button v-if="canAcceptOrder(order)" size="small" type="primary" @click="acceptOrder(order)">
                    接单
                  </el-button>
                  <el-dropdown v-if="canPayOrder(order)" @command="payOrder(order, $event)">
                    <el-button size="small" type="success">去支付</el-button>
                    <template #dropdown>
                      <el-dropdown-menu>
                        <el-dropdown-item command="ALIPAY">支付宝</el-dropdown-item>
                        <el-dropdown-item command="WECHAT">微信支付</el-dropdown-item>
                      </el-dropdown-menu>
                    </template>
                  </el-dropdown>
                  <el-button v-if="canCompleteOrder(order)" size="small" type="primary" @click="completeOrder(order)">
                    完成
                  </el-button>
                  <el-button v-if="canReviewOrder(order)" size="small" type="warning" @click="openReview(order)">
                    评价
                  </el-button>
                  <el-button v-if="canCancelOrder(order)" size="small" @click="cancelOrder(order)">取消</el-button>
                </div>
              </div>
            </div>
            <el-empty v-else description="暂无订单" />
          </div>

          <div v-else-if="activeMenu === 'favorites'" class="product-list">
            <ProductListItem v-for="product in favoriteProducts" :key="product.id" :product="product" />
            <el-empty v-if="favoriteProducts.length === 0" description="暂无收藏商品" />
          </div>

          <div v-else-if="activeMenu === 'notifications'" class="notification-list">
            <div v-for="notice in notifications" :key="notice.id" class="notification-row">
              <div>
                <strong>{{ notice.title }}</strong>
                <p>{{ notice.content }}</p>
                <small>{{ notice.createdAt }}</small>
              </div>
              <el-tag :type="notificationTagType(notice.type)" effect="plain">
                {{ notificationTypeText(notice.type) }}
              </el-tag>
            </div>
            <el-empty v-if="notifications.length === 0" description="暂无系统通知" />
          </div>

          <div v-else-if="activeMenu === 'reviews'" class="review-list">
            <ReviewList :user-id="currentUserId" />
          </div>

          <div v-else-if="activeMenu === 'wanted'" class="profile-item-list">
            <div v-for="purchase in myPurchases" :key="purchase.id" class="profile-item-row profile-bazaar-row">
              <div class="profile-bazaar-icon">求</div>
              <div class="profile-item-main">
                <div class="profile-item-title">
                  <strong>{{ purchase.title }}</strong>
                  <el-tag :type="bazaarStatusType(purchase.status)">{{ bazaarStatusText(purchase.status) }}</el-tag>
                </div>
                <p>{{ purchase.description || '暂无补充描述' }}</p>
                <div class="profile-item-meta">
                  <span>{{ purchase.budget }}</span>
                  <span>{{ purchase.categoryName || '未选分类' }}</span>
                  <span>{{ purchase.campus }}</span>
                  <span>{{ purchase.createdAt }}</span>
                </div>
              </div>
              <div class="profile-item-actions">
                <el-button v-if="purchase.status === 'OPEN'" type="warning" plain @click="closePurchase(purchase)">
                  关闭求购
                </el-button>
                <el-button type="primary" plain @click="router.push('/wanted')">去广场</el-button>
              </div>
            </div>
            <el-empty v-if="myPurchases.length === 0" description="暂未发布求购" />
          </div>

          <div v-else-if="activeMenu === 'swap'" class="profile-item-list">
            <div v-for="exchange in myExchanges" :key="exchange.id" class="profile-item-row profile-bazaar-row">
              <el-image class="profile-item-thumb" :src="exchange.image" fit="cover" />
              <div class="profile-item-main">
                <div class="profile-item-title">
                  <strong>{{ exchange.title }}</strong>
                  <el-tag :type="bazaarStatusType(exchange.status)">{{ bazaarStatusText(exchange.status) }}</el-tag>
                </div>
                <p>想换：{{ exchange.expectedTitle }}</p>
                <div class="profile-item-meta">
                  <span>{{ exchange.campus || '不限校区' }}</span>
                  <span>{{ exchange.exchangeNo || '无编号' }}</span>
                  <span>{{ exchange.createdAt }}</span>
                </div>
              </div>
              <div class="profile-item-actions">
                <el-button v-if="exchange.itemId" type="primary" plain @click="viewExchangeItem(exchange)">查看商品</el-button>
                <el-button v-if="exchange.status === 'OPEN'" type="success" plain @click="markExchangeMatched(exchange)">
                  已换成
                </el-button>
                <el-button v-if="exchange.status === 'OPEN'" type="warning" plain @click="cancelExchange(exchange)">取消</el-button>
              </div>
            </div>
            <el-empty v-if="myExchanges.length === 0" description="暂无置换商品" />
          </div>

          <div v-else class="privacy-list">
            <div class="privacy-row">
              <div>
                <strong>手机号公开</strong>
                <p>开启后，交易双方可在订单详情查看手机号。</p>
              </div>
              <el-switch v-model="privacy.phone" @change="savePrivacyToApi" />
            </div>
            <div class="privacy-row">
              <div>
                <strong>QQ / 微信公开</strong>
                <p>开启后，交易双方可在咨询页查看联系方式。</p>
              </div>
              <el-switch v-model="privacy.wechat" @change="savePrivacyToApi" />
            </div>
          </div>
        </el-card>
      </section>
      <OrderReview v-model="reviewDialogVisible" :order="reviewOrder" @submitted="fetchUserItems" />
    </template>
  </main>
</template>
