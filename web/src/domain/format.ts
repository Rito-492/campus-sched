import type { ActivityStatus } from '../types/api'
export const statusLabels: Record<ActivityStatus, string> = {
  DRAFT: '草稿',
  PENDING_APPROVER: '待批准',
  APPROVED_UNRESERVED: '待资源确认',
  SCHEDULED: '已预约',
  OPEN: '开放报名',
  CANCELLED: '已取消',
}
export function formatTime(instant: string) {
  return new Intl.DateTimeFormat('zh-CN', {
    timeZone: 'Asia/Shanghai',
    month: '2-digit',
    day: '2-digit',
    hour: '2-digit',
    minute: '2-digit',
    hour12: false,
  }).format(new Date(instant))
}
export const shanghaiInput = (input: string) => `${input}:00+08:00`
