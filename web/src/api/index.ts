import { command, request } from './client'
import type * as T from '../types/api'
const id = encodeURIComponent
export const api = {
  login: (username: string, password: string) =>
    request<{ token: string; expiresAt: string; user: T.Actor }>(
      '/auth/login',
      { method: 'POST', body: { username, password }, anonymous: true },
    ),
  me: () => request<T.Actor>('/auth/me'),
  logout: () => request<{ ok: true }>('/auth/logout', { method: 'POST' }),
  staff: () => request<T.StaffView[]>('/users?purpose=staff'),
  users: () => request<T.Actor[]>('/users'),
  createUser: (body: T.UserCreate) => command<T.Actor>('/users', body),
  enableUser: (userId: string, enabled: boolean) =>
    command<{ ok: true }>(`/users/${id(userId)}/enabled`, { enabled }, 'PATCH'),
  resources: () => request<T.ResourceView[]>('/resources'),
  createResource: (body: T.ResourceCreate) =>
    command<T.ResourceView>('/resources', body),
  calendar: (from: string, to: string, resourceId = '') =>
    request<T.CalendarEntry[]>(
      `/resources/calendar?${new URLSearchParams({ from, to, ...(resourceId ? { resourceId } : {}) })}`,
    ),
  createFault: (resourceId: string, body: T.FaultInput) =>
    command<T.FaultView>(`/resources/${id(resourceId)}/faults`, body),
  faults: (resourceId: string, from: string, to: string) =>
    request<T.FaultView[]>(
      `/resources/${id(resourceId)}/faults?${new URLSearchParams({ from, to })}`,
    ),
  createActivity: (body: T.ActivityInput) =>
    command<T.ActivityView>('/activities', body),
  activities: (scope: 'mine' | 'public' | 'review', status = '') =>
    request<T.ActivityView[]>(
      `/activities?${new URLSearchParams({ scope, ...(status ? { status } : {}) })}`,
    ),
  activity: (activityId: string) =>
    request<T.ActivityView>(`/activities/${id(activityId)}`),
  editActivity: (activityId: string, body: T.ActivityEdit) =>
    command<T.ActivityView>(`/activities/${id(activityId)}`, body, 'PATCH'),
  submit: (activityId: string, body: T.VersionCommand) =>
    command<T.ActivityView>(`/activities/${id(activityId)}/submit`, body),
  withdraw: (activityId: string, body: T.ReasonCommand) =>
    command<T.ActivityView>(`/activities/${id(activityId)}/withdraw`, body),
  approve: (activityId: string, body: T.ApprovalCommand) =>
    command<T.ActivityView>(
      `/activities/${id(activityId)}/approver-decision`,
      body,
    ),
  reserve: (activityId: string, body: T.VersionCommand) =>
    command<T.ActivityView>(
      `/activities/${id(activityId)}/resource-confirm`,
      body,
    ),
  publish: (activityId: string, body: T.VersionCommand) =>
    command<T.ActivityView>(`/activities/${id(activityId)}/publish`, body),
  cancel: (activityId: string, body: T.ReasonCommand) =>
    command<T.ActivityView>(`/activities/${id(activityId)}/cancel`, body),
  registration: (activityId: string, body: T.RegistrationCommand) =>
    command<T.RegistrationView>(
      `/activities/${id(activityId)}/registration`,
      body,
      'PUT',
    ),
  registrations: (activityId: string) =>
    request<T.RegistrationView[]>(
      `/activities/${id(activityId)}/registrations`,
    ),
  preview: (activityId: string, body: T.Proposal) =>
    request<T.CheckResult>(`/activities/${id(activityId)}/schedule-preview`, {
      method: 'POST',
      body,
    }),
  alternatives: (activityId: string, body: T.Proposal) =>
    request<T.AlternativesView>(`/activities/${id(activityId)}/alternatives`, {
      method: 'POST',
      body,
    }),
  changePreview: (activityId: string, body: T.Proposal) =>
    request<T.ChangePreview>(`/activities/${id(activityId)}/change-preview`, {
      method: 'POST',
      body,
    }),
  createChange: (activityId: string, body: T.ChangeCreate) =>
    command<T.ChangeView>(`/activities/${id(activityId)}/changes`, body),
  changes: (activityId: string) =>
    request<T.ChangeView[]>(`/activities/${id(activityId)}/changes`),
  change: (changeId: string) =>
    request<T.ChangeView>(`/changes/${id(changeId)}`),
  confirmChange: (changeId: string, body: T.ChangeDecision) =>
    command<T.ChangeView>(`/changes/${id(changeId)}/confirmation`, body),
  approveChange: (changeId: string, body: T.ChangeDecision) =>
    command<T.ChangeView>(`/changes/${id(changeId)}/approver-decision`, body),
  reserveChange: (changeId: string, body: T.ChangeDecision) =>
    command<T.ChangeView>(`/changes/${id(changeId)}/resource-decision`, body),
  withdrawChange: (changeId: string, reason: string) =>
    command<T.ChangeView>(`/changes/${id(changeId)}/withdraw`, { reason }),
  notifications: (unreadOnly = false) =>
    request<T.NotificationView[]>(`/notifications?unreadOnly=${unreadOnly}`),
  readNotification: (notificationId: string) =>
    command<T.NotificationView>(
      `/notifications/${id(notificationId)}/read`,
      undefined,
    ),
  audit: (activityId: string) =>
    request<T.AuditView[]>(`/activities/${id(activityId)}/audit`),
  health: () => request<{ status: 'UP' }>('/health', { anonymous: true }),
  organizations: () =>
    request<{ id: string; name: string }[]>('/organizations'),
  authorization: (userId: string, body: T.AuthorizationInput) =>
    command<T.Actor>(`/users/${id(userId)}/authorization`, body, 'PATCH'),
  eligibility: (resourceId: string, body: T.ResourceEligibility) =>
    command<T.ResourceView>(
      `/resources/${id(resourceId)}/eligibility`,
      body,
      'PATCH',
    ),
}
