import { cn, statusColor, typeIcon, sourceIcon, formatDueDate } from '@/lib/utils';

interface EventCardProps {
  event: {
    id: string;
    title: string;
    type: string;
    dueDate: string;
    dueTime: string | null;
    timezone: string;
    source: string;
    status: string;
    confidenceScore: number;
    notes: string | null;
  };
  onMarkDone?: (id: string) => void;
  onSnooze?: (id: string) => void;
  onDelete?: (id: string) => void;
}

export function EventCard({ event, onMarkDone, onSnooze, onDelete }: EventCardProps) {
  return (
    <div className="bg-white rounded-lg border border-gray-200 p-4 hover:border-gray-300 transition-colors">
      <div className="flex items-start justify-between gap-3">
        <div className="flex items-start gap-3 min-w-0">
          <span className="text-lg flex-shrink-0">{typeIcon(event.type)}</span>
          <div className="min-w-0">
            <h3 className="font-medium text-gray-900 truncate">{event.title}</h3>
            <p className="text-sm text-gray-500 mt-0.5">
              {formatDueDate(event.dueDate, event.dueTime)}
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

      <div className="flex items-center justify-between mt-3 pt-3 border-t border-gray-100">
        <div className="flex items-center gap-2 text-xs text-gray-400">
          <span>{sourceIcon(event.source)}</span>
          <span>{event.source.replace('_', ' ')}</span>
          {event.confidenceScore < 0.7 && (
            <span className="text-amber-500">⚠️ low confidence</span>
          )}
        </div>

        {event.status !== 'done' && (
          <div className="flex items-center gap-1">
            {onMarkDone && (
              <button
                onClick={() => onMarkDone(event.id)}
                className="text-xs px-2 py-1 text-green-600 hover:bg-green-50 rounded transition-colors"
              >
                ✓ Done
              </button>
            )}
            {onSnooze && (
              <button
                onClick={() => onSnooze(event.id)}
                className="text-xs px-2 py-1 text-amber-600 hover:bg-amber-50 rounded transition-colors"
              >
                Snooze
              </button>
            )}
            {onDelete && (
              <button
                onClick={() => onDelete(event.id)}
                className="text-xs px-2 py-1 text-red-500 hover:bg-red-50 rounded transition-colors"
              >
                Delete
              </button>
            )}
          </div>
        )}
      </div>
    </div>
  );
}
