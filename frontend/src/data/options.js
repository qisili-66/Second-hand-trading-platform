export const campuses = ['校本部', '东校区', '西校区', '南校区', '大学城校区']

export const fallbackCategories = [
  '教材教辅',
  '数码3C',
  '生活日用',
  '服饰鞋包',
  '运动户外',
  '其他',
]

export const conditions = ['全新', '9成新', '8成新', '轻微使用', '明显使用']

export const conditionTagMap = {
  全新: 'success',
  '9成新': 'warning',
  '8成新': 'primary',
  轻微使用: 'info',
  明显使用: 'danger',
  NEW: 'success',
  LIKE_NEW: 'warning',
  GOOD: 'primary',
  FAIR: 'info',
}

export function categoryNames(response) {
  const rows = Array.isArray(response?.data) ? response.data : Array.isArray(response) ? response : []
  const names = rows
    .map((category) => category?.name || category?.categoryName || category)
    .filter(Boolean)

  return names.length > 0 ? names : fallbackCategories
}
