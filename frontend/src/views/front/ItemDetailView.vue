<script setup>
import { ref, watch } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import { ElMessage, ElMessageBox } from 'element-plus'
import { ChatDotRound, Flag, StarFilled } from '@element-plus/icons-vue'
import ProductGridCard from '../../components/product/ProductGridCard.vue'
import ReviewList from '../../components/ReviewList.vue'
import { conditionTagMap } from '../../data/mock'
import { chatApi, itemApi, orderApi } from '../../services/api'
import { normalizeComment, normalizeItem, normalizeItemPage } from '../../services/normalizers'
import { useAuthStore } from '../../stores/auth'

const route = useRoute()
const router = useRouter()
const authStore = useAuthStore()
const comment = ref('')
const favorited = ref(false)
const loading = ref(false)
const loadingRelated = ref(false)
const submittingComment = ref(false)
const product = ref(normalizeItem({}))
const comments = ref([])
const relatedProducts = ref([])

watch(
  () => route.params.itemId,
  () => {
    fetchDetail()
  },
  { immediate: true },
)

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

async function fetchDetail() {
  loading.value = true
  product.value = normalizeItem({})
  comments.value = []
  relatedProducts.value = []
  try {
    const response = await itemApi.detail(route.params.itemId)
    product.value = normalizeItem(response.data)
    await Promise.all([fetchComments(), fetchRelatedProducts()])
  } catch (error) {
    product.value = normalizeItem({})
    comments.value = []
    relatedProducts.value = []
    ElMessage.error(error.message || '商品详情加载失败')
  } finally {
    loading.value = false
  }
}

async function fetchComments() {
  try {
    const response = await itemApi.comments(route.params.itemId)
    comments.value = Array.isArray(response.data) ? response.data.map(normalizeComment) : []
  } catch (error) {
    comments.value = []
    console.error(error)
  }
}

async function fetchRelatedProducts() {
  if (!product.value.campus) {
    relatedProducts.value = []
    return
  }

  loadingRelated.value = true
  try {
    const response = await itemApi.list({
      campus: product.value.campus,
      page: 1,
      pageSize: 8,
    })
    relatedProducts.value = normalizeItemPage(response).list
      .filter((item) => String(item.id) !== String(product.value.id))
      .slice(0, 3)
  } catch (error) {
    relatedProducts.value = []
    console.error(error)
  } finally {
    loadingRelated.value = false
  }
}

async function toggleFavorite() {
  if (!requireLogin('收藏商品')) return

  try {
    if (favorited.value) {
      await itemApi.unfavorite(product.value.id)
      favorited.value = false
      ElMessage.success('已取消收藏')
    } else {
      await itemApi.favorite(product.value.id)
      favorited.value = true
      ElMessage.success('已加入我的收藏')
    }
  } catch (error) {
    ElMessage.error(error.message || '收藏操作失败')
  }
}

async function consultSeller() {
  if (!requireLogin('咨询卖家')) return
  try {
    const response = await chatApi.create({ itemId: product.value.id })
    router.push({ path: '/chats', query: { chatId: response.data.chatId } })
  } catch (error) {
    ElMessage.error(error.message || '会话创建失败')
  }
}

function reserveItem() {
  if (!requireLogin('预约商品')) return
  ElMessageBox.confirm(`确认预约《${product.value.title}》？`, '预约商品', {
    confirmButtonText: '确认预约',
    cancelButtonText: '取消',
    type: 'warning',
  })
    .then(async () => {
      try {
        await orderApi.create({
          itemId: product.value.id,
          tradeMode: 'ONLINE',
          message: '我想预约这个商品',
        })
        ElMessage.success('预约成功，请在订单页查看并支付')
        router.push('/orders')
      } catch (error) {
        ElMessage.error(error.message || '预约失败')
      }
    })
    .catch(() => {})
}

function reportItem() {
  if (!requireLogin('举报商品')) return
  ElMessageBox.prompt('请填写举报原因，管理员会在后台审核。', '举报商品', {
    confirmButtonText: '提交举报',
    cancelButtonText: '取消',
    inputPlaceholder: '例如：虚假商品、描述不符、欺诈风险',
  })
    .then(async ({ value }) => {
      if (!value?.trim()) {
        ElMessage.warning('举报原因不能为空')
        return
      }
      ElMessage.success('举报已提交，等待管理员审核')
    })
    .catch(() => {})
}

function reportItemToApi() {
  if (!requireLogin('Report item')) return
  ElMessageBox.prompt('Please enter the report reason.', 'Report item', {
    confirmButtonText: 'Submit',
    cancelButtonText: 'Cancel',
    inputPlaceholder: 'Fake item, misleading description, fraud risk...',
  })
    .then(async ({ value }) => {
      if (!value?.trim()) {
        ElMessage.warning('Report reason cannot be empty')
        return
      }
      await itemApi.report(product.value.id, {
        reportType: 'ITEM',
        content: value.trim(),
      })
      ElMessage.success('Report submitted')
    })
    .catch(() => {})
}

