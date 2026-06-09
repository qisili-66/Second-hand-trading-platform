<script setup>
import { computed, ref, watch } from 'vue'
import { reviewApi } from '../services/api'
import { normalizeReview } from '../services/normalizers'

const props = defineProps({
  userId: {
    type: [Number, String],
    default: null,
  },
  compact: {
    type: Boolean,
    default: false,
  },
})

const loading = ref(false)
const reviews = ref([])
const stats = ref({
  averageRating: 0,
  reviewCount: 0,
  creditScore: 100,
})

const averageRating = computed(() => Number(stats.value.averageRating) || 0)

watch(
  () => props.userId,
  () => fetchReviews(),
  { immediate: true },
)

async function fetchReviews() {
  if (!props.userId) {
    reviews.value = []
    stats.value = { averageRating: 0, reviewCount: 0, creditScore: 100 }
    return
  }

  loading.value = true
  try {
    const [listResponse, statsResponse] = await Promise.all([
      reviewApi.userList(props.userId, { page: 1, pageSize: props.compact ? 3 : 100 }),
      reviewApi.userStats(props.userId),
    ])
    reviews.value = (listResponse.data?.list || []).map(normalizeReview)
    stats.value = statsResponse.data || stats.value
  } catch (error) {
    reviews.value = []
    console.error(error)
  } finally {
    loading.value = false
  }
}
</script>

<template>
  <div class="review-panel" v-loading="loading">
    <div class="review-stats">
      <div>
        <strong>{{ averageRating.toFixed(1) }}</strong>
        <span>平均评分</span>
      </div>
      <div>
        <strong>{{ stats.reviewCount || 0 }}</strong>
        <span>评价数</span>
      </div>
      <div>
        <strong>{{ stats.creditScore || 100 }}</strong>
        <span>信用分</span>
      </div>
      <el-rate :model-value="averageRating" disabled allow-half />
    </div>

    <div v-if="reviews.length > 0" class="review-list">
      <div v-for="review in reviews" :key="review.id" class="review-row">
        <el-image :src="review.item.image" fit="cover" />
        <div>
          <div class="review-title-line">
            <strong>{{ review.item.title || review.orderNo || '交易订单' }}</strong>
            <el-rate :model-value="review.rating" disabled />
          </div>
          <p>{{ review.content || '买家未填写文字评价' }}</p>
          <small>买家：{{ review.reviewerName || '-' }} · {{ review.createdAt }}</small>
        </div>
      </div>
    </div>
    <el-empty v-else description="暂无交易评价" />
  </div>
</template>
