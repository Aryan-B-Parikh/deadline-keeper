'use client';

import { useState, useEffect, useCallback } from 'react';
import { eventApi, type Event } from '@/lib/api';
import { EventCard } from '@/components/EventCard';
import { useAuth } from '@/lib/auth';
import Link from 'next/link';
import { cn } from '@/lib/utils';
import { Calendar, AlertCircle, Clock3, CheckCircle2, Inbox, ArrowUpRight, Plus, Sparkles } from 'lucide-react';

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
    setLoading(true); setError(null);
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

  const handleMarkDone = (id: string) => runMutation(async () => { await eventApi.markDone(id); }, 'Failed to mark deadline as done');
  const handleSnooze = (id: string) => runMutation(async () => { await eventApi.snooze(id, '1d'); }, 'Failed to snooze deadline');
  const handleDelete = async (id: string) => { if (confirm('Delete this event?')) await runMutation(async () => { await eventApi.delete(id); }, 'Failed to delete deadline'); };

  if (authLoading) return <div className="space-y-6 animate-pulse"><div className="h-56 bg-surface rounded-[28px] border border-border-subtle" /><div className="h-10 w-72 bg-surface-hover rounded-full" /><div className="h-28 bg-surface rounded-2xl border border-border-subtle" /></div>;

  const counts = {
    upcoming: events.filter((e) => e.status === 'upcoming').length,
    due_soon: events.filter((e) => e.status === 'due_soon').length,
    overdue: events.filter((e) => e.status === 'overdue').length,
    done: events.filter((e) => e.status === 'done').length,
  };
  const visibleEvents = filter === 'all' ? events : events.filter((event) => event.status === filter);
  const attention = counts.overdue + counts.due_soon;
  const firstName = user?.email?.split('@')[0] || 'there';

  return (
    <div className="space-y-8">
      <section className="relative overflow-hidden rounded-[30px] border border-border-subtle bg-surface shadow-soft">
        <div className="absolute inset-0 bg-[radial-gradient(circle_at_82%_18%,rgba(109,70,193,0.12),transparent_28%)] pointer-events-none" />
        <div className="absolute right-[-8%] top-[-45%] h-80 w-80 rounded-full border border-brand/10 opacity-50" aria-hidden="true" />
        <div className="relative p-6 sm:p-8 lg:p-10">
          <div className="flex flex-col lg:flex-row lg:items-end justify-between gap-8">
            <div className="max-w-2xl">
              <div className="inline-flex items-center gap-2 rounded-full border border-border-subtle bg-surface-hover/80 px-3 py-1.5 text-[11px] font-bold uppercase tracking-[0.14em] text-text-muted mb-5">
                <Sparkles className="w-3.5 h-3.5 text-brand" /> Focus mode
              </div>
              <h1 className="text-[clamp(2.25rem,5vw,4.25rem)] leading-[0.98] font-bold tracking-[-0.055em] text-text-primary text-balance">
                Make room for what matters.
              </h1>
              <p className="mt-4 max-w-xl text-sm sm:text-base leading-7 text-text-secondary">
                Welcome back, <span className="font-semibold text-text-primary">{firstName}</span>. {attention > 0 ? <><span className="font-semibold text-text-primary">{attention} deadlines</span> are asking for attention.</> : "Your queue is clear. Keep the momentum."}
              </p>
            </div>
            <Link href="/dashboard/events/new" className="group inline-flex min-h-[48px] shrink-0 items-center justify-center gap-2 rounded-2xl bg-text-primary px-5 text-sm font-bold text-white shadow-float transition-all duration-200 hover:-translate-y-0.5 hover:opacity-90">
              <Plus className="w-4 h-4" /> Add deadline <ArrowUpRight className="w-4 h-4 transition-transform group-hover:translate-x-0.5 group-hover:-translate-y-0.5" />
            </Link>
          </div>

          <div className="mt-9 grid grid-cols-2 sm:grid-cols-4 gap-2 sm:gap-3">
            {[
              { key: 'upcoming', icon: Calendar, value: counts.upcoming, label: 'Upcoming', color: 'text-brand' },
              { key: 'due_soon', icon: Clock3, value: counts.due_soon, label: 'Due soon', color: 'text-warning' },
              { key: 'overdue', icon: AlertCircle, value: counts.overdue, label: 'Overdue', color: 'text-danger' },
              { key: 'done', icon: CheckCircle2, value: counts.done, label: 'Completed', color: 'text-success' },
            ].map((stat) => (
              <button key={stat.key} onClick={() => setFilter(filter === stat.key ? 'all' : stat.key as StatusFilter)} className={cn('group rounded-2xl border px-4 py-3 text-left transition-all duration-200 hover:-translate-y-0.5', filter === stat.key ? 'border-text-primary bg-text-primary text-white' : 'border-border-subtle bg-surface-hover/65 hover:bg-surface') } aria-pressed={filter === stat.key}>
                <div className="flex items-center justify-between"><stat.icon className={cn('w-4 h-4', filter === stat.key ? 'text-white' : stat.color)} /><span className={cn('text-2xl font-bold tracking-[-0.04em]', filter === stat.key ? 'text-white' : 'text-text-primary')}>{stat.value}</span></div>
                <span className={cn('mt-1 block text-[11px] font-semibold uppercase tracking-[0.08em]', filter === stat.key ? 'text-white/65' : 'text-text-muted')}>{stat.label}</span>
              </button>
            ))}
          </div>
        </div>
      </section>

      <div className="flex items-center justify-between gap-4">
        <div><p className="text-[11px] font-bold uppercase tracking-[0.16em] text-text-muted">Your queue</p><h2 className="mt-1 text-xl font-bold tracking-[-0.025em] text-text-primary">Deadlines</h2></div>
        <div className="flex gap-2 overflow-x-auto scrollbar-hide" role="group" aria-label="Event filters">
          {(['all', 'upcoming', 'due_soon', 'overdue', 'done'] as StatusFilter[]).map((s) => <button key={s} type="button" onClick={() => setFilter(s)} aria-pressed={filter === s} className={cn('min-h-[40px] text-xs px-3.5 rounded-xl whitespace-nowrap border font-semibold transition-all duration-200', filter === s ? 'bg-text-primary text-white border-text-primary shadow-sm' : 'bg-surface text-text-secondary border-border-subtle hover:border-border-strong hover:text-text-primary')}>{s === 'all' ? 'All' : s.replace('_', ' ').replace(/\b\w/g, l => l.toUpperCase())}</button>)}
        </div>
      </div>

      <div className="min-h-[400px]">
        {mutationError && <div role="alert" className="mb-4 p-3.5 bg-danger/10 border border-danger/20 rounded-2xl text-danger text-sm flex items-center justify-between"><div className="flex items-center gap-2"><AlertCircle className="w-4 h-4" /><span>{mutationError}</span></div><button onClick={() => setMutationError(null)} aria-label="Dismiss error" className="p-1 opacity-70 hover:opacity-100">×</button></div>}
        {error ? <div className="flex flex-col items-center justify-center py-20 px-4 text-center bg-surface border border-border-subtle rounded-[24px]"><AlertCircle className="w-10 h-10 text-danger mb-4 opacity-80" /><h3 className="text-lg font-semibold text-text-primary mb-1">Failed to load</h3><p className="text-text-secondary mb-4">{error}</p><button onClick={fetchEvents} className="min-h-[44px] px-4 bg-text-primary text-white rounded-xl text-sm font-semibold">Try again</button></div>
        : loading ? <div className="grid gap-3">{[1,2,3].map(i => <div key={i} className="h-32 bg-surface rounded-2xl border border-border-subtle animate-pulse" />)}</div>
        : visibleEvents.length === 0 ? <div className="flex flex-col items-center justify-center py-24 px-4 text-center bg-surface border border-border-subtle rounded-[24px] shadow-sm"><div className="w-14 h-14 bg-brand/8 text-brand rounded-2xl flex items-center justify-center mb-4"><Inbox className="w-7 h-7" /></div><h3 className="text-lg font-semibold text-text-primary mb-1">{filter === 'all' ? 'No deadlines here' : `No ${filter.replace('_', ' ')} deadlines`}</h3><p className="text-text-secondary text-sm max-w-sm mb-6">{filter === 'all' ? "You're completely caught up. Add a deadline when something needs your attention." : 'Try another filter to see your deadlines.'}</p>{filter === 'all' && <Link href="/dashboard/events/new" className="bg-text-primary text-white px-5 min-h-[44px] flex items-center rounded-xl hover:opacity-90 transition-colors text-sm font-semibold">Add deadline</Link>}</div>
        : <div className="grid gap-3">{visibleEvents.map(event => <EventCard key={event.id} event={event} onMarkDone={handleMarkDone} onSnooze={handleSnooze} onDelete={handleDelete} />)}</div>}
      </div>
    </div>
  );
}
