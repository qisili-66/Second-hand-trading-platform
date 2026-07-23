const STORAGE_PREFIX = 'campus-agent-history'
const DRAFT_PREFIX = 'campus-agent-draft'
const MAX_RECORDS = 20

function userKey(userId) {
  return `${STORAGE_PREFIX}:${userId || 'guest'}`
}

function draftKey(userId) {
  return `${DRAFT_PREFIX}:${userId || 'guest'}`
}

function parseJson(raw, fallback) {
  try {
    return JSON.parse(raw) ?? fallback
  } catch (error) {
    return fallback
  }
}

function safeParse(raw) {
  const value = parseJson(raw, [])
  return Array.isArray(value) ? value : []
}

function makeId() {
  if (globalThis.crypto?.randomUUID) return globalThis.crypto.randomUUID()
  return `${Date.now()}-${Math.random().toString(16).slice(2)}`
}

function compactText(value, maxLength = 120) {
  const text = String(value || '').trim().replace(/\s+/g, ' ')
  return text.length > maxLength ? `${text.slice(0, maxLength)}...` : text
}

export function agentHistoryUserId(user) {
  return user?.userId || user?.id || ''
}

export function readAgentHistory(userId) {
  const records = safeParse(localStorage.getItem(userKey(userId)))
  return records.filter((record) => record?.id && record?.mode && record?.message && record?.result)
}

export function saveAgentTurn(userId, turn) {
  const record = {
    id: makeId(),
    mode: turn.mode,
    message: compactText(turn.message, 1000),
    result: turn.result,
    createdAt: new Date().toISOString(),
  }
  const next = [...readAgentHistory(userId), record].slice(-MAX_RECORDS)
  localStorage.setItem(userKey(userId), JSON.stringify(next))
  return next
}

export function clearAgentHistory(userId) {
  localStorage.removeItem(userKey(userId))
  return []
}

export function readAgentDraft(userId) {
  const draft = parseJson(localStorage.getItem(draftKey(userId)), null)
  if (!draft || !draft.mode || typeof draft.message !== 'string') return null
  return {
    mode: draft.mode,
    message: draft.message,
    updatedAt: draft.updatedAt || '',
  }
}

export function saveAgentDraft(userId, draft) {
  if (!draft?.mode && !draft?.message) {
    clearAgentDraft(userId)
    return null
  }
  const next = {
    mode: draft.mode || '',
    message: String(draft.message || '').slice(0, 1000),
    updatedAt: new Date().toISOString(),
  }
  localStorage.setItem(draftKey(userId), JSON.stringify(next))
  return next
}

export function clearAgentDraft(userId) {
  localStorage.removeItem(draftKey(userId))
  return null
}

export function agentTurnTitle(record) {
  if (record.mode === 'buyer') return record.result?.parsed_need?.keyword || '淘货需求'
  return record.result?.draft?.title || '发布草稿'
}

export function agentTurnSummary(record) {
  if (record.mode === 'buyer') return compactText(record.result?.summary || record.message, 72)
  return compactText(record.result?.draft?.description || record.message, 72)
}

export function agentTurnTime(record) {
  if (!record?.createdAt) return ''
  return String(record.createdAt).replace('T', ' ').slice(5, 16)
}
