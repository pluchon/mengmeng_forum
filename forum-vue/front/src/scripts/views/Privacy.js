import '@/assets/styles/user.css'

export function usePrivacy() {
  const policyData = [
    {
      title: '我们会收集哪些信息',
      content: `
        <p>为提供注册登录、内容发布与消息通知等功能，我们可能收集：</p>
        <ul>
          <li><strong>账号信息：</strong>用户名、昵称、密码（加密存储）。</li>
          <li><strong>联系信息（选填）：</strong>手机号、邮箱。</li>
          <li><strong>内容数据：</strong>帖子、评论、私信、上传的图片。</li>
          <li><strong>基础日志：</strong>访问时间、IP 等用于安全分析的信息。</li>
        </ul>
      `
    },
    {
      title: '我们如何使用信息',
      content: `
        <p>收集到的信息仅用于：</p>
        <ul>
          <li>提供账号注册、登录及身份验证服务。</li>
          <li>向您展示个人主页及站点内容。</li>
          <li>发送站内通知与必要的安全提醒。</li>
          <li>用于安全监控，防止恶意注册、刷帖等违规行为。</li>
          <li>统计分析平台整体运营数据（仅使用匿名化聚合数据）。</li>
        </ul>
      `
    },
    {
      title: '信息安全与共享',
      content: `
        <ul>
          <li>我们会采取合理措施保护数据安全，密码等敏感信息不会以明文形式存储。</li>
          <li>不会出售您的个人信息；仅在法律法规要求、您授权或为保护合法权益的必要范围内披露。</li>
          <li>您公开发布的内容属于您主动公开的信息，其他用户可在站内查看。</li>
        </ul>
      `
    },
    {
      title: '您的权利',
      content: `
        <ul>
          <li><strong>查阅：</strong>随时登录个人设置页面查看您的账号信息。</li>
          <li><strong>修改：</strong>在用户中心修改昵称、头像、联系方式等信息。</li>
          <li><strong>删除：</strong>您可以删除自己发布的帖子和评论。</li>
          <li><strong>注销：</strong>如需注销账号，请联系管理员。</li>
        </ul>
      `
    },
    {
      title: '政策更新',
      content: `
        <p>本政策可能随平台发展不定期更新。重大变更时，我们将在平台显著位置发布通知。继续使用本平台服务即视为您接受更新后的政策。</p>
      `
    }
  ]

  return {
    policyData,
  }
}
