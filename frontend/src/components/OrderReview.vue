<script setup>
import { computed, reactive, watch } from 'vue'
import { ElMessage } from 'element-plus'
import { orderApi } from '../services/api'

const props = defineProps({
  modelValue: {
    type: Boolean,
    default: false,
  },
  order: {
    type: Object,
    default: null,
  },
})

const emit = defineEmits(['update:modelValue', 'submitted'])
const submitting = computed(() => form.loading)
const visible = computed({
  get: () => props.modelValue,
  set: (value) => emit('update:modelValue', value),
})
const form = reactive({
  rating: 5,
  content: '',
  loading: false,
})

watch(
  () => props.modelValue,
  (open) => {
    if (open) {
      form.rating = 5
      form.content = ''
      form.loading = false
    }
  },
)

async function submitReview() {
  if (!props.order?.id) return
  if (form.rating < 1 || form.rating > 5) {
    ElMessage.warning('请选择 1-5 星评分')
    return
  }
  if (form.content.length > 500) {
    ElMessage.warning('评价内容不能超过 500 字')
    return
  }

  form.loading = true
  try {
    await orderApi.review(props.order.id, {
      rating: form.rating,
      content: form.content,
    })
    ElMessage.success('评价已提交')
    emit('submitted')
    visible.value = false
  } catch (error) {
    ElMessage.error(error.message || '评价提交失败')
  } finally {
    form.loading = false
  }
}
</script>

<template>
  <el-dialog v-model="visible" title="评价卖家" width="460px">
    <div v-if="order" class="order-review-dialog">
      <div class="review-order-summary">
        <el-image :src="order.product.image" fit="cover" />
        <div>
          <strong>{{ order.product.title || '订单商品' }}</strong>
          <p>订单号：{{ order.orderNo }}</p>
          <p>卖家：{{ order.sellerName || '-' }}</p>
        </div>
      </div>

      <el-form label-position="top">
        <el-form-item label="评分">
          <el-rate v-model="form.rating" />
        </el-form-item>
        <el-form-item label="文字评价">
          <el-input
            v-model="form.content"
            type="textarea"
            :rows="4"
            maxlength="500"
            show-word-limit
            placeholder="描述交易体验、商品状态或沟通情况"
          />
        </el-form-item>
      </el-form>
    </div>
    <template #footer>
      <el-button @click="visible = false">取消</el-button>
      <el-button type="primary" :loading="submitting" @click="submitReview">提交评价</el-button>
    </template>
  </el-dialog>
</template>
