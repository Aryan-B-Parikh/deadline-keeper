'use client';

import { useState, useEffect } from 'react';
import { eventApi, calendarApi, type Event } from '@/lib/api';
import { useAuth } from '@/lib/auth';
import { cn, typeIcon } from '@/lib/utils';

export default function CalendarPage() {
  const { user } = useAuth();
  const [events, setEvents] = useState<Event[]>([]);
  const [loading, setLoading] = useState(true);
  const [currentMonth, setCurrentMonth] = useState(new Date());
  const [syncing, setSyncing] = useState(false);

  useEffect(() => {
    if (!user) return;
    eventApi.list().then(setEvents).finally(() => setLoading(false));
  }, [user]);

  const handleSync = async () => {
    setSyncing(true);
    try {
      await calendarApi.triggerSync();
      const updated = await eventApi.list();
      setEvents(updated);
    } catch (err: any) {
      alert(err.message || 'Sync failed');
    } finally {
      setSyncing(false);
    }
  };

  const daysInMonth = new Date(currentMonth.getFullYear(), currentMonth.getMonth() + 1, 0).getDate();
  const firstDayOfWeek = new Date(currentMonth.getFullYear(), currentMonth.getMonth(), 1).getDay();
  const monthName = currentMonth.toLocaleString('en-US', { month: 'long', year: 'numeric' });

  const prevMonth = () => setCurrentMonth(new Date(currentMonth.getFullYear(), currentMonth.getMonth() - 1));
  const nextMonth = () => setCurrentMonth(new Date(currentMonth.getFullYear(), currentMonth.getMonth() + 1));

  const getEventsForDay = (day: number) => {
    const dateStr = `${currentMonth.getFullYear()}-${String(currentMonth.getMonth() + 1).padStart(2, '0')}-${String(day).padStart(2, '0')}`;
    return events.filter((e) => e.dueDate === dateStr);
  };

  const today = new Date();
  const isToday = (day: number) =>
    today.getFullYear() === currentMonth.getFullYear() &&
    today.getMonth() === currentMonth.getMonth() &&
    today.getDate() === day;

  if (loading) return <div className="text-center text-gray-400 py-12">Loading...</div>;

  return (
    <div>
      <div className="flex items-center justify-between mb-6">
        <h1 className="text-2xl font-bold text-gray-900">Calendar</h1>
        <button
          onClick={handleSync}
          disabled={syncing}
          className="bg-brand-600 text-white px-4 py-2 rounded-lg hover:bg-brand-700 transition-colors text-sm font-medium disabled:opacity-50"
        >
          {syncing ? 'Syncing...' : '🔄 Sync Google Calendar'}
        </button>
      </div>

      {/* Month nav */}
      <div className="flex items-center justify-between mb-4">
        <button onClick={prevMonth} className="text-gray-500 hover:text-gray-700 px-3 py-1">
          ←
        </button>
        <h2 className="text-lg font-semibold text-gray-900">{monthName}</h2>
        <button onClick={nextMonth} className="text-gray-500 hover:text-gray-700 px-3 py-1">
          →
        </button>
      </div>

      {/* Calendar grid */}
      <div className="bg-white rounded-lg border border-gray-200 overflow-hidden">
        {/* Day headers */}
        <div className="grid grid-cols-7 bg-gray-50 border-b border-gray-200">
          {['Sun', 'Mon', 'Tue', 'Wed', 'Thu', 'Fri', 'Sat'].map((day) => (
            <div key={day} className="p-2 text-center text-xs font-medium text-gray-500">
              {day}
            </div>
          ))}
        </div>

        {/* Days */}
        <div className="grid grid-cols-7">
          {Array.from({ length: firstDayOfWeek }).map((_, i) => (
            <div key={`empty-${i}`} className="p-2 min-h-[80px] border-b border-r border-gray-100" />
          ))}
          {Array.from({ length: daysInMonth }).map((_, i) => {
            const day = i + 1;
            const dayEvents = getEventsForDay(day);
            return (
              <div
                key={day}
                className={cn(
                  'p-2 min-h-[80px] border-b border-r border-gray-100',
                  isToday(day) && 'bg-brand-50'
                )}
              >
                <div className={cn(
                  'text-xs font-medium mb-1',
                  isToday(day) ? 'text-brand-600' : 'text-gray-600'
                )}>
                  {day}
                </div>
                {dayEvents.slice(0, 2).map((event) => (
                  <div
                    key={event.id}
                    className={cn(
                      'text-[10px] px-1 py-0.5 rounded mb-0.5 truncate',
                      event.status === 'overdue' ? 'bg-red-100 text-red-700' :
                      event.status === 'due_soon' ? 'bg-amber-100 text-amber-700' :
                      event.status === 'done' ? 'bg-green-100 text-green-700' :
                      'bg-blue-100 text-blue-700'
                    )}
                  >
                    {typeIcon(event.type)} {event.title}
                  </div>
                ))}
                {dayEvents.length > 2 && (
                  <div className="text-[10px] text-gray-400">+{dayEvents.length - 2} more</div>
                )}
              </div>
            );
          })}
        </div>
      </div>
    </div>
  );
}
