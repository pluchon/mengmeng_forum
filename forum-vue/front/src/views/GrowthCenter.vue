<template>
  <section class="growth-center">
    <el-skeleton v-if="loading" animated :rows="8" />
    <el-result v-else-if="error" icon="error" title="成长中心暂不可用" :sub-title="error"><template #extra><el-button type="primary" @click="load">重试</el-button></template></el-result>
    <template v-else-if="overview">
      <header class="growth-hero"><p>GROWTH CENTER</p><h1>成长中心</h1><span>{{ overview.formalUser ? '正式用户' : '非正式用户 · 完成新人试炼后可参与社区创作' }}</span><el-progress :percentage="progress" :stroke-width="10" /><small>Lv.{{ overview.growthLevel }} · {{ overview.experience }} 经验</small></header>
      <section v-if="!active" class="growth-grid"><article v-for="item in overview.challenges || []" :key="item.challengeCode" class="growth-card"><span>{{ item.challengeCode === 'FORMAL_USER' ? '新人试炼' : '会员体验' }}</span><h2>{{ item.title }}</h2><p>{{ item.description }}</p><small>{{ item.questionCount }} 题 · {{ item.passingScore }} 分通过 · +{{ item.experienceReward }} 经验</small><el-button type="primary" :disabled="item.status === 'REWARDED'" @click="start(item)">{{ item.status === 'REWARDED' ? '已完成' : '开始挑战' }}</el-button></article></section>
      <section v-else class="growth-paper"><h2>{{ active.title }}</h2><p>答完全部题目后提交，及格线 {{ active.passingScore }} 分。</p><article v-for="q in active.questions" :key="q.id" class="growth-question"><strong>{{ q.questionOrder }}. {{ q.stem }}</strong><el-radio-group v-model="answers[q.id]"><el-radio v-for="option in JSON.parse(q.optionsJson || '[]')" :key="option.label" :value="option.label">{{ option.label }}. {{ option.text }}</el-radio></el-radio-group></article><el-button :loading="submitting" type="primary" @click="submit">提交挑战</el-button><el-button @click="active = null">暂不作答</el-button></section>
    </template>
  </section>
</template>
<script src="@/scripts/views/GrowthCenter.js"></script>
<style src="./GrowthCenter.scss" lang="scss"></style>
