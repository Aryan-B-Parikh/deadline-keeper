import { cn, formatDueAt, sourceIcon, typeIcon } from '@/lib/utils';
import { Clock, Calendar as CalendarIcon, CheckCircle2, AlertCircle, Clock3 } from 'lucide-react';

interface EventCardProps {
  event: { id: string; title: string; type: string; dueAt: string; timezone: string; source: string; status: string; aiConfidence: number | null; notes: string | null };
  onMarkDone?: (id: string) => void;
  onSnooze?: (id: string) => void;
  onDelete?: (id: string) => void;
}

export function EventCard({ event, onMarkDone, onSnooze }: EventCardProps) {
  const hasLowConfidence = event.aiConfidence !== null && event.aiConfidence < 0.7;
  const config = (() => {
    switch (event.status) {
      case 'overdue': return { icon: AlertCircle, color: 'text-danger', accent: 'bg-danger', label: 'Overdue' };
      case 'due_soon': return { icon: Clock3, color: 'text-warning', accent: 'bg-warning', label: 'Due soon' };
      case 'done': return { icon: CheckCircle2, color: 'text-success', accent: 'bg-success', label: 'Completed' };
      default: return { icon: CalendarIcon, color: 'text-brand', accent: 'bg-brand', label: 'Upcoming' };
    }
  })();
  const StatusIcon = config.icon;

  return (
    <article className={cn(
      'group relative overflow-hidden rounded-[24px] bg-surface p-5',
      'shadow-neu transition-all duration-200 hover:-translate-y-0.5 hover:shadow-neu-hover',
      event.status === 'done' && 'opacity-65'
    )}>
      <span className={cn('absolute left-0 top-6 bottom-6 w-1 rounded-r-full opacity-80', config.accent)} aria-hidden="true" />
      <div className="flex items-start justify-between gap-4 pl-1">
        <div className="flex min-w-0 items-start gap-4">
          <div className="flex h-11 w-11 flex-shrink-0 items-center justify-center rounded-2xl bg-surface shadow-neu-inset transition-shadow duration-200 group-hover:shadow-neu-inset-strong">
            <span aria-hidden="true" className="text-lg">{typeIcon(event.type)}</span>
          </div>
          <div className="min-w-0 pt-0.5">
            <div className="mb-1 flex items-center gap-2">
              <StatusIcon className={cn('h-3.5 w-3.5', config.color)} aria-hidden="true" />
              <span className={cn('text-[10px] font-bold uppercase tracking-[0.13em]', config.color)}>{config.label}</span>
            </div>
            <h3 className={cn('truncate text-[16px] font-bold tracking-[-0.015em] text-text-primary', event.status === 'done' && 'text-text-secondary line-through')}>{event.title}</h3>
            <div className="mt-1.5 flex items-center gap-1.5 text-sm text-text-secondary">
              <Clock className="h-3.5 w-3.5 flex-shrink-0 text-text-muted" />
              <span className="truncate">{formatDueAt(event.dueAt, event.timezone)}</span>
            </div>
            {event.notes && <p className="mt-2 line-clamp-2 text-xs leading-relaxed text-text-muted">{event.notes}</p>}
          </div>
        </div>
      </div>

      <div className="mt-5 flex items-center justify-between gap-3 border-t border-border-subtle/70 pt-3.5 pl-1">
        <div className="flex min-w-0 items-center gap-2 text-xs text-text-muted">
          <span aria-hidden="true">{sourceIcon(event.source)}</span>
          <span className="truncate">{event.source.replace('_', ' ')}</span>
          {hasLowConfidence && <span className="ml-2 flex items-center gap-1 whitespace-nowrap text-warning"><AlertCircle className="h-3 w-3" /> low confidence</span>}
        </div>
        {event.status !== 'done' && <div className="flex flex-shrink-0 items-center gap-1">
          {onSnooze && <button type="button" onClick={() => onSnooze(event.id)} aria-label={`Snooze ${event.title}`} className="min-h-[40px] rounded-xl px-3 text-xs text-text-secondary transition-all duration-150 hover:text-text-primary hover:shadow-neu-inset active:shadow-neu-inset-strong">Snooze</button>}
          {onMarkDone && <button type="button" onClick={() => onMarkDone(event.id)} aria-label={`Mark ${event.title} as done`} className="flex min-h-[40px] items-center gap-1.5 rounded-xl px-3 text-xs font-bold text-success transition-all duration-150 hover:shadow-neu active:shadow-neu-inset-strong"><CheckCircle2 className="h-3.5 w-3.5" /> Done</button>}
        </div>}
      </div>
    </article>
  );
}
