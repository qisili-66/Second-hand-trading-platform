<script setup>
import { computed, onMounted, reactive, ref } from 'vue'
import { useRouter } from 'vue-router'
import { ElMessage } from 'element-plus'
import { UploadFilled } from '@element-plus/icons-vue'
import { campuses, categoryNames, conditions, fallbackCategories } from '../../data/options'
import { categoryApi, itemApi } from '../../services/api'
import { useAuthStore } from '../../stores/auth'

const router = useRouter()
const authStore = useAuthStore()
const fileList = ref([])
const submitting = ref(false)
const savingDraft = ref(false)
const categoryOptions = ref(fallbackCategories)
const uploadHeaders = computed(() => {
  const token = localStorage.getItem('accessToken')
  return token ? { Authorization: `Bearer ${token}` } : {}
})

const form = reactive({
  title: '',
  desc: '',
  price: null,
  originalPrice: null,
  condition: '',
  category: '',
  campus: '',
  dorm: '',
  tradeModes: ['面交'],
  swapSupported: false,
})

async function fetchCategories() {
  try {
    categoryOptions.value = categoryNames(await categoryApi.list())
  } catch (error) {
    categoryOptions.value = fallbackCategories
    console.error(error)
  }
}

function formatText(command) {
  ElMessage.info(`${command}功能已记录，后续接入富文本编辑器`)
}

function validateForm() {
  if (!form.title.trim()) return '请填写商品标题'
  if (!form.desc.trim()) return '请填写详细描述'
  if (!form.price && form.price !== 0) return '请填写售价'
  if (!form.condition) return '请选择成色'
  if (!form.category) return '请选择商品分类'
  if (!form.campus) return '请选择校区'
  if (!form.tradeModes.length) return '至少选择一种交易模式'
  return ''
}

function imageUrls() {
  return fileList.value
    .map((file) => file.response?.data?.url || file.response?.url || file.url || '')
    .filter((url) => url && !url.startsWith('blob:') && !url.startsWith('data:'))
    .filter(Boolean)
}

function hasUploadingImages() {
  return fileList.value.some((file) => file.status === 'uploading')
}

function beforeImageUpload(file) {
  if (!file.type?.startsWith('image/')) {
    ElMessage.warning('只能上传图片文件')
    return false
  }
  const maxSizeMb = 10
  if (file.size / 1024 / 1024 > maxSizeMb) {
    ElMessage.warning(`图片不能超过 ${maxSizeMb}MB`)
    return false
  }
  return true
}

function handleUploadSuccess(response, uploadFile) {
  if (!response?.data?.url) {
    ElMessage.warning(`${uploadFile.name} 上传成功，但没有返回图片地址`)
  }
}

function handleUploadError(error, uploadFile) {
  console.error(error)
  ElMessage.error(`${uploadFile.name} 上传失败，请重试`)
}

async function saveItem(status) {
  if (!authStore.isLoggedIn) {
    router.push('/login')
    return
  }

  const error = validateForm()
  if (error) {
    ElMessage.warning(error)
    return
  }
  if (hasUploadingImages()) {
    ElMessage.warning('图片还在上传，请稍后再保存')
    return
  }

  const isDraft = status === '草稿'
  if (isDraft) savingDraft.value = true
  else submitting.value = true

  try {
    const response = await itemApi.create({
      title: form.title.trim(),
      description: form.desc.trim(),
      price: form.price,
      originalPrice: form.originalPrice,
      condition: form.condition,
      category: form.category,
      campus: form.campus,
      dormitory: Array.isArray(form.dorm) ? form.dorm.join('/') : form.dorm,
      tradeModes: form.tradeModes,
      swapSupported: form.swapSupported,
      status,
      imageUrls: imageUrls(),
    })
    if (isDraft) {
      ElMessage.success('草稿已保存')
      router.push({ path: '/profile', query: { tab: 'selling', itemStatus: 'drafts' } })
    } else {
      ElMessage.success('商品已发布')
      router.push(`/items/${response.data.itemId}`)
    }
  } catch (error) {
    ElMessage.error(error.message || (isDraft ? '草稿保存失败' : '商品发布失败'))
  } finally {
    if (isDraft) savingDraft.value = false
    else submitting.value = false
  }
}

function saveDraft() {
  saveItem('草稿')
}

function submitItem() {
  saveItem('上架')
}

