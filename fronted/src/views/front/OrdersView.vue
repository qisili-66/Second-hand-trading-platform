<script setup>
import { computed, ref, watch } from 'vue'
import { useRouter } from 'vue-router'
import { ElMessage, ElMessageBox } from 'element-plus'
import OrderReview from '../../components/OrderReview.vue'
import { orderApi } from '../../services/api'
import { normalizeOrder } from '../../services/normalizers'
import { useAuthStore } from '../../stores/auth'

const router = useRouter()
const authStore = useAuthStore()
const activeStatus = ref('ALL')
const orders = ref([])
const loading = ref(false)
const reviewDialogVisible = ref(false)
const reviewOrder = ref(null)
const currentUserId = computed(() => authStore.user?.userId || authStore.user?.id)

const statuses = [
  { label: '全部', value: 'ALL' },
  { label: '待接单', value: 'PENDING' },
  { label: '待支付', value: 'ACCEPTED' },
  { label: '支付中', value: 'PAYING' },
  { label: '已支付', value: 'PAID' },
  { label: '已完成', value: 'COMPLETED' },
  { label: '已取消', value: 'CANCELLED' },
]

const visibleOrders = computed(() =>
  activeStatus.value === 'ALL'
    ? orders.value
    : orders.value.filter((order) => order.status === activeStatus.value),
)

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

watch(
  () => authStore.isLoggedIn,
  (loggedIn) => {
    if (loggedIn) fetchOrders()
  },
  { immediate: true },
)

async function fetchOrders() {
  loading.value = true
  try {
    const response = await orderApi.list({ page: 1, pageSize: 100 })
    const list = response.data?.list || []
    orders.value = list.map(normalizeOrder)
  } catch (error) {
    orders.value = []
    ElMessage.error(error.message || '订单加载失败')
  } finally {
    loading.value = false
  }
}

async function acceptOrder(order) {
  try {
    await orderApi.accept(order.id)
    ElMessage.success('已接单')
    fetchOrders()
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
    fetchOrders()
  } catch (error) {
    if (error !== 'cancel') ElMessage.error(error.message || '取消失败')
  }
}

async function completeOrder(order) {
  try {
    await orderApi.complete(order.id)
    ElMessage.success('订单已完成')
    fetchOrders()
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
    fetchOrders()
  } catch (error) {
    ElMessage.error(error.message || '支付创建失败')
  }
}

function viewDetail(order) {
  ElMessageBox.alert(
    `订单号：${order.orderNo}\n身份：${orderRoleText(order)}\n对方：${orderCounterparty(order)}\n状态：${orderStatusText(order.status)}\n交易口令：${order.tradeCode || '-'}\n商品：${order.product.title}`,
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
  <main class="page-wrap orders-page">
    <el-card v-if="!authStore.isLoggedIn" class="auth-required-card" shadow="never">
      <h1>还没登录</h1>
      <p>登录或注册后可以查看订单、支付和完成交易。</p>
      <div class="hero-actions">
        <el-button type="primary" size="large" @click="router.push('/login')">前往登录</el-button>
        <el-button size="large" @click="router.push('/register')">前往注册</el-button>
      </div>
    </el-card>

    <template v-else>
      <div class="page-title">
        <p class="section-kicker">订单管理</p>
        <h1>我的交易订单</h1>
      </div>

      <el-tabs v-model="activeStatus" class="order-tabs">
        <el-tab-pane v-for="status in statuses" :key="status.value" :label="status.label" :name="status.value">
          <div class="order-list" v-loading="loading">
            <el-card v-for="order in visibleOrders" :key="order.id" class="order-card" shadow="hover">
              <div class="order-head">
                <span>订单号：{{ order.orderNo }}</span>
                <el-tag :type="orderStatusType(order.status)">{{ orderStatusText(order.status) }}</el-tag>
              </div>
              <div class="order-body">
                <el-image :src="order.product.image" fit="cover" />
                <div class="order-info">
                  <h3>{{ order.product.title }}</h3>
                  <p>{{ order.tradeMode }} · {{ order.createdAt }}</p>
                  <p>{{ orderRoleText(order) }} · 对方：{{ orderCounterparty(order) }}</p>
                  <strong>￥{{ order.amount }}</strong>
                </div>
                <div class="trade-code">
                  <span>交易口令</span>
                  <strong>{{ order.tradeCode || '-' }}</strong>
                  <div class="qr-box">PAY</div>
                </div>
                <div class="order-actions">
                  <el-button @click="viewDetail(order)">查看详情</el-button>
                  <el-button v-if="canAcceptOrder(order)" type="primary" @click="acceptOrder(order)">接单</el-button>
                  <el-dropdown v-if="canPayOrder(order)" @command="payOrder(order, $event)">
                    <el-button type="success">去支付</el-button>
                    <template #dropdown>
                      <el-dropdown-menu>
                        <el-dropdown-item command="ALIPAY">支付宝</el-dropdown-item>
                        <el-dropdown-item command="WECHAT">微信支付</el-dropdown-item>
                      </el-dropdown-menu>
                    </template>
                  </el-dropdown>
                  <el-button v-if="canCompleteOrder(order)" type="primary" @click="completeOrder(order)">
                    完成交易
                  </el-button>
                  <el-button v-if="canReviewOrder(order)" type="warning" @click="openReview(order)">
                    评价卖家
                  </el-button>
                  <el-button v-if="canCancelOrder(order)" @click="cancelOrder(order)">
                    取消
                  </el-button>
                </div>
              </div>
            </el-card>
            <el-empty v-if="!loading && visibleOrders.length === 0" description="暂无订单" />
          </div>
        </el-tab-pane>
      </el-tabs>
      <OrderReview v-model="reviewDialogVisible" :order="reviewOrder" @submitted="fetchOrders" />
    </template>
  </main>
</template>
