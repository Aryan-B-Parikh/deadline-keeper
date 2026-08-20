'use client';

import { useState, useEffect, useCallback } from 'react';
import { eventApi, type Event } from '@/lib/api';
import { EventCard } from '@/components/EventCard';
import { useAuth } from '@/lib/auth';
import Link from 'next/link';
import { cn } from '@/lib/utils';
import { Calendar, AlertCircle, Clock3, CheckCircle2, Inbox, ArrowUpRight } from 'lucide-react';

type StatusFilter = 'all' | 'upcoming' | 'due_soon' | 'overdue' | 'done';

export default function DashboardPage() {
  const { user, loading: authLoading } = useAuth();
  const [events, setEvents] = useState<Event[]>([]);
  const [loading, setLoading] = useState(true);
  const [error, setError] = useState<string | null>(null);
  const [mutationError, setMutationError] = useState<string | null>(null);
  const [filter, setFilter] = useState<StatusFilter>('all');

  const fetchEvents = useCallback(async () => {
    if (!user) return;
    setLoading(true);
    setError(null);
    try { setEvents(await eventApi.list()); }
    catch { setError('Unable to load deadlines. Please try again.'); }
    finally { setLoading(false); }
  }, [user]);

  useEffect(() => { if (!authLoading && user) fetchEvents(); }, [authLoading, user, fetchEvents]);

  const runMutation = async (action: () => Promise<void>, fallback: string) => {
    setMutationError(null);
    try { await action(); await fetchEvents(); }
    catch (err: unknown) { setMutationError(err instanceof Error ? err.message : fallback); }
  };

  const handleMarkDone = (id: string) => runMutation(() => eventApi.markDone(id), 'Failed to mark deadline as done');
  const handleSnooze = (id: string) => runMutation(() => eventApi.snooze(id, '1d'), 'Failed to snooze deadline');
  const handleDelete = async (id: string) => {
    if (!confirm('Delete this event?')) return;
    await runMutation(() => eventApi.delete(id), 'Failed to delete deadline');
  };

  if (authLoading) return <div className="animate-pulse space-y-6"><div className="h-32 bg-surface-hover rounded-2xl border border-border-subtle" /><div className="h-8 w-64 bg-surface-hover rounded-full" /><div className="grid gap-3"><div className="h-28 bg-surface-hover rounded-xl border border-border-subtle" /><div className="h-28 bg-surface-hover rounded-xl border border-border-subtle" /></div></div>;

  const counts = {
    upcoming: events.filter((e) => e.status === 'upcoming').length,
    due_soon: events.filter((e) => e.status === 'due_soon').length,
    overdue: events.filter((e) => e.status === 'overdue').length,
    done: events.filter((e) => e.status === 'done').length,
  };
  const visibleEvents = filter === 'all' ? events : events.filter((event) => event.status === filter);

  return (
    <div className="space-y-7">
      <section className="relative overflow-hidden rounded-2xl border border-border-subtle bg-surface shadow-soft p-6 sm:p-8">
        <div className="absolute inset-x-0 top-0 h-px bg-gradient-to-r from-transparent via-brand/50 to-transparent" />
        <div className="relative flex flex-col lg:flex-row lg:items-center justify-between gap-7">
          <div className="max-w-xl">
            <div className="inline-flex items-center gap-2 rounded-full border border-brand/20 bg-brand/5 px-3 py-1 text-xs font-semibold text-brand mb-4">
              <span className="w-1.5 h-1.5 rounded-full bg-brand" /> Your workspace
            </div>
            <h1 className="text-3xl sm:text-4xl font-bold text-text-primary tracking-[-0.03em]">
              Good {new Date().getHours() < 12 ? 'morning' : new Date().getHours() < 18 ? 'afternoon' : 'evening'}, {user?.email?.split('@')[0] || 'User'}.
            </h1>
            <p className="text-text-secondary mt-2 text-sm sm:text-base">
              {counts.overdue + counts.due_soon > 0 ? <><span className="font-semibold text-text-primary">{counts.overdue + counts.due_soon} deadlines</span> need your attention.</> : "You're all caught up. Keep the momentum going."}
            </p>
          </div>
          <Link href="/dashboard/events/new" className="group inline-flex items-center justify-center gap-2 rounded-xl bg-brand px-5 py-3 text-sm font-semibold text-white shadow-sm hover:bg-brand-hover hover:-translate-y-0.5 transition-all duration-200">
            Add deadline <ArrowUpRight className="w-4 h-4 transition-transform group-hover:translate-x-0.5 group-hover:-translate-y-0.5" />
          </Link>
        </div>

        <div className="grid grid-cols-4 mt-7 pt-5 border-t border-border-subtle">
          {[
            { key: 'upcoming', icon: Calendar, value: counts.upcoming, label: 'Upcoming', color: 'text-brand' },
            { key: 'due_soon', icon: Clock3, value: counts.due_soon, label: 'Due soon', color: 'text-warning' },
            { key: 'overdue', icon: AlertCircle, value: counts.overdue, label: 'Overdue', color: 'text-danger' },
            { key: 'done', icon: CheckCircle2, value: counts.done, label: 'Done', color: 'text-success' },
          ].map((stat) => (
            <button key={stat.key} onClick={() => setFilter(filter === stat.key ? 'all' : stat.key as StatusFilter)} className={cn('text-left px-3 py-1 first:pl-0 last:pr-0 group', filter === stat.key && 'text-text-primary')} aria-pressed={filter === stat.key}>
              <div className="flex items-center gap-2"><stat.icon className={cn('w-4 h-4', stat.color)} /><span className="text-lg font-bold text-text-primary">{stat.value}</span></div>
              <span className="text-[11px] font-medium text-text-muted group-hover:text-text-secondary transition-colors">{stat.label}</span>
            </button>
          ))}
        </div>
      </section>

      <div className="flex gap-2 overflow-x-auto pb-1 scrollbar-hide" role="group" aria-label="Event filters">
        {(['all', 'upcoming', 'due_soon', 'overdue', 'done'] as StatusFilter[]).map((s) => (
          <button key={s} type="button" onClick={() => setFilter(s)} aria-pressed={filter === s} className={cn('text-xs sm:text-sm px-4 py-2 rounded-lg whitespace-nowrap transition-all duration-200 border font-medium', filter === s ? 'bg-text-primary text-surface border-text-primary' : 'bg-surface text-text-secondary border-border-subtle hover:border-border-strong hover:text-text-primary')}>
            {s === 'all' ? 'All deadlines' : s.replace('_', ' ').replace(/\b\w/g, l => l.toUpperCase())}
          </button>
        ))}
      </div>

      <div className="min-h-[400px]">
        {mutationError && <div role="alert" className="mb-4 p-3 bg-danger/10 border border-danger/20 rounded-xl text-danger text-sm flex items-center justify-between"><div className="flex items-center gap-2"><AlertCircle className="w-4 h-4" /><span>{mutationError}</span></div><button onClick={() => setMutationError(null)} aria-label="Dismiss error" className="p-1 opacity-70 hover:opacity-100">×</button></div>}
        {error ? <div className="flex flex-col items-center justify-center py-20 px-4 text-center bg-surface border border-border-subtle rounded-xl"><AlertCircle className="w-10 h-10 text-danger mb-4 opacity-80" /><h3 className="text-lg font-medium text-text-primary mb-1">Failed to load</h3><p className="text-text-secondary mb-4">{error}</p><button onClick={fetchEvents} className="px-4 py-2 bg-surface hover:bg-surface-hover border border-border-strong text-text-primary rounded-lg text-sm transition-colors">Try again</button></div>
        : loading ? <div className="grid gap-3">{[1,2,3].map(i => <div key={i} className="h-28 bg-surface-hover rounded-xl border border-border-subtle animate-pulse" />)}</div>
        : visibleEvents.length === 0 ? <div className="flex flex-col items-center justify-center py-24 px-4 text-center bg-surface border border-border-subtle rounded-2xl shadow-sm"><div className="w-14 h-14 bg-brand/8 text-brand rounded-2xl flex items-center justify-center mb-4"><Inbox className="w-7 h-7" /></div><h3 className="text-lg font-semibold text-text-primary mb-1">{filter === 'all' ? 'No upcoming deadlines' : `No ${filter.replace('_', ' ')} deadlines`}</h3><p className="text-text-secondary text-sm max-w-sm mb-6">{filter === 'all' ? "You're completely caught up. Add a deadline when something needs your attention." : 'Try another filter to see your deadlines.'}</p>{filter === 'all' && <Link href="/dashboard/events/new" className="bg-brand text-white px-5 py-2.5 rounded-xl hover:bg-brand-hover transition-colors text-sm font-semibold">Add deadline</Link>}</div>
        : <div className="grid gap-3">{visibleEvents.map(event => <EventCard key={event.id} event={event} onMarkDone={handleMarkDone} onSnooze={handleSnooze} onDelete={handleDelete} />)}</div>}
      </div>
    </div>
  );
}
