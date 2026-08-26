<template>
  <div ref="pageRef" class="privacy-page policy-page--light shell-page-scroll">
    <ParticleSea />
    <div class="privacy-container animate-fade-up">
      <main class="privacy-content">
        <article class="policy-card policy-card--privacy">
          <div class="policy-card__inner-frame" aria-hidden="true" />
          <div class="policy-card__glow policy-card__glow--tr" aria-hidden="true" />
          <div class="policy-card__glow policy-card__glow--bl" aria-hidden="true" />
          <div class="policy-card__ribbon policy-card__ribbon--left" aria-hidden="true" />
          <div class="policy-card__ribbon policy-card__ribbon--right" aria-hidden="true" />
          <div class="policy-card__dot policy-card__dot--left" aria-hidden="true" />
          <div class="policy-card__dot policy-card__dot--right" aria-hidden="true" />

          <header class="policy-card__header">
            <h1 class="policy-card__title">会员服务协议</h1>
            <span class="policy-card__date">最近更新：2026-08-09</span>
          </header>
          <div class="policy-card__rule" aria-hidden="true" />

          <section v-for="(section, index) in policyData" :key="index" class="policy-section">
            <h2 class="section-title">{{ section.title }}</h2>
            <div class="section-body" v-html="section.content"></div>
          </section>

          <section class="policy-section contact-section">
            <h2 class="section-title">联系我们</h2>
            <p>如有会员服务相关问题，可通过以下方式联系我们：</p>
            <div class="contact-methods">
              <div class="admin-qr">
                <div class="qr-item">
                  <div class="qr-item__meta">
                    <div class="qr-item__channel">
                      <img class="qr-item__icon qr-item__icon--wechat" :src="wechatIcon" alt="">
                      <span>微信</span>
                    </div>
                    <p class="qr-item__copy">网站作者微信，有问题和建议随时联系</p>
                  </div>
                  <div class="qr-wrapper">
                    <img :src="wechatImg" alt="微信">
                  </div>
                </div>
                <div class="qr-item">
                  <div class="qr-item__meta">
                    <div class="qr-item__channel">
                      <img class="qr-item__icon qr-item__icon--qq" :src="qqIcon" alt="">
                      <span>QQ</span>
                    </div>
                    <p class="qr-item__copy">网站作者QQ，有问题和建议随时联系</p>
                  </div>
                  <div class="qr-wrapper">
                    <img :src="qqImg" alt="QQ">
                  </div>
                </div>
              </div>
            </div>
          </section>

          <button
            type="button"
            class="policy-back-btn"
            aria-label="返回首页"
            title="返回首页"
            @click="$router.push('/')"
          >
            <el-icon :size="20"><Back /></el-icon>
          </button>
        </article>
      </main>
    </div>
  </div>
</template>

<script setup>
import { ref } from 'vue'
import { Back } from '@element-plus/icons-vue'
import ParticleSea from '@/components/common/ParticleSea.vue'
import { useVipAgreement } from '@scripts/views/VipAgreement'
import { clientOssUrl } from '@/utils/clientOss'
import wechatIcon from '@/assets/svg/WeChat.svg'
import qqIcon from '@/assets/svg/QQ.svg'

const pageRef = ref(null)
const { policyData } = useVipAgreement()
const wechatImg = clientOssUrl('wechat.webp')
const qqImg = clientOssUrl('QQ.webp')
</script>

<style scoped src="@/assets/styles/policy.css"></style>
