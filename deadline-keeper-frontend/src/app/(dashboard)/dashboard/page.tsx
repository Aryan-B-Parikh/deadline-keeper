'use client';

import { useState, useEffect, useCallback } from 'react';
import { eventApi, type Event } from '@/lib/api';
import { EventCard } from '@/components/EventCard';
import { useAuth } from '@/lib/auth';
import Link from 'next/link';
import { cn } from '@/lib/utils';
import { Calendar, AlertCircle, Clock3, CheckCircle2, Inbox } from 'lucide-react';

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
    try {
      const data = await eventApi.list();
      setEvents(data);
    } catch (err: unknown) {
      setError('Unable to load deadlines. Please try again.');
    } finally {
      setLoading(false);
    }
  }, [user]);

  useEffect(() => {
    if (!authLoading && user) fetchEvents();
  }, [authLoading, user, fetchEvents]);

  const handleMarkDone = async (id: string) => {
    setMutationError(null);
    try {
      await eventApi.markDone(id);
      await fetchEvents();
    } catch (err: unknown) {
      setMutationError(err instanceof Error ? err.message : 'Failed to mark deadline as done');
    }
  };

  const handleSnooze = async (id: string) => {
    setMutationError(null);
    try {
      await eventApi.snooze(id, '1d');
      await fetchEvents();
    } catch (err: unknown) {
      setMutationError(err instanceof Error ? err.message : 'Failed to snooze deadline');
    }
  };

  const handleDelete = async (id: string) => {
    if (!confirm('Delete this event?')) return;
    setMutationError(null);
    try {
      await eventApi.delete(id);
      await fetchEvents();
    } catch (err: unknown) {
      setMutationError(err instanceof Error ? err.message : 'Failed to delete deadline');
    }
  };

  if (authLoading) {
    return (
      <div className="animate-pulse space-y-6">
        <div className="h-32 bg-surface-hover rounded-2xl w-full border border-border-subtle" />
        <div className="flex gap-2"><div className="h-8 w-16 bg-surface-hover rounded-full" /><div className="h-8 w-24 bg-surface-hover rounded-full" /></div>
        <div className="grid gap-3"><div className="h-28 bg-surface-hover rounded-xl border border-border-subtle" /><div className="h-28 bg-surface-hover rounded-xl border border-border-subtle" /></div>
      </div>
    );
  }

  const counts = {
    upcoming: events.filter((e) => e.status === 'upcoming').length,
    due_soon: events.filter((e) => e.status === 'due_soon').length,
    overdue: events.filter((e) => e.status === 'overdue').length,
    done: events.filter((e) => e.status === 'done').length,
  };

  const visibleEvents = filter === 'all'
    ? events
    : events.filter((event) => event.status === filter);

  return (
    <div className="space-y-6">
      {/* Compact Hero Section */}
      <section className="relative overflow-hidden bg-glass rounded-2xl border border-border-subtle p-6 sm:p-8 shadow-sm">
        
        <div className="relative z-10 flex flex-col sm:flex-row sm:items-center justify-between gap-6">
          <div>
            <h1 className="text-2xl sm:text-3xl font-bold text-text-primary tracking-tight">
              Good {new Date().getHours() < 12 ? 'morning' : new Date().getHours() < 18 ? 'afternoon' : 'evening'}, {user?.email?.split('@')[0] || 'User'}
            </h1>
            <p className="text-text-secondary mt-1">
              {counts.overdue > 0 || counts.due_soon > 0 
                ? <span className="font-medium text-warning">You have {counts.overdue + counts.due_soon} deadlines needing attention.</span>
                : "You're all caught up for now."}
            </p>
          </div>
          
          <div className="flex items-center gap-3 bg-surface border border-border-subtle rounded-xl p-2 shadow-sm shrink-0">
            {[
              { key: 'upcoming', icon: Calendar, value: counts.upcoming, color: 'text-brand', bg: 'bg-brand/10' },
              { key: 'due_soon', icon: Clock3, value: counts.due_soon, color: 'text-warning', bg: 'bg-warning/10' },
              { key: 'overdue', icon: AlertCircle, value: counts.overdue, color: 'text-danger', bg: 'bg-danger/10' },
              { key: 'done', icon: CheckCircle2, value: counts.done, color: 'text-success', bg: 'bg-success/10' }
            ].map((stat) => (
              <button
                key={stat.key}
                onClick={() => setFilter(filter === stat.key ? 'all' : stat.key as StatusFilter)}
                className={cn(
                  "flex flex-col items-center justify-center w-12 h-12 rounded-lg transition-all",
                  filter === stat.key ? stat.bg : "hover:bg-surface-hover",
                  filter === stat.key ? stat.color : "text-text-secondary"
                )}
                title={stat.key.replace('_', ' ')}
              >
                <stat.icon className="w-5 h-5 mb-0.5" />
                <span className="text-xs font-semibold leading-none">{stat.value}</span>
              </button>
            ))}
          </div>
        </div>
      </section>

      {/* Filter Pills */}
      <div className="flex gap-2 overflow-x-auto pb-2 -mx-4 px-4 sm:mx-0 sm:px-0 scrollbar-hide" role="group" aria-label="Event filters">
        {(['all', 'upcoming', 'due_soon', 'overdue', 'done'] as StatusFilter[]).map((s) => (
          <button
            key={s}
            type="button"
            onClick={() => setFilter(s)}
            aria-pressed={filter === s}
            className={cn(
              "text-sm px-4 py-1.5 rounded-full whitespace-nowrap transition-all duration-200 border",
              filter === s
                ? "bg-text-primary text-surface border-text-primary shadow-sm"
                : "bg-surface text-text-secondary border-border-subtle hover:border-border-strong hover:text-text-primary"
            )}
          >
            {s === 'all' ? 'All Deadlines' : s.replace('_', ' ').replace(/\b\w/g, l => l.toUpperCase())}
          </button>
        ))}
      </div>

      {/* Content Area */}
      <div className="min-h-[400px]">
        {mutationError && (
          <div className="mb-4 p-3 bg-danger/10 border border-danger/20 rounded-xl text-danger text-sm flex items-center justify-between">
            <div className="flex items-center gap-2">
              <AlertCircle className="w-4 h-4" />
              <span>{mutationError}</span>
            </div>
            <button onClick={() => setMutationError(null)} className="opacity-70 hover:opacity-100">
              ✕
            </button>
          </div>
        )}
        {error ? (
          <div className="flex flex-col items-center justify-center py-20 px-4 text-center bg-surface border border-border-subtle rounded-xl">
            <AlertCircle className="w-10 h-10 text-danger mb-4 opacity-80" />
            <h3 className="text-lg font-medium text-text-primary mb-1">Failed to load</h3>
            <p className="text-text-secondary mb-4">{error}</p>
            <button 
              onClick={fetchEvents}
              className="px-4 py-2 bg-surface hover:bg-surface-hover border border-border-strong text-text-primary rounded-lg text-sm transition-colors"
            >
              Try again
            </button>
          </div>
        ) : loading ? (
          <div className="grid gap-3">
            <div className="h-28 bg-surface-hover rounded-xl border border-border-subtle animate-pulse" />
            <div className="h-28 bg-surface-hover rounded-xl border border-border-subtle animate-pulse" />
            <div className="h-28 bg-surface-hover rounded-xl border border-border-subtle animate-pulse" />
          </div>
        ) : visibleEvents.length === 0 ? (
          <div className="flex flex-col items-center justify-center py-24 px-4 text-center bg-surface-glass border border-border-subtle rounded-2xl shadow-sm">
            <div className="w-16 h-16 bg-brand/5 text-brand rounded-2xl flex items-center justify-center mb-4">
              <Inbox className="w-8 h-8" />
            </div>
            <h3 className="text-lg font-medium text-text-primary mb-1">
              {filter === 'all' ? 'No upcoming deadlines' : `No ${filter.replace('_', ' ')} deadlines`}
            </h3>
            <p className="text-text-secondary text-sm max-w-sm mb-6">
              {filter === 'all' 
                ? "You're completely caught up. Enjoy the peace of mind, or add a new deadline to track."
                : "Try changing your filter to see other deadlines."}
            </p>
            {filter === 'all' && (
              <Link
                href="/dashboard/events/new"
                className="bg-brand text-white px-5 py-2.5 rounded-xl hover:bg-brand-hover transition-colors text-sm font-medium shadow-sm"
              >
                Add Deadline
              </Link>
            )}
          </div>
        ) : (
          <div className="grid gap-3">
            {visibleEvents.map((event) => (
              <EventCard
                key={event.id}
                event={event}
                onMarkDone={handleMarkDone}
                onSnooze={handleSnooze}
                onDelete={handleDelete}
              />
            ))}
          </div>
        )}
      </div>
    </div>
  );
}
