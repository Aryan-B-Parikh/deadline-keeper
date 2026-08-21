'use client';

import { useState, useEffect } from 'react';
import { eventApi, calendarApi, type Event } from '@/lib/api';
import { useAuth } from '@/lib/auth';
import { cn, typeIcon } from '@/lib/utils';
import { Calendar as CalendarIcon, ChevronLeft, ChevronRight, RefreshCw } from 'lucide-react';

export default function CalendarPage() {
  const { user } = useAuth();
  const [events, setEvents] = useState<Event[]>([]);
  const [loading, setLoading] = useState(true);
  const [currentMonth, setCurrentMonth] = useState(new Date());
  const [syncing, setSyncing] = useState(false);
  useEffect(() => { if (!user) return; eventApi.list().then(setEvents).finally(() => setLoading(false)); }, [user]);
  const handleSync = async () => { setSyncing(true); try { await calendarApi.triggerSync(); setEvents(await eventApi.list()); } catch (err: unknown) { alert(err instanceof Error ? err.message : 'Sync failed'); } finally { setSyncing(false); } };
  const daysInMonth = new Date(currentMonth.getFullYear(), currentMonth.getMonth() + 1, 0).getDate();
  const firstDayOfWeek = new Date(currentMonth.getFullYear(), currentMonth.getMonth(), 1).getDay();
  const monthName = currentMonth.toLocaleString('en-US', { month: 'long', year: 'numeric' });
  const prevMonth = () => setCurrentMonth(new Date(currentMonth.getFullYear(), currentMonth.getMonth() - 1));
  const nextMonth = () => setCurrentMonth(new Date(currentMonth.getFullYear(), currentMonth.getMonth() + 1));
  const goToToday = () => setCurrentMonth(new Date());
  const getEventsForDay = (day: number) => { const dateStr = `${currentMonth.getFullYear()}-${String(currentMonth.getMonth() + 1).padStart(2, '0')}-${String(day).padStart(2, '0')}`; return events.filter((e) => { if (!e.dueAt) return false; try { return new Date(e.dueAt).toLocaleDateString('en-CA', { timeZone: e.timezone }) === dateStr; } catch { return e.dueAt.startsWith(dateStr); } }); };
  const today = new Date();
  const isToday = (day: number) => today.getFullYear() === currentMonth.getFullYear() && today.getMonth() === currentMonth.getMonth() && today.getDate() === day;
  if (loading) return <div className="space-y-6 animate-pulse"><div className="h-16 clay-surface rounded-[26px]" /><div className="h-[600px] clay-surface rounded-[28px]" /></div>;
  return (
    <div className="space-y-6">
      <div className="flex flex-col sm:flex-row sm:items-center justify-between gap-4">
        <div className="flex items-center gap-3"><div className="w-11 h-11 rounded-2xl clay-inset text-brand flex items-center justify-center"><CalendarIcon className="w-5 h-5" /></div><div><h1 className="text-2xl font-bold text-text-primary tracking-tight">Calendar</h1><p className="text-sm text-text-secondary">View and sync your deadlines</p></div></div>
        <button onClick={handleSync} disabled={syncing} className="clay-button flex items-center justify-center gap-2 bg-surface min-h-[44px] px-5 rounded-2xl text-sm font-semibold text-text-primary disabled:opacity-50"><RefreshCw className={cn('w-4 h-4', syncing && 'animate-spin text-brand')} />{syncing ? 'Syncing...' : 'Sync with Google'}</button>
      </div>
      <div className="clay-surface rounded-[28px] overflow-hidden flex flex-col h-[calc(100vh-160px)] min-h-[600px]">
        <div className="flex items-center justify-between p-4 sm:p-6 border-b border-border-subtle bg-surface-hover/55">
          <div className="flex items-center gap-2"><button onClick={prevMonth} aria-label="Previous month" className="clay-button min-h-[44px] min-w-[44px] rounded-xl flex items-center justify-center text-text-secondary hover:text-text-primary"><ChevronLeft className="w-5 h-5" /></button><button onClick={goToToday} className="clay-button px-3 min-h-[40px] rounded-xl text-sm font-semibold text-text-secondary hidden sm:block">Today</button><button onClick={nextMonth} aria-label="Next month" className="clay-button min-h-[44px] min-w-[44px] rounded-xl flex items-center justify-center text-text-secondary hover:text-text-primary"><ChevronRight className="w-5 h-5" /></button></div>
          <h2 className="text-lg font-bold text-text-primary tracking-tight">{monthName}</h2>
        </div>
        <div className="flex flex-col flex-1 overflow-auto">
          <div className="grid grid-cols-7 border-b border-border-subtle bg-surface-hover/70 sticky top-0 z-10">{['Sun','Mon','Tue','Wed','Thu','Fri','Sat'].map(day => <div key={day} className="p-3 text-center text-xs font-bold text-text-muted uppercase tracking-wider"><span className="hidden sm:inline">{day}</span><span className="sm:hidden">{day.charAt(0)}</span></div>)}</div>
          <div className="grid grid-cols-7 flex-1 auto-rows-fr">{Array.from({ length: firstDayOfWeek }).map((_, i) => <div key={`empty-${i}`} className="p-2 min-h-[100px] border-b border-r border-border-subtle/50 bg-surface-inset/35" />)}
            {Array.from({ length: daysInMonth }).map((_, i) => { const day = i + 1; const dayEvents = getEventsForDay(day); const todayMatches = isToday(day); return <div key={day} className={cn('p-1.5 sm:p-2 min-h-[100px] border-b border-r border-border-subtle/50 transition-colors hover:bg-surface-hover/60 flex flex-col', todayMatches && 'bg-clay-blue/10')}><div className="flex items-start justify-end mb-1"><span className={cn('text-xs font-bold w-7 h-7 flex items-center justify-center rounded-xl', todayMatches ? 'bg-brand text-white shadow-neu' : 'text-text-secondary')}>{day}</span></div><div className="flex-1 overflow-y-auto scrollbar-hide space-y-1 pr-0.5">{dayEvents.map(event => <div key={event.id} className={cn('text-[10px] sm:text-xs px-2 py-1.5 rounded-xl truncate font-semibold border shadow-neu-inset transition-transform hover:scale-[1.02]', event.status === 'overdue' ? 'pastel-pink text-danger' : event.status === 'due_soon' ? 'pastel-peach text-warning' : event.status === 'done' ? 'pastel-mint text-success opacity-65' : 'pastel-blue text-text-primary')} title={event.title}><span aria-hidden="true" className="mr-1">{typeIcon(event.type)}</span>{event.title}</div>)}</div></div>; })}
          </div>
        </div>
      </div>
    </div>
  );
}
