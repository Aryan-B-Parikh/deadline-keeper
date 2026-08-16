import { clsx, type ClassValue } from 'clsx';

export function cn(...inputs: ClassValue[]) {
  return clsx(inputs);
}

export function formatDueDate(date: string, time: string | null): string {
  const d = new Date(date + 'T00:00:00');
  const now = new Date();
  const diffDays = Math.ceil((d.getTime() - now.getTime()) / (1000 * 60 * 60 * 24));

  const dateStr = d.toLocaleDateString('en-US', {
    month: 'short',
    day: 'numeric',
    year: 'numeric',
  });

  const timeStr = time
    ? ` at ${time}`
    : '';

  if (diffDays < 0) return `${dateStr}${timeStr} (overdue)`;
  if (diffDays === 0) return `Today${timeStr}`;
  if (diffDays === 1) return `Tomorrow${timeStr}`;
  if (diffDays <= 7) return `In ${diffDays} days — ${dateStr}${timeStr}`;
  return `${dateStr}${timeStr}`;
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
