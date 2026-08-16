'use client';

import { useState, useEffect } from 'react';
import { notificationApi, type Notification } from '@/lib/api';
import { useAuth } from '@/lib/auth';

export default function NotificationsPage() {
  const { user } = useAuth();
  const [notifications, setNotifications] = useState<Notification[]>([]);
  const [loading, setLoading] = useState(true);

  useEffect(() => {
    if (!user) return;
    notificationApi.list().then(setNotifications).finally(() => setLoading(false));
  }, [user]);

  const markRead = async (id: string) => {
    await notificationApi.markRead(id);
    setNotifications((prev) =>
      prev.map((n) => (n.id === id ? { ...n, isRead: true } : n))
    );
  };

  if (loading) return <div className="text-center text-gray-400 py-12">Loading...</div>;

  return (
    <div className="max-w-2xl mx-auto">
      <h1 className="text-2xl font-bold text-gray-900 mb-6">Notifications</h1>

      {notifications.length === 0 ? (
        <div className="text-center py-12">
          <div className="text-4xl mb-3">🔔</div>
          <p className="text-gray-500">No notifications yet</p>
        </div>
      ) : (
        <div className="space-y-2">
          {notifications.map((n) => (
            <div
              key={n.id}
              className={`p-4 rounded-lg border transition-colors ${
                n.isRead
                  ? 'bg-white border-gray-200'
                  : 'bg-brand-50 border-brand-200'
              }`}
            >
              <div className="flex items-start justify-between gap-3">
                <div>
                  <h3 className="font-medium text-gray-900 text-sm">{n.title}</h3>
                  <p className="text-gray-600 text-sm mt-1">{n.message}</p>
                  <p className="text-gray-400 text-xs mt-2">
                    {new Date(n.createdAt).toLocaleString()}
                  </p>
                </div>
                {!n.isRead && (
                  <button
                    onClick={() => markRead(n.id)}
                    className="text-xs text-brand-600 hover:text-brand-700 flex-shrink-0"
                  >
                    Mark read
                  </button>
                )}
              </div>
            </div>
          ))}
        </div>
      )}
    </div>
  );
}
