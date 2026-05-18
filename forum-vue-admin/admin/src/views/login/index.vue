<template>
  <el-config-provider :locale="zhCn">
    <div class="admin-login-page">
      <AdminParticleSea />

      <div class="admin-login-card animate__animated animate__fadeIn">
        <div class="admin-login-left" />

        <div class="admin-login-right">
          <el-button
            class="admin-login-theme-btn"
            circle
            :aria-label="isDark ? '切换为浅色' : '切换为深色'"
            @click="toggleTheme"
          >
            <el-icon>
              <Sunny v-if="!isDark" />
              <Moon v-else />
            </el-icon>
          </el-button>

          <header class="admin-login-brand">
            <el-icon>
              <CircleCheck />
            </el-icon>
            <span>萌萌论坛 · 管理后台</span>
          </header>

          <el-form
            ref="formRef"
            class="admin-login-form"
            :model="form"
            :rules="rules"
            label-position="top"
            size="large"
            @submit.prevent="login"
          >
            <el-form-item label="账号" prop="username">
              <el-input
                v-model="form.username"
                placeholder="管理员账号"
                clearable
                autocomplete="username"
              >
                <template #prefix>
                  <el-icon>
                    <User />
                  </el-icon>
                </template>
              </el-input>
            </el-form-item>

            <el-form-item label="密码" prop="password">
              <el-input
                v-model="form.password"
                type="password"
                placeholder="登录密码"
                show-password
                clearable
                autocomplete="current-password"
                @keyup.enter="login"
              >
                <template #prefix>
                  <el-icon>
                    <Lock />
                  </el-icon>
                </template>
              </el-input>
            </el-form-item>

            <div class="admin-login-remember">
              <el-checkbox v-model="remember">
                记住密码
              </el-checkbox>
            </div>

            <el-form-item>
              <el-button
                type="primary"
                class="admin-login-submit"
                :loading="loading"
                native-type="submit"
                @click="login"
              >
                登 录
              </el-button>
            </el-form-item>
          </el-form>
        </div>

        <a
          class="admin-login-back-front"
          :href="frontSignInUrl"
          target="_blank"
          rel="noopener noreferrer"
          title="回到用户端登录"
        >
          <img :src="backToFrontIcon" alt="回到用户端登录">
        </a>
      </div>
    </div>
  </el-config-provider>
</template>

<script setup lang="ts">
import { CircleCheck, Lock, Moon, Sunny, User } from '@element-plus/icons-vue'
import {
  ElButton,
  ElCheckbox,
  ElConfigProvider,
  ElForm,
  ElFormItem,
  ElIcon,
  ElInput,
  ElMessage
} from 'element-plus'
import zhCn from 'element-plus/es/locale/lang/zh-cn'
import type { FormInstance, FormRules } from 'element-plus'
import AdminParticleSea from '@/components/AdminParticleSea.vue'
import backToFrontIcon from '@/assets/svg/回到用户端登录.svg'
import { useLoading, useTheme } from '@/hooks'
import { useTabsStore, useUserStore } from '@/stores'
import 'element-plus/dist/index.css'
import '@/styles/admin-login.css'

defineOptions({ name: 'Login' })

const router = useRouter()
const userStore = useUserStore()
const tabsStore = useTabsStore()
const { isDark, toggleTheme } = useTheme()

const frontSignInUrl = import.meta.env.VITE_FRONT_SIGN_IN_URL || 'http://localhost:5173/sign-in'

const form = reactive({
  username: '',
  password: ''
})

const rules: FormRules = {
  username: [{ required: true, message: '请输入账号', trigger: 'blur' }],
  password: [
    { required: true, message: '请输入密码', trigger: 'blur' },
    { min: 6, message: '密码至少为 6 位', trigger: 'blur' }
  ]
}

const formRef = useTemplateRef<FormInstance>('formRef')
const { loading, setLoading } = useLoading()
const remember = ref(false)

const login = async () => {
  if (!formRef.value)
    return
  try {
    await formRef.value.validate()
  } catch {
    return
  }

  try {
    setLoading(true)
    await userStore.login(form)
    tabsStore.reset()
    const { redirect, ...othersQuery } = router.currentRoute.value.query
    router.push({
      path: (redirect as string) || '/',
      query: { ...othersQuery }
    })
    ElMessage.success('登录成功')
  } catch (error) {
    ElMessage.error((error as Error).message || '登录失败')
  } finally {
    setLoading(false)
  }
}
</script>
