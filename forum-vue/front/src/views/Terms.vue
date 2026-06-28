<template>
  <div ref="pageRef" class="privacy-page shell-page-scroll">
    <ParticleSea />
    <div class="privacy-container animate-fade-up">
      <main class="privacy-content">
        <div class="hero-section">
          <h1>用户协议</h1>
          <p>使用萌部落前，请仔细阅读以下条款。注册、登录或使用本平台服务即表示您同意本协议。</p>
          <div class="update-tag">最近更新：2026-05-14</div>
        </div>

        <div class="policy-card">
          <section v-for="(section, index) in termsData" :key="index" class="policy-section">
            <h2 class="section-title">{{ section.title }}</h2>
            <div class="section-body" v-html="section.content"></div>
            <div v-if="index < termsData.length - 1" class="divider"></div>
          </section>

          <section class="policy-section contact-section">
            <h2 class="section-title">联系我们</h2>
            <p>如对本协议有疑问，可通过以下方式联系：</p>
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
        </div>
      </main>

      <footer class="privacy-footer">
        <SiteIcpLink variant="footer" />
      </footer>
    </div>
  </div>
</template>

<script setup>
import { ref } from 'vue'
import ParticleSea from '@/components/common/ParticleSea.vue'
import SiteIcpLink from '@/components/layout/SiteIcpLink.vue'
import { useTerms } from '@scripts/views/Terms'

import { clientOssUrl } from '@/utils/clientOss'

const pageRef = ref(null)
const { termsData } = useTerms()
const wechatImg = clientOssUrl('wechat.webp')
const qqImg = clientOssUrl('QQ.webp')
</script>
