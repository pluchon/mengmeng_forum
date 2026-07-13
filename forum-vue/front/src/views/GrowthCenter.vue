<template>
  <section class="growth-center">
    <el-skeleton v-if="loading" animated :rows="8" />
    <el-result v-else-if="error" icon="error" title="成长中心暂不可用" :sub-title="error"><template #extra><el-button type="primary" @click="load">重试</el-button></template></el-result>
    <template v-else-if="overview">
      <header class="growth-hero">
        <div class="growth-hero-copy"><div class="growth-mark"><el-icon><Opportunity /></el-icon></div><div><p>GROWTH CENTER</p><h1>慢慢成长，也很好</h1><span>{{ overview.formalUser ? '正式用户' : '完成新人试炼，开启社区创作' }}</span></div></div>
        <div class="growth-hero-level"><small>当前等级</small><strong>Lv.{{ overview.growthLevel }}</strong></div>
      </header>
      <section v-if="!active" class="growth-dashboard">
        <section class="growth-journey"><div class="growth-journey-head"><span>成长进度</span><b>{{ overview.experience }} / {{ overview.nextLevelExperience }} XP</b></div><div class="growth-journey-track"><i :style="{ width: progress + '%' }"></i></div><div class="growth-journey-foot"><span>Lv.{{ overview.growthLevel }}</span><span>距离下一等级还差 {{ Math.max(0, overview.nextLevelExperience - overview.experience) }} XP</span></div></section>
        <section class="growth-challenges"><div class="growth-section-title"><span>成长挑战</span><small>选择一项，按自己的节奏完成</small></div><div class="growth-grid"><article v-for="(item, index) in overview.challenges || []" :key="item.challengeCode" class="growth-card" :class="{ 'is-done': item.status === 'REWARDED' }"><b class="growth-card-no">0{{ index + 1 }}</b><span class="growth-card-icon"><el-icon><CircleCheck v-if="item.status === 'REWARDED'" /><Opportunity v-else /></el-icon></span><div class="growth-card-copy"><h3>{{ item.title }}</h3><div class="growth-card-meta">{{ item.questionCount }} 题 · {{ item.passingScore }} 分通过</div></div><em>+{{ item.experienceReward }} XP</em><el-button type="primary" :disabled="item.status === 'REWARDED'" @click="start(item)">{{ item.status === 'REWARDED' ? '已完成' : '开始' }}<el-icon v-if="item.status !== 'REWARDED'"><ArrowRight /></el-icon></el-button></article></div></section>
      </section>
      <section v-else class="growth-paper"><div class="growth-paper-head"><div><span>挑战进行中</span><h2>{{ active.title }}</h2></div><b>第 {{ activeQuestionNo }} / {{ activeQuestionTotal }} 题</b></div><div class="growth-workspace"><article class="growth-question"><strong>{{ activeQuestionNo }}. {{ activeQuestion?.stem }}</strong><el-radio-group v-model="answers[activeQuestion?.id]"><el-radio v-for="option in JSON.parse(activeQuestion?.optionsJson || '[]')" :key="option.label" :value="option.label">{{ option.label }}. {{ option.text }}</el-radio></el-radio-group><div class="growth-paper-actions"><el-button :disabled="activeQuestionIndex === 0" @click="prevQuestion">上一题</el-button><el-button v-if="activeQuestionIndex < activeQuestionTotal - 1" type="primary" @click="nextQuestion">下一题</el-button><el-button v-else :loading="submitting" type="primary" @click="submit">提交挑战</el-button><el-button text @click="active = null">退出</el-button></div></article><aside class="growth-question-map"><span>题目导航</span><div><button v-for="(q,index) in active.questions" :key="q.id" :class="{ 'is-active': index === activeQuestionIndex, 'is-answered': answers[q.id] }" @click="selectQuestion(index)">{{ index + 1 }}</button></div></aside></div></section>
    </template>
  </section>
</template>
<script src="@/scripts/views/GrowthCenter.js"></script>
<style src="./GrowthCenter.scss" lang="scss"></style>