async function submitComment() {
  if (!requireLogin('发表留言')) return
  if (!comment.value.trim()) {
    ElMessage.warning('请输入留言内容')
    return
  }

  submittingComment.value = true
  try {
    await itemApi.createComment(product.value.id, { content: comment.value.trim() })
    comment.value = ''
    await fetchComments()
    ElMessage.success('留言已发表')
  } catch (error) {
    ElMessage.error(error.message || '留言发表失败')
  } finally {
    submittingComment.value = false
  }
}
</script>

<template>
  <main class="page-wrap detail-page" v-loading="loading">
    <el-breadcrumb v-if="product.id" separator="/">
      <el-breadcrumb-item :to="{ path: '/' }">首页</el-breadcrumb-item>
      <el-breadcrumb-item>{{ product.category }}</el-breadcrumb-item>
      <el-breadcrumb-item>{{ product.title }}</el-breadcrumb-item>
    </el-breadcrumb>

    <section v-if="product.id" class="detail-main">
      <div class="detail-gallery">
        <el-carousel height="430px" indicator-position="outside">
          <el-carousel-item v-for="image in product.imageUrls" :key="image">
            <el-image :src="image" fit="cover" />
          </el-carousel-item>
        </el-carousel>
      </div>

      <el-card class="detail-info" shadow="never">
        <h1>{{ product.title }}</h1>
        <p class="detail-desc">{{ product.desc }}</p>
        <div class="price-row">
          <strong>￥{{ product.price }}</strong>
          <span>原价 ￥{{ product.originalPrice }}</span>
        </div>
        <div class="tag-row">
          <el-tag :type="conditionTagMap[product.condition]" size="large">{{ product.condition }}</el-tag>
          <el-tag effect="plain">{{ product.category }}</el-tag>
          <el-tag effect="plain">{{ product.campus }} · {{ product.dorm }}</el-tag>
        </div>

        <div class="seller-card">
          <el-avatar :size="52">{{ product.seller.slice(0, 1) }}</el-avatar>
          <div>
            <h3>{{ product.seller }}</h3>
            <p>信用分 {{ product.credit }}</p>
          </div>
          <el-tag type="success" effect="dark">高信用</el-tag>
        </div>

        <div class="detail-actions">
          <el-button size="large" :type="favorited ? 'warning' : 'default'" :icon="StarFilled" @click="toggleFavorite">
            {{ favorited ? '已收藏' : '收藏' }}
          </el-button>
          <el-button size="large" type="primary" :icon="ChatDotRound" @click="consultSeller">
            立即咨询
          </el-button>
          <el-button size="large" type="warning" @click="reserveItem">预约商品</el-button>
          <el-button size="large" :icon="Flag" @click="reportItemToApi">举报</el-button>
        </div>
      </el-card>
    </section>

    <section v-if="product.id" class="detail-sections">
      <el-card shadow="never">
        <template #header>商品详细描述</template>
        <p class="long-text">{{ product.desc }}</p>
      </el-card>

      <el-card shadow="never">
        <template #header>商品留言</template>
        <div v-if="comments.length > 0" class="comment-list">
          <div v-for="item in comments" :key="item.id" class="comment-item">
            <strong>{{ item.user }}</strong>
            <p>{{ item.text }}</p>
            <div class="seller-reply">{{ item.createdAt }}</div>
          </div>
        </div>
        <el-empty v-else description="暂无留言" />
        <div class="comment-input">
          <el-input v-model="comment" placeholder="向卖家留言咨询" />
          <el-button type="primary" :loading="submittingComment" @click="submitComment">
            发表留言
          </el-button>
        </div>
      </el-card>

      <el-card shadow="never">
        <template #header>卖家评价</template>
        <ReviewList :user-id="product.sellerId" compact />
      </el-card>

      <el-card shadow="never">
        <template #header>同校区相关推荐</template>
        <div class="related-products-panel" v-loading="loadingRelated">
          <div v-if="relatedProducts.length > 0" class="product-grid three">
            <ProductGridCard
              v-for="item in relatedProducts"
              :key="item.id"
              :product="item"
            />
          </div>
          <el-empty v-else description="暂无同校区真实商品推荐" />
        </div>
      </el-card>
    </section>

    <el-empty v-else-if="!loading" description="商品不存在、已下架或仍在草稿箱">
      <el-button type="primary" @click="router.push('/items')">返回商品列表</el-button>
    </el-empty>
  </main>
</template>
