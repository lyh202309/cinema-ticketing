<script setup>
import { ref } from 'vue'
import { useRouter, useRoute } from 'vue-router'
import { ElMessage } from 'element-plus'
import { login, sendCode, getMe } from '../api/user'
import { useUserStore } from '../stores/user'

const router = useRouter()
const route = useRoute()
const store = useUserStore()

const mode = ref('code') // code=验证码 / password=密码
const form = ref({ phone: '', code: '', password: '' })
const sending = ref(false)
const submitting = ref(false)

async function onSendCode() {
  if (!/^1\d{10}$/.test(form.value.phone)) {
    return ElMessage.warning('请输入正确的手机号')
  }
  sending.value = true
  try {
    await sendCode(form.value.phone)
    ElMessage.success('验证码已发送（测试环境请查看后端控制台日志）')
  } finally {
    sending.value = false
  }
}

async function onSubmit() {
  const { phone, code, password } = form.value
  if (!/^1\d{10}$/.test(phone)) return ElMessage.warning('请输入正确的手机号')
  if (mode.value === 'code' && !code) return ElMessage.warning('请输入验证码')
  if (mode.value === 'password' && !password) return ElMessage.warning('请输入密码')

  submitting.value = true
  try {
    const token = await login(mode.value === 'code' ? { phone, code } : { phone, password })
    store.setLogin(token, null)
    const user = await getMe()
    store.setUser(user)
    ElMessage.success('登录成功')
    router.push(route.query.redirect || '/')
  } catch (e) {
    /* 错误已由拦截器提示 */
  } finally {
    submitting.value = false
  }
}
</script>

<template>
  <div class="login-wrap">
    <el-card class="login-card">
      <h2 class="title">影院订票系统</h2>
      <el-tabs v-model="mode">
        <el-tab-pane label="验证码登录" name="code">
          <el-form label-width="0">
            <el-form-item>
              <el-input v-model="form.phone" maxlength="11" placeholder="手机号" />
            </el-form-item>
            <el-form-item>
              <div style="display:flex; width:100%; gap:8px">
                <el-input v-model="form.code" placeholder="验证码" />
                <el-button :loading="sending" @click="onSendCode">获取验证码</el-button>
              </div>
            </el-form-item>
          </el-form>
        </el-tab-pane>
        <el-tab-pane label="密码登录" name="password">
          <el-form label-width="0">
            <el-form-item>
              <el-input v-model="form.phone" maxlength="11" placeholder="手机号" />
            </el-form-item>
            <el-form-item>
              <el-input v-model="form.password" type="password" show-password placeholder="密码" />
            </el-form-item>
          </el-form>
        </el-tab-pane>
      </el-tabs>
      <el-button type="primary" style="width:100%" :loading="submitting" @click="onSubmit">
        登录
      </el-button>
      <p class="tip">测试账号：13800138000 / 123456</p>
    </el-card>
  </div>
</template>

<style scoped>
.login-wrap { display: flex; justify-content: center; padding-top: 12vh; }
.login-card { width: 380px; padding: 8px 12px; }
.title { text-align: center; margin-bottom: 8px; color: #e64d2e; }
.tip { margin-top: 12px; text-align: center; color: #999; font-size: 12px; }
</style>
