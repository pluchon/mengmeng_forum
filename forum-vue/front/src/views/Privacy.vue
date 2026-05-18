<template>
  <div class="privacy-page shell-page-scroll">
    <div class="privacy-container animate-fade-up">
      <main class="privacy-content">
        <div class="hero-section">
          <h1>隐私政策</h1>
          <p>萌萌论坛重视您的隐私。注册或使用本平台前，请仔细阅读以下内容。</p>
          <div class="update-tag">最近更新：2026-05-14</div>
        </div>

        <div class="policy-card">
          <section v-for="(section, index) in policyData" :key="index" class="policy-section">
            <h2 class="section-title">{{ section.title }}</h2>
            <div class="section-body" v-html="section.content"></div>
            <div v-if="index < policyData.length - 1" class="divider"></div>
          </section>

          <section class="policy-section contact-section">
            <h2 class="section-title">联系我们</h2>
            <p>如对本隐私政策有疑问，可通过以下方式联系：</p>
            <div class="contact-methods">
              <a
                href="https://github.com/pluchon/forum-demo"
                target="_blank"
                class="contact-item"
                rel="noopener noreferrer"
              >
                <el-icon><Link /></el-icon>
                GitHub / forum-demo
              </a>
              <div class="admin-qr">
                <div class="qr-item">
                  <div class="qr-wrapper">
                    <img src="../assets/images/wechat.jpg" alt="微信">
                  </div>
                  <p>微信</p>
                </div>
                <div class="qr-item">
                  <div class="qr-wrapper">
                    <img src="../assets/images/QQ.jpg" alt="QQ">
                  </div>
                  <p>QQ</p>
                </div>
              </div>
            </div>
          </section>
        </div>
      </main>

      <footer class="privacy-footer">
        版权所有 © 2026 <strong>萌萌论坛</strong>。保留所有权利。
      </footer>
    </div>
  </div>
</template>

<script setup>
import { usePrivacy } from '@scripts/views/Privacy'

const { Link, policyData } = usePrivacy()
</script>