onMounted(async () => {
  await fetchCategories()
})
</script>

<template>
  <main class="page-wrap publish-page">
    <el-card v-if="!authStore.isLoggedIn" class="auth-required-card" shadow="never">
      <h1>还没登录</h1>
      <p>登录或注册后才能发布校园闲置商品。</p>
      <div class="hero-actions">
        <el-button type="primary" size="large" @click="router.push('/login')">前往登录</el-button>
        <el-button size="large" @click="router.push('/register')">前往注册</el-button>
      </div>
    </el-card>

    <template v-else>
      <div class="page-title">
        <p class="section-kicker">免费发布</p>
        <h1>发布校园闲置商品</h1>
      </div>

      <el-form label-position="top" class="publish-form">
        <el-card shadow="never" class="form-card">
          <template #header>多图上传</template>
          <el-upload
            v-model:file-list="fileList"
            action="/api/files/images"
            :headers="uploadHeaders"
            drag
            multiple
            :limit="9"
            list-type="picture-card"
            accept="image/*"
            :before-upload="beforeImageUpload"
            :on-success="handleUploadSuccess"
            :on-error="handleUploadError"
          >
            <el-icon class="el-icon--upload"><UploadFilled /></el-icon>
            <div class="el-upload__text">拖拽图片到此处，或点击上传</div>
          </el-upload>
        </el-card>

        <el-card shadow="never" class="form-card">
          <template #header>基础信息</template>
          <el-form-item label="商品标题">
            <el-input v-model="form.title" maxlength="40" show-word-limit placeholder="例如：高等数学教材九成新" />
          </el-form-item>
          <el-form-item label="详细描述">
            <div class="rich-editor">
              <div class="rich-toolbar">
                <el-button size="small" @click="formatText('加粗')">加粗</el-button>
                <el-button size="small" @click="formatText('清单')">清单</el-button>
                <el-button size="small" @click="formatText('插图')">插图</el-button>
              </div>
              <el-input
                v-model="form.desc"
                type="textarea"
                :rows="7"
                placeholder="描述购买时间、使用情况、瑕疵、配件、可交易时间"
              />
            </div>
          </el-form-item>

          <div class="two-column-form">
            <el-form-item label="售价">
              <el-input-number v-model="form.price" :min="0" :precision="2" controls-position="right" />
            </el-form-item>
            <el-form-item label="原价">
              <el-input-number v-model="form.originalPrice" :min="0" :precision="2" controls-position="right" />
            </el-form-item>
            <el-form-item label="成色">
              <el-select v-model="form.condition" placeholder="选择成色">
                <el-option v-for="condition in conditions" :key="condition" :label="condition" :value="condition" />
              </el-select>
            </el-form-item>
          </div>
        </el-card>

        <el-card shadow="never" class="form-card">
          <template #header>属性选择</template>
          <div class="two-column-form">
            <el-form-item label="商品分类">
              <el-select v-model="form.category" placeholder="选择分类">
                <el-option v-for="category in categoryOptions" :key="category" :label="category" :value="category" />
              </el-select>
            </el-form-item>
            <el-form-item label="校区">
              <el-select v-model="form.campus" placeholder="选择校区">
                <el-option v-for="campus in campuses" :key="campus" :label="campus" :value="campus" />
              </el-select>
            </el-form-item>
            <el-form-item label="宿舍楼 / 教学楼">
              <el-input v-model="form.dorm" placeholder="例如：桃李园 3 栋" />
            </el-form-item>
          </div>
        </el-card>

        <el-card shadow="never" class="form-card">
          <template #header>交易设置</template>
          <el-form-item label="交易模式">
            <el-checkbox-group v-model="form.tradeModes">
              <el-checkbox label="面交" value="面交" />
              <el-checkbox label="线上担保" value="线上担保" />
            </el-checkbox-group>
          </el-form-item>
          <el-form-item label="置换意愿">
            <el-switch v-model="form.swapSupported" active-text="接受以物换物" inactive-text="只出售" />
          </el-form-item>
        </el-card>

        <div class="sticky-submit">
          <el-button size="large" :loading="savingDraft" :disabled="submitting" @click="saveDraft">保存草稿</el-button>
          <el-button type="primary" size="large" :loading="submitting" @click="submitItem">
            提交发布
          </el-button>
        </div>
      </el-form>
    </template>
  </main>
</template>
