import http from '@/utils/http'

async function uploadFile(url: string, file: File): Promise<string> {
  const formData = new FormData()
  formData.append('file', file)
  const res = await http.request<string>({
    method: 'post',
    url,
    data: formData,
    headers: {
      'Content-Type': 'multipart/form-data'
    }
  })
  return typeof res.data === 'string' ? res.data : ''
}

export function uploadNoticePicture(file: File, noticeId: number) {
  return uploadFile(`/file/uploadNoticePicture?noticeId=${noticeId}`, file)
}

export function uploadLotteryActivityPicture(file: File, activityId: number) {
  return uploadFile(`/file/uploadLotteryActivityPicture?activityId=${activityId}`, file)
}

export function uploadLotteryPrizePicture(file: File, activityId: number, prizeId: number) {
  return uploadFile(`/file/uploadLotteryPrizePicture?activityId=${activityId}&prizeId=${prizeId}`, file)
}
