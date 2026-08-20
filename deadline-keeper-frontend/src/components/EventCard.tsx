import { cn, formatDueAt, sourceIcon, typeIcon } from '@/lib/utils';
import { Clock, Calendar as CalendarIcon, CheckCircle2, AlertCircle, Clock3 } from 'lucide-react';

interface EventCardProps {
  event: {
    id: string;
    title: string;
    type: string;
    dueAt: string;
    timezone: string;
    source: string;
    status: string;
    aiConfidence: number | null;
    notes: string | null;
  };
  onMarkDone?: (id: string) => void;
  onSnooze?: (id: string) => void;
  onDelete?: (id: string) => void;
}

export function EventCard({ event, onMarkDone, onSnooze, onDelete }: EventCardProps) {
  const hasLowConfidence = event.aiConfidence !== null && event.aiConfidence < 0.7;

  // Determine semantic border and icon based on status
  const getStatusConfig = () => {
    switch (event.status) {
      case 'overdue':
        return { border: 'border-danger/30 hover:border-danger/60', icon: AlertCircle, color: 'text-danger', glow: 'hover:shadow-[0_0_12px_rgba(220,38,38,0.1)]' };
      case 'due_soon':
        return { border: 'border-warning/30 hover:border-warning/60', icon: Clock3, color: 'text-warning', glow: 'hover:shadow-[0_0_12px_rgba(217,119,6,0.1)]' };
      case 'done':
        return { border: 'border-success/30 hover:border-success/50', icon: CheckCircle2, color: 'text-success', glow: 'hover:shadow-[0_0_12px_rgba(22,163,74,0.1)]', bg: 'bg-surface-hover/50' };
      case 'upcoming':
      default:
        return { border: 'border-border-subtle hover:border-border-strong', icon: CalendarIcon, color: 'text-brand', glow: 'hover:shadow-soft' };
    }
  };

  const config = getStatusConfig();
  const StatusIcon = config.icon;

  return (
    <article 
      className={cn(
        "group relative bg-surface rounded-xl border p-4 transition-all duration-200",
        config.border,
        config.glow,
        config.bg,
        event.status === 'done' ? "opacity-75" : "hover:-translate-y-0.5"
      )}
    >
      <div className="flex items-start justify-between gap-3">
        <div className="flex items-start gap-3 min-w-0">
          <div className={cn(
            "flex items-center justify-center w-10 h-10 rounded-lg bg-surface-hover border border-border-subtle text-lg flex-shrink-0 transition-colors",
            "group-hover:bg-surface-elevated group-hover:border-border-strong"
          )}>
            <span aria-hidden="true">{typeIcon(event.type)}</span>
          </div>
          <div className="min-w-0 pt-0.5">
            <h3 className={cn(
              "font-medium text-text-primary truncate transition-colors",
              event.status === 'done' && "line-through text-text-secondary"
            )}>
              {event.title}
            </h3>
            <div className="flex items-center gap-1.5 text-sm text-text-secondary mt-1">
              <Clock className="w-3.5 h-3.5 flex-shrink-0" />
              <span className="truncate">{formatDueAt(event.dueAt, event.timezone)}</span>
            </div>
            {event.notes && (
              <p className="text-xs text-text-muted mt-2 line-clamp-2 leading-relaxed">{event.notes}</p>
            )}
          </div>
        </div>

        <div className="flex items-center gap-1.5 flex-shrink-0">
          <StatusIcon className={cn("w-4 h-4", config.color)} aria-hidden="true" />
          <span className={cn("text-xs font-medium hidden sm:inline-block", config.color)}>
            {event.status.replace('_', ' ')}
          </span>
        </div>
      </div>

      <div className="flex items-center justify-between mt-4 pt-3 border-t border-border-subtle gap-3">
        <div className="flex items-center gap-2 text-xs text-text-muted min-w-0">
          <span aria-hidden="true" className="opacity-75">{sourceIcon(event.source)}</span>
          <span className="truncate">{event.source.replace('_', ' ')}</span>
          {hasLowConfidence && (
            <span className="flex items-center gap-1 text-warning whitespace-nowrap ml-2" title="AI extraction confidence is below 70%">
              <AlertCircle className="w-3 h-3" /> low confidence
            </span>
          )}
        </div>

        {event.status !== 'done' && (
          <div className="flex items-center gap-1 flex-shrink-0 opacity-0 group-hover:opacity-100 transition-opacity duration-200 sm:opacity-100">
            {onSnooze && (
              <button
                type="button"
                onClick={() => onSnooze(event.id)}
                aria-label={`Snooze ${event.title}`}
                className="text-xs px-2.5 py-1.5 text-text-secondary hover:text-text-primary hover:bg-surface-hover border border-transparent hover:border-border-subtle rounded-lg transition-all"
              >
                Snooze
              </button>
            )}
            {onMarkDone && (
              <button
                type="button"
                onClick={() => onMarkDone(event.id)}
                aria-label={`Mark ${event.title} as done`}
                className="text-xs px-2.5 py-1.5 text-success hover:bg-success/10 rounded-lg transition-colors font-medium flex items-center gap-1.5"
              >
                <CheckCircle2 className="w-3.5 h-3.5" /> Done
              </button>
            )}
          </div>
        )}
      </div>
    </article>
  );
}
