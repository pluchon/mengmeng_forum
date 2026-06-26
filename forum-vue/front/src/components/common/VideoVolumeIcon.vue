<template>
  <svg
    class="video-volume-icon"
    viewBox="0 0 24 24"
    :width="size"
    :height="size"
    aria-hidden="true"
  >
    <path fill="currentColor" d="M3 9v6h4l5 5V4L7 9H3z" />
    <path
      v-if="muted"
      fill="none"
      stroke="currentColor"
      stroke-width="2"
      stroke-linecap="round"
      d="M16 8l6 6M22 8l-6 6"
    />
    <template v-else>
      <path fill="currentColor" d="M13.5 3.23v2.06c2.89.86 5 3.54 5 6.71s-2.11 5.85-5 6.71v2.06c4.01-.91 7-4.49 7-8.77s-2.99-7.86-7-8.77z" />
      <path fill="currentColor" d="M16.5 12c0-1.77-1.02-3.29-2.5-4.03v8.05c1.48-.73 2.5-2.25 2.5-4.02z" />
    </template>
  </svg>
</template>

<script setup src="./VideoVolumeIcon.js"></script>
