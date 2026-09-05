function compactText(value, maxLength = 120) {
  const text = String(value || '').trim().replace(/\s+/g, ' ')
  return text.length > maxLength ? `${text.slice(0, maxLength)}...` : text
}

export function agentTurnTitle(record) {
  return record?.result?.parsed_need?.keyword || '淘货需求'
}

export function agentTurnSummary(record) {
  return compactText(record?.result?.summary || record?.message, 72)
}

export function agentTurnTime(record) {
  if (!record?.createdAt) return ''
  return String(record.createdAt).replace('T', ' ').slice(5, 16)
}
