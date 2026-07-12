// Keep this file aligned with backend/sql/02_seed_data.sql.

import { campuses, categories, categoryTags, products } from './mock.js'

const onSaleProducts = products.filter((product) => product.status === 'ON_SALE')

export const adminStats = [
  { label: '总用户数', value: '0', trend: '普通用户未初始化' },
  { label: '今日新增用户', value: '0', trend: '无普通用户账号' },
  { label: '在售商品总量', value: String(onSaleProducts.length), trend: '来自 items 表' },
  { label: '今日交易额', value: '￥0.00', trend: 'orders 表已清空' },
  { label: '活跃用户数', value: '0', trend: 'notifications 表已清空' },
]

export const adminUsers = []

export const adminProducts = products.map((product) => ({
  id: product.id,
  title: product.title,
  category: product.category,
  seller: product.seller,
  campus: product.campus,
  price: product.price,
  status: product.statusText,
  publishedAt: product.date,
}))

export const adminCategories = categories.map((category, index) => ({
  id: index + 1,
  name: category,
  tags: categoryTags.filter((tag) => tag.category === category).map((tag) => tag.name),
  productCount: products.filter((product) => product.category === category).length,
}))

export const adminOrders = []
export const adminSwapRequests = []
export const adminDisputes = []
export const adminReports = []

export const sensitiveWords = [
  '私下转账',
  '押金',
  '脱离平台',
  '先付款',
  '加微信交易',
  '绕过平台',
  '定金不退',
  '银行卡转账',
  '虚拟币',
  '不走平台',
]

export const campusDistribution = campuses.map((campus) => ({
  campus,
  count: products.filter((product) => product.campus === campus).length,
}))
