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
  <RouterLink class="product-list-item" :to="`/items/${product.id}`">
    <el-image class="product-thumb" :src="product.image" fit="cover" lazy />

    <div class="product-main">
      <div class="product-title-row">
        <h3>{{ product.title }}</h3>
        <el-tag v-if="product.swap" type="warning" effect="plain">支持置换</el-tag>
      </div>
      <p>{{ product.desc }}</p>
      <div class="product-meta">
        <strong>￥{{ product.price }}</strong>
        <span>原价 ￥{{ product.originalPrice }}</span>
        <el-tag :type="conditionTagMap[product.condition]" effect="light">
          {{ product.condition }}
        </el-tag>
      </div>
    </div>

    <div class="product-side">
      <span>{{ product.campus }}</span>
      <small>{{ product.dorm }}</small>
      <small>{{ product.date }}</small>
    </div>
  </RouterLink>
</template>
