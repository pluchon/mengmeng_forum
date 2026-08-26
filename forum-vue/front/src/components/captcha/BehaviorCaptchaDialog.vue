<template>
  <el-dialog
    v-model="visible"
    width="380px"
    append-to-body
    destroy-on-close
    class="behavior-captcha-dialog"
    @closed="onDialogClosed"
  >
    <template #header>
      <h2 class="captcha-dialog-title">安全验证</h2>
    </template>

    <div v-if="errorMsg" class="captcha-error">
      <el-icon class="captcha-error__icon"><WarningFilled /></el-icon>
      <span>{{ errorMsg }}</span>
      <a class="cap-link" @click="loadVo">重新加载</a>
    </div>

    <div v-else-if="vo && mode === 'slider'" class="cap-panel">
      <p class="cap-tip">
        <el-icon><Grid /></el-icon>
        拖动滑块，将拼图移到缺口位置
      </p>

      <div ref="bgWrapRef" class="cap-img-wrap">
        <img
          ref="bgImgRef"
          class="cap-bg-img"
          :src="bgSrc"
          alt=""
          draggable="false"
          @load="onBgLoad"
        />
        <img
          ref="tplRef"
          class="slider-tpl"
          :src="tplSrc"
          alt=""
          draggable="false"
          :style="{ left: dragX + 'px', top: dragY + 'px', width: tplW > 0 ? tplW + 'px' : 'auto' }"
          @pointerdown="onHandlePointerDown"
        />
        <div class="slider-progress" :style="{ width: dragX + 'px' }" />
      </div>

      <div class="slider-rail">
        <span class="slider-rail-hint">{{ dragging ? '松开手完成验证' : '向右拖动完成拼图' }}</span>
        <div class="slider-track" :style="{ width: trackWidth + 'px' }" />
        <div
          class="slider-btn"
          :class="{ dragging }"
          :style="{ transform: `translateX(${dragX}px)` }"
          @pointerdown.stop="onHandlePointerDown"
        >
          <el-icon><DArrowRight /></el-icon>
        </div>
      </div>

      <div class="cap-footer">
        <el-tooltip content="换一张" placement="top">
          <span class="cap-refresh-icon" @click="loadVo"><el-icon><RefreshRight /></el-icon></span>
        </el-tooltip>
        <span class="cap-footer-switch">
          试试 <a class="cap-link" @click="switchMode('click')">文字点击验证</a>
        </span>
      </div>
    </div>

    <div v-else-if="vo && mode === 'click'" class="cap-panel">
      <div class="cap-tip click-tip">
        <el-icon><ChatLineRound /></el-icon>
        <span class="click-tip-label">依次点击图中文字：</span>
        <img
          v-if="tplSrc"
          class="click-tip-img"
          :src="tplSrc"
          alt="点击提示"
          draggable="false"
        />
        <span v-if="clickDots.length > 0" class="click-progress">
          {{ clickDots.length }}/{{ clickCount }}
        </span>
      </div>

      <div ref="clickImgRef" class="cap-img-wrap" @click="onClickImage">
        <img
          class="cap-bg-img"
          :src="bgSrc"
          alt=""
          draggable="false"
          @load="onClickBgLoad"
        />
        <div
          v-for="(dot, i) in clickDots"
          :key="i"
          class="click-dot"
          :style="{ left: dot.px + 'px', top: dot.py + 'px' }"
        >
          <span>{{ i + 1 }}</span>
        </div>
        <div v-if="submitting" class="click-submitting-mask">
          <el-icon class="is-loading"><Loading /></el-icon>
        </div>
      </div>

      <div class="cap-footer">
        <el-tooltip content="换一张" placement="top">
          <span class="cap-refresh-icon" @click="loadVo"><el-icon><RefreshRight /></el-icon></span>
        </el-tooltip>
        <span class="cap-footer-switch">
          试试 <a class="cap-link" @click="switchMode('slider')">滑块拼图验证</a>
        </span>
        <a
          v-if="clickDots.length > 0"
          class="cap-link cap-link--danger"
          @click="resetClick"
        >重置</a>
      </div>
    </div>

    <div v-else class="captcha-skeleton">
      <el-skeleton :rows="3" animated />
    </div>
  </el-dialog>

  <SystemUpgradeDialog ref="upgradeDialogRef" />
</template>

<script setup src="@/scripts/components/captcha/BehaviorCaptchaDialog.js"></script>
<style scoped src="@/assets/styles/captcha-dialog.css"></style>
