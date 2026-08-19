import { cn, statusColor, typeIcon, sourceIcon, formatDueAt } from '@/lib/utils';

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

  return (
    <article className="bg-white rounded-lg border border-gray-200 p-4 hover:border-gray-300 transition-colors">
      <div className="flex items-start justify-between gap-3">
        <div className="flex items-start gap-3 min-w-0">
          <span aria-hidden="true" className="text-lg flex-shrink-0">{typeIcon(event.type)}</span>
          <div className="min-w-0">
            <h3 className="font-medium text-gray-900 truncate">{event.title}</h3>
            <p className="text-sm text-gray-500 mt-0.5">
              {formatDueAt(event.dueAt, event.timezone)}
            </p>
            {event.notes && (
              <p className="text-xs text-gray-400 mt-1 line-clamp-2">{event.notes}</p>
            )}
          </div>
        </div>

        <div className="flex items-center gap-2 flex-shrink-0">
          <span className={cn('text-xs px-2 py-0.5 rounded-full font-medium', statusColor(event.status))}>
            {event.status.replace('_', ' ')}
          </span>
        </div>
      </div>

      <div className="flex items-center justify-between mt-3 pt-3 border-t border-gray-100 gap-3">
        <div className="flex items-center gap-2 text-xs text-gray-400 min-w-0">
          <span aria-hidden="true">{sourceIcon(event.source)}</span>
          <span className="truncate">{event.source.replace('_', ' ')}</span>
          {hasLowConfidence && (
            <span className="text-amber-500 whitespace-nowrap" title="AI extraction confidence is below 70%">
              ⚠️ low confidence
            </span>
          )}
        </div>

        {event.status !== 'done' && (
          <div className="flex items-center gap-1 flex-shrink-0">
            {onMarkDone && (
              <button
                type="button"
                onClick={() => onMarkDone(event.id)}
                aria-label={`Mark ${event.title} as done`}
                className="text-xs px-2 py-1 text-green-600 hover:bg-green-50 rounded transition-colors"
              >
                ✓ Done
              </button>
            )}
            {onSnooze && (
              <button
                type="button"
                onClick={() => onSnooze(event.id)}
                aria-label={`Snooze ${event.title}`}
                className="text-xs px-2 py-1 text-amber-600 hover:bg-amber-50 rounded transition-colors"
              >
                Snooze
              </button>
            )}
            {onDelete && (
              <button
                type="button"
                onClick={() => onDelete(event.id)}
                aria-label={`Delete ${event.title}`}
                className="text-xs px-2 py-1 text-red-500 hover:bg-red-50 rounded transition-colors"
              >
                Delete
              </button>
            )}
          </div>
        )}
      </div>
    </article>
  );
}
