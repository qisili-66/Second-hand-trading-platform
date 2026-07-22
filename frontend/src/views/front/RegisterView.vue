<script setup>
import { reactive, ref } from 'vue'
import { useRouter } from 'vue-router'
import { ElMessage } from 'element-plus'
import { authApi } from '../../services/api'

const router = useRouter()
const submitting = ref(false)

const registerForm = reactive({
  studentNo: '',
  realName: '',
  major: '',
  year: '',
  email: '',
  password: '',
  phoneVisible: false,
  wechatVisible: false,
})

function verifyEmail() {
  if (!registerForm.email.trim()) {
    ElMessage.warning('请先输入校园邮箱')
    return
  }
  ElMessage.success('校园邮箱验证邮件已发送')
}

async function submitRegister() {
  const requiredFields = [
    ['学号', registerForm.studentNo],
    ['姓名', registerForm.realName],
    ['院系专业', registerForm.major],
    ['入学年份', registerForm.year],
    ['校园邮箱', registerForm.email],
    ['密码', registerForm.password],
  ]

  const missing = requiredFields.find(([, value]) => !String(value).trim())
  if (missing) {
    ElMessage.warning(`请填写${missing[0]}`)
    return
  }

  if (registerForm.password.length < 8) {
    ElMessage.warning('密码至少 8 位')
    return
  }

  submitting.value = true
  try {
    await authApi.register({
      studentNo: registerForm.studentNo.trim(),
      realName: registerForm.realName.trim(),
      nickname: registerForm.realName.trim(),
      department: registerForm.major.trim(),
      enrollmentYear: registerForm.year,
      email: registerForm.email.trim(),
      password: registerForm.password,
      phoneVisible: registerForm.phoneVisible,
      wechatVisible: registerForm.wechatVisible,
    })
    ElMessage.success('注册成功，请登录')
    router.push('/login')
  } catch (error) {
    ElMessage.error(error.message || '注册失败')
  } finally {
    submitting.value = false
  }
}
</script>

<template>
  <main class="page-wrap auth-page simple-auth-page">
    <section class="auth-layout auth-layout-single auth-layout-wide">
      <el-card class="auth-card simple-auth-card register-card" shadow="never">
        <div class="simple-auth-title">
          <p>校园二手闲置网</p>
          <h1>注册</h1>
        </div>

        <el-form label-position="top">
          <div class="two-column-form">
            <el-form-item label="学号">
              <el-input v-model="registerForm.studentNo" placeholder="请输入学号" />
            </el-form-item>
            <el-form-item label="姓名">
              <el-input v-model="registerForm.realName" placeholder="实名信息绑定" />
            </el-form-item>
            <el-form-item label="院系专业">
              <el-input v-model="registerForm.major" placeholder="例如：计算机科学与技术" />
            </el-form-item>
            <el-form-item label="入学年份">
              <el-select v-model="registerForm.year" placeholder="选择年份">
                <el-option label="2026" value="2026" />
                <el-option label="2025" value="2025" />
                <el-option label="2024" value="2024" />
                <el-option label="2023" value="2023" />
              </el-select>
            </el-form-item>
            <el-form-item label="校园邮箱">
              <div class="inline-control">
                <el-input v-model="registerForm.email" placeholder="name@school.edu.cn" />
                <el-button @click="verifyEmail">验证</el-button>
              </div>
            </el-form-item>
            <el-form-item label="密码">
              <el-input v-model="registerForm.password" show-password placeholder="至少 8 位" />
            </el-form-item>
          </div>

          <div class="privacy-checks">
            <el-checkbox v-model="registerForm.phoneVisible">手机号对买家/卖家公开</el-checkbox>
            <el-checkbox v-model="registerForm.wechatVisible">微信/QQ 对买家/卖家公开</el-checkbox>
          </div>

          <el-button
            type="primary"
            size="large"
            class="full-button register-submit"
            :loading="submitting"
            @click="submitRegister"
          >
            注册并完成实名绑定
          </el-button>
        </el-form>

        <div class="auth-switch-line">
          <span>已经注册？</span>
          <RouterLink to="/login">前往登录</RouterLink>
        </div>
      </el-card>
    </section>
  </main>
</template>
