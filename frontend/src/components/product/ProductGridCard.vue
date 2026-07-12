<script setup>
import { conditionTagMap } from '../../data/mock'

defineProps({
  product: {
    type: Object,
    required: true,
  },
})
</script>

<template>
  <RouterLink class="product-grid-card" :to="`/items/${product.id}`">
    <el-image class="grid-image" :src="product.image" fit="cover" lazy />
    <div class="grid-body">
      <div class="grid-topline">
        <el-tag :type="conditionTagMap[product.condition]" size="small">
          {{ product.condition }}
        </el-tag>
        <span>{{ product.campus }}</span>
      </div>
      <h3>{{ product.title }}</h3>
      <p>{{ product.desc }}</p>
      <div class="grid-price">
        <strong>￥{{ product.price }}</strong>
        <span>￥{{ product.originalPrice }}</span>
      </div>
    </div>
  </RouterLink>
</template>
