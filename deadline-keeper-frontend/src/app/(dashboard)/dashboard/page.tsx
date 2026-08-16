'use client';

import { useState, useEffect, useCallback } from 'react';
import { eventApi, type Event } from '@/lib/api';
import { EventCard } from '@/components/EventCard';
import { useAuth } from '@/lib/auth';
import Link from 'next/link';

type StatusFilter = 'all' | 'upcoming' | 'due_soon' | 'overdue' | 'done';

export default function DashboardPage() {
  const { user, loading: authLoading } = useAuth();
  const [events, setEvents] = useState<Event[]>([]);
  const [loading, setLoading] = useState(true);
  const [filter, setFilter] = useState<StatusFilter>('all');

  const fetchEvents = useCallback(async () => {
    if (!user) return;
    try {
      const data = await eventApi.list(filter !== 'all' ? filter : undefined);
      setEvents(data);
    } catch (err) {
      console.error('Failed to fetch events:', err);
    } finally {
      setLoading(false);
    }
  }, [user, filter]);

  useEffect(() => {
    if (!authLoading && user) fetchEvents();
  }, [authLoading, user, fetchEvents]);

  const handleMarkDone = async (id: string) => {
    try {
      await eventApi.markDone(id);
      fetchEvents();
    } catch (err) {
      console.error('Failed to mark done:', err);
    }
  };

  const handleSnooze = async (id: string) => {
    try {
      await eventApi.snooze(id, '1d');
      fetchEvents();
    } catch (err) {
      console.error('Failed to snooze:', err);
    }
  };

  const handleDelete = async (id: string) => {
    if (!confirm('Delete this event?')) return;
    try {
      await eventApi.delete(id);
      fetchEvents();
    } catch (err) {
      console.error('Failed to delete:', err);
    }
  };

  if (authLoading) {
    return <div className="text-center text-gray-400 py-12">Loading...</div>;
  }

  const counts = {
    upcoming: events.filter((e) => e.status === 'upcoming').length,
    due_soon: events.filter((e) => e.status === 'due_soon').length,
    overdue: events.filter((e) => e.status === 'overdue').length,
    done: events.filter((e) => e.status === 'done').length,
  };

  return (
    <div>
      <div className="flex items-center justify-between mb-6">
        <div>
          <h1 className="text-2xl font-bold text-gray-900">Dashboard</h1>
          <p className="text-gray-500 text-sm mt-1">Your deadlines at a glance</p>
        </div>
        <Link
          href="/dashboard/events/new"
          className="bg-brand-600 text-white px-4 py-2 rounded-lg hover:bg-brand-700 transition-colors text-sm font-medium"
        >
          + Add Event
        </Link>
      </div>

      {/* Status summary cards */}
      <div className="grid grid-cols-2 md:grid-cols-4 gap-3 mb-6">
        {[
          { key: 'upcoming', label: 'Upcoming', color: 'bg-blue-50 text-blue-700 border-blue-200', icon: '📅' },
          { key: 'due_soon', label: 'Due Soon', color: 'bg-amber-50 text-amber-700 border-amber-200', icon: '⚠️' },
          { key: 'overdue', label: 'Overdue', color: 'bg-red-50 text-red-700 border-red-200', icon: '🚨' },
          { key: 'done', label: 'Done', color: 'bg-green-50 text-green-700 border-green-200', icon: '✅' },
        ].map(({ key, label, color, icon }) => (
          <button
            key={key}
            onClick={() => setFilter(filter === key ? 'all' : (key as StatusFilter))}
            className={`${color} border rounded-lg p-4 text-left transition-all ${
              filter === key ? 'ring-2 ring-offset-1 ring-brand-400' : ''
            }`}
          >
            <div className="flex items-center gap-2">
              <span>{icon}</span>
              <span className="text-sm font-medium">{label}</span>
            </div>
            <div className="text-2xl font-bold mt-1">
              {counts[key as keyof typeof counts] ?? 0}
            </div>
          </button>
        ))}
      </div>

      {/* Filter pills */}
      <div className="flex gap-2 mb-4">
        {(['all', 'upcoming', 'due_soon', 'overdue', 'done'] as StatusFilter[]).map((s) => (
          <button
            key={s}
            onClick={() => setFilter(s)}
            className={`text-xs px-3 py-1 rounded-full transition-colors ${
              filter === s
                ? 'bg-brand-600 text-white'
                : 'bg-gray-100 text-gray-600 hover:bg-gray-200'
            }`}
          >
            {s === 'all' ? 'All' : s.replace('_', ' ')}
          </button>
        ))}
      </div>

      {/* Event list */}
      {loading ? (
        <div className="text-center text-gray-400 py-12">Loading events...</div>
      ) : events.length === 0 ? (
        <div className="text-center py-12">
          <div className="text-4xl mb-3">📭</div>
          <p className="text-gray-500">
            {filter === 'all' ? 'No events yet. Add your first deadline!' : `No ${filter.replace('_', ' ')} events`}
          </p>
          {filter === 'all' && (
            <Link
              href="/dashboard/events/new"
              className="inline-block mt-4 text-brand-600 hover:text-brand-700 font-medium text-sm"
            >
              + Add Event
            </Link>
          )}
        </div>
      ) : (
        <div className="grid gap-3">
          {events.map((event) => (
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
  );
}
