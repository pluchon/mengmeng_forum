<template>
  <footer class="footer">
    <div class="footer-inner">
      <ul class="footer-links">
        <li>
          Copyright &copy; 2026
          <a href="/">{{ SITE_NAME }}</a>
          版权所有
        </li>
        <li>
          <a
            :href="SITE_ICP_URL"
            target="_blank"
            rel="noopener noreferrer"
          >{{ SITE_ICP_NUMBER }}</a>
        </li>
      </ul>
    </div>
  </footer>
</template>

<script setup>
import { SITE_ICP_NUMBER, SITE_ICP_URL, SITE_NAME } from '@/constants/site'
import { useTheFooter } from '@scripts/components/layout/TheFooter'

useTheFooter()
</script>

<style scoped src="@/assets/styles/layout.css"></style>
