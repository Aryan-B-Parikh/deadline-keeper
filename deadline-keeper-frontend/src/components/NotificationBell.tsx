'use client';

import { useState, useEffect } from 'react';
import { notificationApi, type Notification } from '@/lib/api';
import { Bell } from 'lucide-react';

export function NotificationBell() {
  const [count, setCount] = useState(0);

  useEffect(() => {
    const fetchCount = async () => {
      try {
        const result = await notificationApi.unreadCount();
        setCount(result.count);
      } catch {}
    };
    fetchCount();
    const interval = setInterval(fetchCount, 60000);
    return () => clearInterval(interval);
  }, []);

  return (
    <div className="relative">
      <Bell className="w-5 h-5 text-gray-500" />
      {count > 0 && (
        <span className="absolute -top-1 -right-1 bg-red-500 text-white text-[10px] font-bold rounded-full w-4 h-4 flex items-center justify-center">
          {count > 9 ? '9+' : count}
        </span>
      )}
    </div>
  );
}
