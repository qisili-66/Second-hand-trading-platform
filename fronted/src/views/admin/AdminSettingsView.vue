<script setup>
import { onMounted, reactive, ref } from 'vue'
import { ElMessage } from 'element-plus'
import { adminApi } from '../../services/api'

const words = ref([])
const newWord = ref('')
const loading = ref(false)
const savingPayment = ref(false)
const savingRules = ref(false)

const payment = reactive({
  wechatAppId: '',
  alipayAppId: '',
  campusCardMerchant: '',
})

const rules = reactive({
  maxImages: 9,
  disputeDays: 3,
  creditDeduction: 10,
  tradeTip: '',
})

function parseJson(value, fallback = {}) {
  try {
    return value ? JSON.parse(value) : fallback
  } catch {
    return fallback
  }
}

async function loadSettings() {
  loading.value = true
  try {
    const response = await adminApi.settings()
    const data = response.data || {}
    words.value = Array.isArray(data.sensitiveWords) ? data.sensitiveWords : []
    const wechat = parseJson(data.payment_wechat)
    const alipay = parseJson(data.payment_alipay)
    const campusCard = parseJson(data.payment_campus_card)
    const tradeRules = parseJson(data.trade_rules)
    payment.wechatAppId = wechat.appId || ''
    payment.alipayAppId = alipay.appId || ''
    payment.campusCardMerchant = campusCard.merchant || ''
    rules.maxImages = Number(tradeRules.maxImages) || 9
    rules.disputeDays = Number(tradeRules.disputeDays) || 3
    rules.creditDeduction = Number(tradeRules.creditDeduction) || 10
    rules.tradeTip = tradeRules.tradeTip || ''
  } catch (error) {
    ElMessage.error(error.message || 'Settings load failed')
  } finally {
    loading.value = false
  }
}

async function saveSettings(payload, successText) {
  await adminApi.updateSettings(payload)
  ElMessage.success(successText)
  await loadSettings()
}

async function addWord() {
  const word = newWord.value.trim()
  if (!word) {
    ElMessage.warning('Sensitive word is required')
    return
  }
  try {
    await saveSettings({ sensitiveWords: [...new Set([...words.value, word])] }, 'Sensitive word added')
    newWord.value = ''
  } catch (error) {
    ElMessage.error(error.message || 'Sensitive word add failed')
  }
}

async function removeWord(word) {
  try {
    await saveSettings({ sensitiveWords: words.value.filter((item) => item !== word) }, 'Sensitive word deleted')
  } catch (error) {
    ElMessage.error(error.message || 'Sensitive word delete failed')
  }
}

async function savePayment() {
  savingPayment.value = true
  try {
    await saveSettings({ payment: { ...payment } }, 'Payment settings saved')
  } catch (error) {
    ElMessage.error(error.message || 'Payment settings save failed')
  } finally {
    savingPayment.value = false
  }
}

async function saveRules() {
  savingRules.value = true
  try {
    await saveSettings({ rules: { ...rules } }, 'Trade rules saved')
  } catch (error) {
    ElMessage.error(error.message || 'Trade rules save failed')
  } finally {
    savingRules.value = false
  }
}

onMounted(loadSettings)
</script>

<template>
  <div v-loading="loading" class="admin-page settings-grid">
    <el-card shadow="never">
      <template #header>Sensitive Words</template>
      <div class="word-add-row">
        <el-input v-model="newWord" placeholder="New sensitive word" />
        <el-button type="primary" @click="addWord">Add</el-button>
      </div>
      <div class="tag-stack">
        <el-tag v-for="word in words" :key="word" closable type="warning" @close="removeWord(word)">
          {{ word }}
        </el-tag>
      </div>
    </el-card>

    <el-card shadow="never">
      <template #header>Payment Settings</template>
      <el-form label-position="top">
        <el-form-item label="Wechat AppID">
          <el-input v-model="payment.wechatAppId" />
        </el-form-item>
        <el-form-item label="Alipay AppID">
          <el-input v-model="payment.alipayAppId" />
        </el-form-item>
        <el-form-item label="Campus Card Merchant">
          <el-input v-model="payment.campusCardMerchant" />
        </el-form-item>
        <el-button type="primary" :loading="savingPayment" @click="savePayment">Save Payment</el-button>
      </el-form>
    </el-card>

    <el-card shadow="never" class="settings-wide">
      <template #header>Trade Rules</template>
      <el-form label-position="top">
        <div class="admin-filter-grid">
          <el-form-item label="Max Images">
            <el-input-number v-model="rules.maxImages" :min="1" :max="12" />
          </el-form-item>
          <el-form-item label="Dispute Days">
            <el-input-number v-model="rules.disputeDays" :min="1" :max="15" />
          </el-form-item>
          <el-form-item label="Credit Deduction">
            <el-input-number v-model="rules.creditDeduction" :min="1" :max="100" />
          </el-form-item>
        </div>
        <el-form-item label="Trade Tip">
          <el-input v-model="rules.tradeTip" type="textarea" :rows="4" />
        </el-form-item>
        <el-button type="primary" :loading="savingRules" @click="saveRules">Save Rules</el-button>
      </el-form>
    </el-card>
  </div>
</template>
