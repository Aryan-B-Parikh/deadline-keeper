import { clsx, type ClassValue } from 'clsx';

export function cn(...inputs: ClassValue[]) {
  return clsx(inputs);
}

export function formatDueAt(dueAt: string, timezone: string): string {
  const d = new Date(dueAt);
  const now = new Date();
  
  // Calculate difference in days (roughly, based on local time difference for simplicity)
  const diffTime = d.getTime() - now.getTime();
  const diffDays = Math.ceil(diffTime / (1000 * 60 * 60 * 24));

  const dateStr = d.toLocaleDateString('en-US', {
    timeZone: timezone,
    month: 'short',
    day: 'numeric',
    year: 'numeric',
  });

  const timeStr = d.toLocaleTimeString('en-US', {
    timeZone: timezone,
    hour: '2-digit',
    minute: '2-digit',
    hour12: false
  });

  if (diffDays < 0) return `${dateStr} at ${timeStr} (overdue)`;
  if (diffDays === 0) return `Today at ${timeStr}`;
  if (diffDays === 1) return `Tomorrow at ${timeStr}`;
  if (diffDays <= 7) return `In ${diffDays} days — ${dateStr} at ${timeStr}`;
  return `${dateStr} at ${timeStr}`;
}

export function toLocalDatetimeString(isoString: string): string {
  if (!isoString) return '';
  const d = new Date(isoString);
  if (isNaN(d.getTime())) return '';
  const pad = (n: number) => String(n).padStart(2, '0');
  return `${d.getFullYear()}-${pad(d.getMonth() + 1)}-${pad(d.getDate())}T${pad(d.getHours())}:${pad(d.getMinutes())}`;
}

export function statusColor(status: string): string {
  switch (status) {
    case 'upcoming': return 'bg-blue-100 text-blue-800';
    case 'due_soon': return 'bg-amber-100 text-amber-800';
    case 'overdue': return 'bg-red-100 text-red-800';
    case 'done': return 'bg-green-100 text-green-800';
    default: return 'bg-gray-100 text-gray-800';
  }
}

export function typeIcon(type: string): string {
  switch (type) {
    case 'exam': return '📝';
    case 'submission': return '📋';
    case 'hackathon': return '💻';
    default: return '📌';
  }
}

export function sourceIcon(source: string): string {
  switch (source) {
    case 'manual': return '✏️';
    case 'screenshot': return '📸';
    case 'pasted_text': return '📋';
    case 'email': return '📧';
    case 'calendar_sync': return '📅';
    default: return '📥';
  }
}
