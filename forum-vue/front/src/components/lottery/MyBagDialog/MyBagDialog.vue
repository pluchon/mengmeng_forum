<template>
  <el-dialog
    v-model="visible"
    width="min(1000px, 94vw)"
    align-center
    append-to-body
    destroy-on-close
    :show-close="false"
    class="mxh-shop-dialog"
    modal-class="mxh-shop-modal"
  >
    <div class="mxh-shop mybag">
      <header class="mxh-shop__header">
        <div class="mxh-shop__title-group">
          <img class="mxh-shop__brand-icon" :src="mengXinghuiIconUrl" alt="" aria-hidden="true" />
          <h2 class="mxh-shop__title">我的背包</h2>
        </div>
        <button type="button" class="mxh-shop__close" aria-label="关闭" @click="closeBag">×</button>
      </header>

      <div class="mxh-shop__tabs">
        <button
          v-for="tab in FILTERS"
          :key="tab.key"
          type="button"
          class="mxh-shop__tab"
          :class="{ 'is-active': filterKey === tab.key }"
          @click="onFilter(tab.key)"
        >
          {{ tab.label }}
        </button>
      </div>

      <div class="mxh-shop__body">
        <div class="mxh-shop__content">
          <div v-if="loading" class="mxh-shop__state mxh-shop__state--loading" aria-busy="true">
            <span class="mxh-shop__spinner" aria-hidden="true" />
            <span>加载中...</span>
          </div>
          <div v-else-if="error" class="mxh-shop__state mxh-shop__state--error">
            <span>{{ error }}</span>
            <button type="button" class="mxh-shop__retry" @click="load">重试</button>
          </div>
          <div v-else-if="!items.length" class="mxh-shop__empty">
            <img class="mxh-shop__empty-img" :src="emptyBagUrl" alt="" />
            <p class="mxh-shop__empty-text">背包还是空的</p>
          </div>
          <div v-else class="mxh-shop__grid">
            <article v-for="row in items" :key="row.id" class="mxh-shop-card">
              <div class="mxh-shop-card__media" aria-hidden="true">
                <img v-if="coverOf(row)" class="mxh-shop-card__cover" :src="coverOf(row)" alt="" />
                <span v-else class="mxh-shop-card__placeholder" />
              </div>
              <div class="mxh-shop-card__name-row">
                <span class="mxh-shop-card__name">{{ row.itemName }}</span>
                <span class="mxh-shop-card__tag">{{ sourceLabel(row) }}</span>
              </div>
              <div class="mxh-shop-card__meta mybag-card__meta">
                <span class="mybag-card__detail" :title="detailText(row)">{{ detailText(row) }}</span>
              </div>
              <button
                type="button"
                class="mxh-shop-card__btn"
                :disabled="btnDisabled(row)"
                @click="onUse(row)"
              >
                {{ btnText(row) }}
              </button>
            </article>
          </div>
        </div>
      </div>

      <footer class="mxh-shop__footer">
        <span class="mxh-shop__hint">点击使用生效，实物周边后管理员统一发放</span>
        <div class="mxh-shop__pager">
          <AppPagination
            :current-page="pageNum"
            size="small"
            :total="total"
            :page-size="PAGE_SIZE"
            :pager-count="5"
            :show-jumper="false"
            :hide-on-single-page="false"
            :disabled="loading"
            @current-change="goPage"
          />
        </div>
      </footer>
    </div>
  </el-dialog>
</template>

<script setup src="./MyBagDialog.js"></script>
<style lang="scss" src="../MengXinghuiShop/MengXinghuiShop.scss"></style>
<style lang="scss" src="./MyBagDialog.scss"></style>
