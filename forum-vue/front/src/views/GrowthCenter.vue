<template>
  <section class="growth-center">
    <el-skeleton v-if="loading" animated :rows="8" />
    <el-result v-else-if="error" icon="error" title="成长中心暂不可用" :sub-title="error"><template #extra><el-button type="primary" @click="load">重试</el-button></template></el-result>
    <template v-else-if="overview">
      <header class="growth-hero">
        <div class="growth-hero-copy"><div class="growth-mark"><el-icon><Opportunity /></el-icon></div><div><p>GROWTH CENTER</p><h1>成长中心</h1><span>{{ overview.formalUser ? '正式用户 · 已开启完整社区权限' : '非正式用户 · 完成新人试炼后可参与社区创作' }}</span></div></div>
        <div class="growth-level-card"><div><small>当前等级</small><strong>Lv.{{ overview.growthLevel }}</strong></div><el-progress type="circle" :percentage="progress" :width="74" :stroke-width="8" color="#ff5b70" /><b>{{ overview.experience }} XP</b></div>
      </header>
      <section v-if="!active" class="growth-dashboard">
        <div><div class="growth-section-title"><div><span>今日成长</span><h2>完成挑战，解锁你的下一步</h2></div><el-icon><Trophy /></el-icon></div>
          <div class="growth-grid"><article v-for="item in overview.challenges || []" :key="item.challengeCode" class="growth-card" :class="{ 'is-done': item.status === 'REWARDED' }"><div class="growth-card-top"><span class="growth-card-icon"><el-icon><CircleCheck v-if="item.status === 'REWARDED'" /><Opportunity v-else /></el-icon></span><em>+{{ item.experienceReward }} XP</em></div><h3>{{ item.title }}</h3><p>{{ item.description }}</p><div class="growth-card-meta">{{ item.questionCount }} 题 · {{ item.passingScore }} 分通过</div><el-button type="primary" :disabled="item.status === 'REWARDED'" @click="start(item)">{{ item.status === 'REWARDED' ? '已完成' : '开始挑战' }}<el-icon v-if="item.status !== 'REWARDED'"><ArrowRight /></el-icon></el-button></article></div>
        </div>
        <aside class="growth-side"><div class="growth-side-head"><el-icon><Medal /></el-icon><span>成长里程</span></div><div class="growth-timeline"><div class="is-active"><b>Lv.{{ overview.growthLevel }}</b><span>当前成长等级</span></div><div><b>下一等级</b><span>再积累 {{ Math.max(0, overview.nextLevelExperience - overview.experience) }} XP</span></div></div><div class="growth-tip"><el-icon><Opportunity /></el-icon><p>成长等级当前仅用于展示，后续会逐步加入更多专属成长玩法。</p></div></aside>
      </section>
      <section v-else class="growth-paper"><div class="growth-paper-head"><span>挑战进行中</span><h2>{{ active.title }}</h2><p>答完全部题目后提交，及格线 {{ active.passingScore }} 分。</p></div><article v-for="q in active.questions" :key="q.id" class="growth-question"><strong>{{ q.questionOrder }}. {{ q.stem }}</strong><el-radio-group v-model="answers[q.id]"><el-radio v-for="option in JSON.parse(q.optionsJson || '[]')" :key="option.label" :value="option.label">{{ option.label }}. {{ option.text }}</el-radio></el-radio-group></article><div class="growth-paper-actions"><el-button :loading="submitting" type="primary" @click="submit">提交挑战</el-button><el-button @click="active = null">暂不作答</el-button></div></section>
    </template>
  </section>
</template>
<script src="@/scripts/views/GrowthCenter.js"></script>
<style src="./GrowthCenter.scss" lang="scss"></style>
