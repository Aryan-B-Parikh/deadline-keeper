import { cn, formatDueAt, sourceIcon, typeIcon } from '@/lib/utils';
import { Clock, Calendar as CalendarIcon, CheckCircle2, AlertCircle, Clock3, ArrowUpRight } from 'lucide-react';

interface EventCardProps {
  event: { id: string; title: string; type: string; dueAt: string; timezone: string; source: string; status: string; aiConfidence: number | null; notes: string | null };
  onMarkDone?: (id: string) => void;
  onSnooze?: (id: string) => void;
  onDelete?: (id: string) => void;
}

export function EventCard({ event, onMarkDone, onSnooze, onDelete }: EventCardProps) {
  const hasLowConfidence = event.aiConfidence !== null && event.aiConfidence < 0.7;
  const getStatusConfig = () => {
    switch (event.status) {
      case 'overdue': return { border: 'border-danger/30 hover:border-danger/55', icon: AlertCircle, color: 'text-danger', accent: 'bg-danger', label: 'Overdue' };
      case 'due_soon': return { border: 'border-warning/30 hover:border-warning/55', icon: Clock3, color: 'text-warning', accent: 'bg-warning', label: 'Due soon' };
      case 'done': return { border: 'border-success/25 hover:border-success/40', icon: CheckCircle2, color: 'text-success', accent: 'bg-success', label: 'Completed' };
      default: return { border: 'border-border-subtle hover:border-brand/30', icon: CalendarIcon, color: 'text-brand', accent: 'bg-brand', label: 'Upcoming' };
    }
  };
  const config = getStatusConfig();
  const StatusIcon = config.icon;

  return (
    <article className={cn('group relative overflow-hidden bg-surface rounded-[22px] border p-5 transition-all duration-200', config.border, event.status === 'done' ? 'opacity-65' : 'hover:-translate-y-0.5 hover:shadow-soft')}>
      <span className={cn('absolute left-0 top-5 bottom-5 w-1 rounded-r-full opacity-80', config.accent)} aria-hidden="true" />
      <div className="flex items-start justify-between gap-4 pl-1">
        <div className="flex items-start gap-4 min-w-0">
          <div className="flex items-center justify-center w-11 h-11 rounded-2xl bg-surface-hover border border-border-subtle flex-shrink-0 group-hover:border-brand/20 group-hover:bg-brand/5 transition-all duration-200"><span aria-hidden="true" className="text-lg">{typeIcon(event.type)}</span></div>
          <div className="min-w-0 pt-0.5">
            <div className="flex items-center gap-2 mb-1"><span className={cn('w-1.5 h-1.5 rounded-full', config.accent)} aria-hidden="true" /><span className={cn('text-[10px] font-bold uppercase tracking-[0.13em]', config.color)}>{config.label}</span></div>
            <h3 className={cn('font-bold text-[16px] text-text-primary truncate tracking-[-0.015em]', event.status === 'done' && 'line-through text-text-secondary')}>{event.title}</h3>
            <div className="flex items-center gap-1.5 text-sm text-text-secondary mt-1.5"><Clock className="w-3.5 h-3.5 flex-shrink-0 text-text-muted" /><span className="truncate">{formatDueAt(event.dueAt, event.timezone)}</span></div>
            {event.notes && <p className="text-xs text-text-muted mt-2 line-clamp-2 leading-relaxed">{event.notes}</p>}
          </div>
        </div>
        <div className="hidden sm:flex items-center justify-center w-8 h-8 rounded-full border border-border-subtle text-text-muted opacity-0 group-hover:opacity-100 transition-opacity"><ArrowUpRight className="w-4 h-4" /></div>
      </div>

      <div className="flex items-center justify-between mt-5 pt-3.5 border-t border-border-subtle gap-3 pl-1">
        <div className="flex items-center gap-2 text-xs text-text-muted min-w-0"><span aria-hidden="true">{sourceIcon(event.source)}</span><span className="truncate">{event.source.replace('_', ' ')}</span>{hasLowConfidence && <span className="flex items-center gap-1 text-warning whitespace-nowrap ml-2"><AlertCircle className="w-3 h-3" /> low confidence</span>}</div>
        {event.status !== 'done' && <div className="flex items-center gap-1 flex-shrink-0 opacity-100">
          {onSnooze && <button type="button" onClick={() => onSnooze(event.id)} aria-label={`Snooze ${event.title}`} className="text-xs px-3 min-h-[36px] text-text-secondary hover:text-text-primary hover:bg-surface-hover border border-transparent hover:border-border-subtle rounded-xl transition-all">Snooze</button>}
          {onMarkDone && <button type="button" onClick={() => onMarkDone(event.id)} aria-label={`Mark ${event.title} as done`} className="text-xs px-3 min-h-[36px] text-success hover:bg-success/10 rounded-xl transition-colors font-bold flex items-center gap-1.5"><CheckCircle2 className="w-3.5 h-3.5" /> Done</button>}
        </div>}
      </div>
    </article>
  );
}
