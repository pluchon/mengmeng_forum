// 门户静态图 登录/注册海报等 固定走线上 OSS 的 forum_images/ 路径，本地开发也不改桶、不改前缀
// 用户上传内容才走 local develop / 线上业务桶 由后端 oss.* 配置 ，与本文件无关
const CLIENT_OSS_BASE = (
  import.meta.env.VITE_CLIENT_OSS_BASE ||
  'https://item-for-picture-with-zhanglihong.oss-cn-shenzhen.aliyuncs.com'
).replace(/\/+$/, '')

const DEFAULT_PREFIX = `${CLIENT_OSS_BASE}/forum_images/client/webp/`
const RAW_PREFIX = import.meta.env.VITE_CLIENT_OSS_PREFIX || DEFAULT_PREFIX
const PREFIX = RAW_PREFIX ? (RAW_PREFIX.endsWith('/') ? RAW_PREFIX : `${RAW_PREFIX}/`) : DEFAULT_PREFIX

export function clientOssUrl(filename, fallbackUrl = '') {
  if (!filename) return fallbackUrl || ''
  const name = String(filename).trim()
  if (/^https?:\/\//i.test(name)) return name
  if (name.startsWith('forum_images/') || name.startsWith('forum_3d/')) {
    return `${CLIENT_OSS_BASE}/${name.replace(/^\//, '')}`
  }
  if (!PREFIX) return fallbackUrl || ''
  const base = PREFIX.endsWith('/') ? PREFIX : `${PREFIX}/`
  const leaf = name.replace(/^\//, '')
  return `${base}${leaf}`
}

// 认证页左侧宽屏插画
export const LOGIN_WEBP_URL = clientOssUrl('login.webp')
export const LOGIN_TITLE_WEBP_URL = clientOssUrl('login_title.webp')
export const REGISTER_WEBP_URL = clientOssUrl('register.webp')
export const CREATE_ACCOUNT_TITLE_WEBP_URL = clientOssUrl('create_account_title.webp')
export const FIND_WEBP_URL = clientOssUrl('find_password.webp')
export const FIND_PASSWORD_TITLE_WEBP_URL = clientOssUrl('find_password_title.webp')

// 门户 / 游戏封面与背景 / 营销展示图
export const DOOR_CHAT_SHOW_WEBP_URL = clientOssUrl('door_chat_show.webp')
export const DOOR_CREATE_01_WEBP_URL = clientOssUrl('door_create_01.webp')
export const DOOR_CREATE_02_WEBP_URL = clientOssUrl('door_create_02.webp')
export const DOOR_CREATE_03_WEBP_URL = clientOssUrl('door_create_03.webp')
export const DOOR_GAME_SHOW_WEBP_URL = clientOssUrl('door_game_show.webp')
export const DOOR_NOT_LOGIN_BACKGROUND_WEBP_URL = clientOssUrl('door_not_login_background.webp')

// 门户未登录 3D 场景模型（Draco 压缩）
// 开发走 Vite 同源代理 /forum_3d/；生产直连 OSS（桶需配置 CORS）
function doorGlbUrl(filename) {
  const leaf = String(filename || '').replace(/^\//, '')
  if (import.meta.env.DEV) return `/forum_3d/${leaf}`
  return `${CLIENT_OSS_BASE}/forum_3d/${leaf}`
}
export const DOOR_QIU_GLB_URL = doorGlbUrl('qiu.glb')
export const DOOR_HUAN_GLB_URL = doorGlbUrl('huan.glb')
export const DOOR_JIQI_GLB_URL = doorGlbUrl('jiqi.glb')
export const WUZIQI_COVER_WEBP_URL = clientOssUrl('wuziqi.webp')
export const JINZI_COVER_WEBP_URL = clientOssUrl('jinzi.webp')
export const ELUOSI_ALONE_WEBP_URL = clientOssUrl('eluosi_alone.webp')
export const ELUOSI_PK_WEBP_URL = clientOssUrl('eluosi_pk.webp')
export const WUZIQI_BACKGROUND_WEBP_URL = clientOssUrl('wuziqi_background.webp')
export const JINGZI_BACKGROUND_WEBP_URL = clientOssUrl('jingzi_background.webp')
export const ELUOSI_BACKGROUND_WEBP_URL = clientOssUrl('eluosi_background.webp')
export const GAME_CARD_WEBP_URL = clientOssUrl('game_card.webp')
export const CREATE_CENTER_WEBP_URL = clientOssUrl('create_center.webp')
export const EMJIO_SHOP_WEBP_URL = clientOssUrl('emjio_shop.webp')
export const MENG_COIN_CENTER_WEBP_URL = clientOssUrl('meng_coin_center.webp')
export const VIP_BG_WEBP_URL = clientOssUrl('VIP.webp')
export const VIP_FREE_VISUAL_WEBP_URL = clientOssUrl('free.webp')
export const VIP_PRO_VISUAL_WEBP_URL = clientOssUrl('pro.webp')
export const VIP_MAX_VISUAL_WEBP_URL = clientOssUrl('max.webp')
export const PRO_TIME_TO_TEST_WEBP_URL = clientOssUrl('pro_time_to_test.webp')
export const VIP_RESET_CARD_WEBP_URL = clientOssUrl('VIP_reset_card.webp')
export const MENGBI_DIKOUQUAN_WEBP_URL = clientOssUrl('mengbi_dikouquan.webp')
export const QIANDAO_BUQIANKA_WEBP_URL = clientOssUrl('qiandao_buqianka.webp')
export const PRIZE_THANKS_WEBP_URL = clientOssUrl('prize_thanks.webp')
export const PRIZE_SHENMI_WEBP_URL = clientOssUrl('prize_shenmi.webp')
export const PRIZE_ZHOUBIAN_WEBP_URL = clientOssUrl('prize_zhoubian.webp')
export const PRIZE_ANWEI_WEBP_URL = clientOssUrl('prize_anwei.webp')
export const PRIZE_JIFEN_WEBP_URL = clientOssUrl('prize_jifen.webp')
export const PRIZE_VIP_WEBP_URL = clientOssUrl('prize_vip.webp')
export const LOTTERY_BACKGROUND_WEBP_URL = clientOssUrl('lottery_background.webp')
export const LOTTERY_PRIZE_WEBP_URL = clientOssUrl('lottery_prize.webp')
