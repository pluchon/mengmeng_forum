<template>
  <div
    class="tetris-cover-board"
    :class="[wrapperClass, { 'is-matching': matching }]"
    aria-hidden="true"
  >
    <div class="tetris-cover-stage">
      <div class="tetris-cover-grid">
        <span class="tetris-mini-cell is-stack is-purple" style="--x: 2; --y: 7" />
        <span class="tetris-mini-cell is-stack is-purple" style="--x: 3; --y: 7" />
        <span class="tetris-mini-cell is-stack is-green" style="--x: 6; --y: 7" />
        <span class="tetris-mini-cell is-stack is-cyan" style="--x: 1; --y: 8" />
        <span class="tetris-mini-cell is-stack is-cyan" style="--x: 2; --y: 8" />
        <span class="tetris-mini-cell is-stack is-cyan" style="--x: 3; --y: 8" />
        <span class="tetris-mini-cell is-stack is-orange" style="--x: 6; --y: 8" />
        <span class="tetris-mini-cell is-stack is-orange" style="--x: 7; --y: 8" />
        <span class="tetris-mini-cell is-floor is-blue" style="--x: 0; --y: 9" />
        <span class="tetris-mini-cell is-floor is-blue" style="--x: 1; --y: 9" />
        <span class="tetris-mini-cell is-floor is-blue" style="--x: 2; --y: 9" />
        <span class="tetris-mini-cell is-floor is-blue" style="--x: 3; --y: 9" />
        <span class="tetris-mini-cell is-floor is-blue" style="--x: 6; --y: 9" />
        <span class="tetris-mini-cell is-floor is-blue" style="--x: 7; --y: 9" />
        <span class="tetris-mini-cell is-floor is-blue" style="--x: 8; --y: 9" />
        <span class="tetris-mini-cell is-floor is-blue" style="--x: 9; --y: 9" />
        <span class="tetris-mini-cell is-drop is-yellow" style="--x: 4; --y: 9" />
        <span class="tetris-mini-cell is-drop is-yellow" style="--x: 5; --y: 9" />
        <span class="tetris-cover-clear" />
      </div>
    </div>
  </div>
</template>

<script setup src="@scripts/components/game/TetrisCoverBoard.js"></script>
<style src="@/assets/styles/tetris-cover-board.css"></style>
