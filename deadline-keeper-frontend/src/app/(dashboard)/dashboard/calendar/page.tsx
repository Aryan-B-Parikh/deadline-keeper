'use client';

import { useState, useEffect } from 'react';
import { eventApi, calendarApi, type Event } from '@/lib/api';
import { useAuth } from '@/lib/auth';
import { cn, typeIcon } from '@/lib/utils';
import { Calendar as CalendarIcon, ChevronLeft, ChevronRight, RefreshCw, Loader2 } from 'lucide-react';

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
  const goToToday = () => setCurrentMonth(new Date());

  const getEventsForDay = (day: number) => {
    const dateStr = `${currentMonth.getFullYear()}-${String(currentMonth.getMonth() + 1).padStart(2, '0')}-${String(day).padStart(2, '0')}`;
    return events.filter((e) => {
      if (!e.dueAt) return false;
      try {
        const localDateStr = new Date(e.dueAt).toLocaleDateString('en-CA', { timeZone: e.timezone });
        return localDateStr === dateStr;
      } catch (err) {
        return e.dueAt.startsWith(dateStr); // fallback
      }
    });
  };

  const today = new Date();
  const isToday = (day: number) =>
    today.getFullYear() === currentMonth.getFullYear() &&
    today.getMonth() === currentMonth.getMonth() &&
    today.getDate() === day;

  if (loading) {
    return (
      <div className="space-y-6 animate-pulse">
        <div className="flex justify-between items-center"><div className="h-8 w-32 bg-surface-hover rounded-lg" /><div className="h-10 w-40 bg-surface-hover rounded-xl" /></div>
        <div className="flex justify-between items-center"><div className="h-8 w-8 bg-surface-hover rounded-lg" /><div className="h-6 w-32 bg-surface-hover rounded-lg" /><div className="h-8 w-8 bg-surface-hover rounded-lg" /></div>
        <div className="h-[600px] bg-surface-hover rounded-2xl border border-border-subtle" />
      </div>
    );
  }

  return (
    <div className="space-y-6 animate-in fade-in slide-in-from-bottom-4 duration-500">
      <div className="flex flex-col sm:flex-row sm:items-center justify-between gap-4">
        <div className="flex items-center gap-3">
          <div className="w-10 h-10 rounded-xl bg-brand/10 text-brand flex items-center justify-center shadow-sm">
            <CalendarIcon className="w-5 h-5" />
          </div>
          <div>
            <h1 className="text-2xl font-bold text-text-primary tracking-tight">Calendar</h1>
            <p className="text-sm text-text-secondary">View and sync your deadlines</p>
          </div>
        </div>
        <button
          onClick={handleSync}
          disabled={syncing}
          className="flex items-center justify-center gap-2 bg-surface border border-border-strong text-text-primary px-4 py-2.5 rounded-xl hover:bg-surface-hover hover:border-brand/50 transition-all text-sm font-medium disabled:opacity-50 disabled:hover:border-border-strong shadow-sm"
        >
          <RefreshCw className={cn("w-4 h-4", syncing && "animate-spin text-brand")} />
          {syncing ? 'Syncing...' : 'Sync with Google'}
        </button>
      </div>

      <div className="bg-surface border border-border-subtle rounded-2xl shadow-sm overflow-hidden flex flex-col h-[calc(100vh-160px)] min-h-[600px]">
        {/* Month nav */}
        <div className="flex items-center justify-between p-4 sm:p-6 border-b border-border-subtle bg-surface-hover/30">
          <div className="flex items-center gap-1">
            <button onClick={prevMonth} className="p-2 text-text-secondary hover:text-text-primary hover:bg-surface rounded-lg transition-colors border border-transparent hover:border-border-subtle">
              <ChevronLeft className="w-5 h-5" />
            </button>
            <button onClick={goToToday} className="px-3 py-1.5 text-sm font-medium text-text-secondary hover:text-text-primary hover:bg-surface rounded-lg transition-colors border border-transparent hover:border-border-subtle hidden sm:block">
              Today
            </button>
            <button onClick={nextMonth} className="p-2 text-text-secondary hover:text-text-primary hover:bg-surface rounded-lg transition-colors border border-transparent hover:border-border-subtle">
              <ChevronRight className="w-5 h-5" />
            </button>
          </div>
          <h2 className="text-lg font-semibold text-text-primary tracking-tight">{monthName}</h2>
        </div>

        {/* Calendar grid */}
        <div className="flex flex-col flex-1 overflow-auto">
          {/* Day headers */}
          <div className="grid grid-cols-7 border-b border-border-subtle bg-surface-hover/50 sticky top-0 z-10">
            {['Sun', 'Mon', 'Tue', 'Wed', 'Thu', 'Fri', 'Sat'].map((day) => (
              <div key={day} className="p-3 text-center text-xs font-semibold text-text-secondary uppercase tracking-wider">
                <span className="hidden sm:inline">{day}</span>
                <span className="sm:hidden">{day.charAt(0)}</span>
              </div>
            ))}
          </div>

          {/* Days */}
          <div className="grid grid-cols-7 flex-1 auto-rows-fr">
            {Array.from({ length: firstDayOfWeek }).map((_, i) => (
              <div key={`empty-${i}`} className="p-2 min-h-[100px] border-b border-r border-border-subtle/50 bg-surface-hover/20" />
            ))}
            {Array.from({ length: daysInMonth }).map((_, i) => {
              const day = i + 1;
              const dayEvents = getEventsForDay(day);
              const todayMatches = isToday(day);
              return (
                <div
                  key={day}
                  className={cn(
                    'p-1.5 sm:p-2 min-h-[100px] border-b border-r border-border-subtle/50 transition-colors hover:bg-surface-hover/30 flex flex-col',
                    todayMatches && 'bg-brand/5'
                  )}
                >
                  <div className="flex items-start justify-end mb-1">
                    <span className={cn(
                      'text-xs font-medium w-6 h-6 flex items-center justify-center rounded-full',
                      todayMatches ? 'bg-brand text-white shadow-sm' : 'text-text-secondary'
                    )}>
                      {day}
                    </span>
                  </div>
                  <div className="flex-1 overflow-y-auto scrollbar-hide space-y-1 pr-0.5">
                    {dayEvents.map((event) => (
                      <div
                        key={event.id}
                        className={cn(
                          'text-[10px] sm:text-xs px-1.5 py-1 rounded-md truncate font-medium border shadow-sm transition-transform hover:scale-[1.02] cursor-default',
                          event.status === 'overdue' ? 'bg-danger/10 text-danger border-danger/20' :
                          event.status === 'due_soon' ? 'bg-warning/10 text-warning border-warning/20' :
                          event.status === 'done' ? 'bg-success/5 text-success border-success/20 opacity-60' :
                          'bg-surface-elevated text-text-primary border-border-strong hover:border-brand/30'
                        )}
                        title={event.title}
                      >
                        <span aria-hidden="true" className="mr-1">{typeIcon(event.type)}</span>
                        {event.title}
                      </div>
                    ))}
                  </div>
                </div>
              );
            })}
          </div>
        </div>
      </div>
    </div>
  );
}
