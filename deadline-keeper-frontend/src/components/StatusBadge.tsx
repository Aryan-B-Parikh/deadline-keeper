import { cn, statusColor } from '@/lib/utils';

interface StatusBadgeProps {
  status: string;
}

export function StatusBadge({ status }: StatusBadgeProps) {
  return (
    <span className={cn('text-xs px-2 py-0.5 rounded-full font-medium', statusColor(status))}>
      {status.replace('_', ' ')}
    </span>
  );
}
