import { supabase } from './supabase';

const API_URL = process.env.NEXT_PUBLIC_API_URL || 'http://localhost:8080';

async function getAuthHeader(): Promise<string | null> {
  const { data } = await supabase.auth.getSession();
  return data.session?.access_token ?? null;
}

async function request<T>(path: string, options: RequestInit = {}): Promise<T> {
  const token = await getAuthHeader();
  if (!token) throw new Error('Not authenticated');

  const res = await fetch(`${API_URL}${path}`, {
    ...options,
    headers: {
      'Content-Type': 'application/json',
      Authorization: `Bearer ${token}`,
      ...options.headers,
    },
  });

  if (!res.ok) {
    const body = await res.json().catch(() => ({}));
    throw new Error(body.error || `API error: ${res.status}`);
  }

  if (res.status === 204) return null as T;
  return res.json();
}

async function uploadRequest<T>(path: string, formData: FormData): Promise<T> {
  const token = await getAuthHeader();
  if (!token) throw new Error('Not authenticated');

  const res = await fetch(`${API_URL}${path}`, {
    method: 'POST',
    headers: { Authorization: `Bearer ${token}` },
    body: formData,
  });

  if (!res.ok) {
    const body = await res.json().catch(() => ({}));
    throw new Error(body.error || `API error: ${res.status}`);
  }

  return res.json();
}

// Event API
export const eventApi = {
  list: (status?: string) =>
    request<Event[]>(`/api/events${status ? `?status=${status}` : ''}`),

  get: (id: string) => request<Event>(`/api/events/${id}`),

  create: (data: CreateEventInput) =>
    request<Event>('/api/events', {
      method: 'POST',
      body: JSON.stringify(data),
    }),

  update: (id: string, data: CreateEventInput) =>
    request<Event>(`/api/events/${id}`, {
      method: 'PUT',
      body: JSON.stringify(data),
    }),

  delete: (id: string) =>
    request<void>(`/api/events/${id}`, { method: 'DELETE' }),

  markDone: (id: string) =>
    request<Event>(`/api/events/${id}/done`, { method: 'POST' }),

  snooze: (id: string, duration: string) =>
    request<Event>(`/api/events/${id}/snooze`, {
      method: 'POST',
      body: JSON.stringify({ duration }),
    }),

  extract: (formData: FormData) =>
    uploadRequest<ExtractionResult>('/api/events/extract', formData),

  confirmExtract: (data: ExtractConfirmInput) =>
    request<Event[]>('/api/events/extract/confirm', {
      method: 'POST',
      body: JSON.stringify(data),
    }),
};

// Notification API
export const notificationApi = {
  list: (unreadOnly = false) =>
    request<Notification[]>(`/api/notifications?unreadOnly=${unreadOnly}`),

  markRead: (id: string) =>
    request<void>(`/api/notifications/${id}/read`, { method: 'POST' }),

  unreadCount: () => request<{ count: number }>('/api/notifications/unread-count'),
};

// User API
export const userApi = {
  getProfile: () => request<UserProfile>('/api/user/profile'),

  updateProfile: (data: UpdateProfileInput) =>
    request<UserProfile>('/api/user/profile', {
      method: 'PUT',
      body: JSON.stringify(data),
    }),
};

// Calendar API
export const calendarApi = {
  startSync: () => `${API_URL}/api/calendar/sync/start`,
  triggerSync: () =>
    request<{ status: string }>('/api/calendar/sync/trigger', { method: 'POST' }),
  disconnect: () =>
    request<void>('/api/calendar/sync', { method: 'DELETE' }),
};

// Types
export interface Event {
  id: string;
  title: string;
  type: string;
  dueAt: string;
  timezone: string;
  source: string;
  aiConfidence: number;
  status: string;
  reminderSchedule: string[];
  notes: string | null;
  sourceFileUrl: string | null;
  createdAt: string;
  updatedAt: string;
}

export interface CreateEventInput {
  title: string;
  type: string;
  dueAt: string;
  timezone?: string;
  reminderSchedule?: string[];
  notes?: string | null;
}

export interface ExtractionResult {
  events: ExtractedEvent[];
  needsConfirmation: boolean;
  clarificationQuestion: string | null;
}

export interface ExtractedEvent {
  title: string;
  type: string;
  dueAt: string;
  timezone: string | null;
  aiConfidence: number;
  needsClarification: boolean;
}

export interface ExtractConfirmInput {
  events: {
    title: string;
    type: string;
    dueAt: string;
    timezone?: string | null;
    reminderSchedule?: string[];
    notes?: string | null;
  }[];
  sourceType: string;
  sourceReference?: string;
  sourceFileUrl?: string;
}

export interface Notification {
  id: string;
  eventId: string | null;
  title: string;
  message: string;
  isRead: boolean;
  channel: string;
  createdAt: string;
}

export interface UserProfile {
  email: string;
  displayName: string | null;
  timezone: string;
  plan: string;
  notificationPrefs: {
    channels?: string[];
    default_offsets?: string[];
  };
}

export interface UpdateProfileInput {
  displayName?: string;
  timezone?: string;
  notificationPrefs?: {
    channels?: string[];
    default_offsets?: string[];
  };
}
