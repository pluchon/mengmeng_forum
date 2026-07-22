<template>
  <div ref="pageRef" class="privacy-page shell-page-scroll">
    <ParticleSea />
    <div class="privacy-container animate-fade-up">
      <main class="privacy-content">
        <header class="hero-section">
          <h1>隐私政策</h1>
          <p>萌部落重视您的隐私。注册或使用本平台前，请仔细阅读以下内容。</p>
          <div class="update-tag">最近更新：2026-05-14</div>
        </header>

        <article class="policy-card">
          <section v-for="(section, index) in policyData" :key="index" class="policy-section">
            <h2 class="section-title">{{ section.title }}</h2>
            <div class="section-body" v-html="section.content"></div>
            <div v-if="index < policyData.length - 1" class="divider"></div>
          </section>

          <section class="policy-section contact-section">
            <h2 class="section-title">联系我们</h2>
            <p>如对本隐私政策有疑问，可通过以下方式联系：</p>
            <div class="contact-methods">
              <div class="admin-qr">
                <div class="qr-item">
                  <div class="qr-wrapper">
                    <img :src="wechatImg" alt="微信">
                  </div>
                  <p>微信</p>
                </div>
                <div class="qr-item">
                  <div class="qr-wrapper">
                    <img :src="qqImg" alt="QQ">
                  </div>
                  <p>QQ</p>
                </div>
              </div>
            </div>
          </section>
        </article>
      </main>
    </div>
  </div>
</template>

<script setup>
import { ref } from 'vue'
import ParticleSea from '@/components/common/ParticleSea.vue'
import { usePrivacy } from '@scripts/views/Privacy'

import { clientOssUrl } from '@/utils/clientOss'

const pageRef = ref(null)
const { policyData } = usePrivacy()
const wechatImg = clientOssUrl('wechat.webp')
const qqImg = clientOssUrl('QQ.webp')
</script>
