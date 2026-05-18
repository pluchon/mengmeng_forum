<template>
  <footer class="footer">
    <div class="footer-container">
      <ul class="footer-links">
        <li>
          Copyright &copy; 2026
          <a href="#">萌萌论坛</a>
          版权所�?
        </li>
        <li>
          <a href="https://github.com/pluchon/forum-demo" target="_blank">
            项目地址
          </a>
        </li>
      </ul>
    </div>
  </footer>
</template>

<script setup>
import { useTheFooter } from '@scripts/components/layout/TheFooter'

useTheFooter()
</script>


<style scoped src='@/assets/styles/layout.css'></style>
