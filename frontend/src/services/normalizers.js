const fallbackImage =
  'https://images.unsplash.com/photo-1516321318423-f06f85e504b3?auto=format&fit=crop&w=640&q=80'

function numberValue(value) {
  const number = Number(value)
  return Number.isFinite(number) ? number : 0
}

function dateText(value) {
  if (!value) return ''
  return String(value).replace('T', ' ').slice(0, 16)
}

export function normalizeItem(item = {}) {
  const category = item.category || {}
  const seller = item.seller || {}
  const imageUrls = Array.isArray(item.imageUrls) ? item.imageUrls : []
  const coverUrl = item.coverUrl || imageUrls[0] || item.image || fallbackImage

  return {
    id: item.itemId ?? item.id,
    itemId: item.itemId ?? item.id,
    sellerId: seller.userId ?? item.sellerId,
    title: item.title || '',
    desc: item.description || item.desc || '',
    description: item.description || item.desc || '',
    price: numberValue(item.price),
    originalPrice: numberValue(item.originalPrice),
    condition: item.condition || item.conditionLevel || '',
    conditionCode: item.condition || item.conditionLevel || '',
    campus: item.campus || seller.campus || '',
    dorm: item.dormitory || item.tradePlace || '',
    tradePlace: item.tradePlace || '',
    category: category.name || item.categoryName || item.category || '',
    categoryId: category.categoryId || item.categoryId,
    date: dateText(item.createdAt),
    hot: numberValue(item.viewCount),
    favoriteCount: numberValue(item.favoriteCount),
    distance: '',
    swap: Boolean(item.swapSupported),
    image: coverUrl,
    imageUrls: imageUrls.length ? imageUrls : [coverUrl],
    seller: seller.nickname || (seller.userId ? `用户${seller.userId}` : ''),
    credit: 100,
    status: item.itemStatus || item.status || 'ON_SALE',
    statusText: item.itemStatus || item.status || '',
  }
}

export function normalizeItemPage(response = {}) {
  const data = response.data || response
  return {
    list: Array.isArray(data.list) ? data.list.map(normalizeItem) : [],
    page: Number(data.page) || 1,
    pageSize: Number(data.pageSize) || 10,
    total: Number(data.total) || 0,
  }
}

export function normalizeComment(comment = {}) {
  const user = comment.user || {}
  return {
    id: comment.commentId || comment.id,
    user: user.nickname || (comment.userId ? `用户${comment.userId}` : ''),
    text: comment.content || '',
    content: comment.content || '',
    createdAt: dateText(comment.createdAt),
  }
}

export function normalizeOrder(order = {}) {
  const item = order.item || {}
  const buyer = order.buyer || {}
  const seller = order.seller || {}
  return {
    id: order.orderId || order.id,
    orderId: order.orderId || order.id,
    orderNo: order.orderNo || '',
    status: order.orderStatus || order.status || '',
    message: order.message || '',
    tradeMode: order.tradeMode || '',
    tradeCode: order.tradeCode || '',
    amount: numberValue(item.price ?? order.amount),
    product: {
      id: item.itemId || order.itemId,
      title: item.title || '',
      image: item.coverUrl || fallbackImage,
      campus: item.campus || '',
      dorm: '',
    },
    buyer,
    seller,
    buyerId: buyer.userId ?? order.buyerId,
    sellerId: seller.userId ?? order.sellerId,
    buyerName: buyer.nickname || (buyer.userId ? `用户${buyer.userId}` : ''),
    sellerName: seller.nickname || (seller.userId ? `用户${seller.userId}` : ''),
    reviewedByBuyer: Boolean(order.reviewedByBuyer),
    payments: order.payments || [],
    statusLogs: order.statusLogs || [],
    createdAt: dateText(order.createdAt),
  }
}

export function normalizeReview(review = {}) {
  const item = review.item || {}
  const reviewer = review.reviewer || review.user || {}
  const targetUser = review.targetUser || {}
  return {
    id: review.reviewId || review.commentId || review.id,
    orderId: review.orderId,
    orderNo: review.orderNo || '',
    rating: numberValue(review.rating),
    content: review.content || '',
    createdAt: dateText(review.createdAt),
    reviewerId: review.reviewerId || reviewer.userId || review.userId,
    reviewerName: review.reviewerName || reviewer.nickname || (review.reviewerId ? `用户${review.reviewerId}` : ''),
    targetUserId: review.targetUserId || targetUser.userId || review.sellerId,
    targetUserName: review.targetUserName || targetUser.nickname || review.sellerName || '',
    relation: review.relation || '',
    item: {
      id: review.itemId || item.itemId,
      title: review.itemTitle || item.title || '',
      image: review.coverUrl || item.coverUrl || fallbackImage,
    },
  }
}

export function normalizeChat(chat = {}, currentUserId) {
  const buyer = chat.buyer || {}
  const seller = chat.seller || {}
  const peer = String(buyer.userId) === String(currentUserId) ? seller : buyer
  const item = chat.item || {}
  return {
    id: chat.chatId || chat.id,
    chatId: chat.chatId || chat.id,
    name: peer.nickname || `用户${peer.userId || ''}`,
    peer,
    buyer,
    seller,
    last: chat.lastMessage || '',
    unread: 0,
    item: {
      id: item.itemId || chat.itemId,
      title: item.title || '',
      image: item.coverUrl || fallbackImage,
    },
    updatedAt: dateText(chat.updatedAt),
  }
}

export function normalizeMessage(message = {}, currentUserId) {
  return {
    id: message.messageId || message.id,
    from: String(message.senderId) === String(currentUserId) ? 'me' : 'other',
    senderId: message.senderId,
    text: message.content || '',
    messageType: message.messageType || 'TEXT',
    imageUrl: message.imageUrl || '',
    time: dateText(message.createdAt),
  }
}
