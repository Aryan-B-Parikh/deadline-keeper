'use client';

import Link from 'next/link';
import { useAuth } from '@/lib/auth';
import { useRouter } from 'next/navigation';
import { Bell, Calendar, Inbox, Settings, LogOut, Plus } from 'lucide-react';

export default function DashboardLayout({ children }: { children: React.ReactNode }) {
  const { user, signOut } = useAuth();
  const router = useRouter();

  const handleSignOut = async () => {
    await signOut();
    router.push('/login');
  };

  const navItems = [
    { href: '/dashboard', label: 'Dashboard', icon: '📊' },
    { href: '/dashboard/events/new', label: 'Add Event', icon: '➕' },
    { href: '/dashboard/calendar', label: 'Calendar', icon: Calendar },
    { href: '/dashboard/inbox', label: 'Inbox', icon: Inbox },
    { href: '/dashboard/settings', label: 'Settings', icon: Settings },
  ];

  return (
    <div className="min-h-screen bg-gray-50">
      {/* Top bar */}
      <header className="bg-white border-b border-gray-200 sticky top-0 z-30">
        <div className="max-w-7xl mx-auto px-4 sm:px-6 lg:px-8 flex items-center justify-between h-16">
          <Link href="/dashboard" className="flex items-center gap-2">
            <span className="text-xl">⏰</span>
            <span className="font-bold text-lg text-brand-600">DeadlineKeeper</span>
          </Link>

          <div className="flex items-center gap-4">
            <Link href="/dashboard/notifications" className="relative text-gray-500 hover:text-gray-700">
              <Bell className="w-5 h-5" />
            </Link>

            <div className="flex items-center gap-3">
              <div className="w-8 h-8 bg-brand-100 text-brand-700 rounded-full flex items-center justify-center text-sm font-medium">
                {user?.email?.charAt(0).toUpperCase() || '?'}
              </div>
              <button
                onClick={handleSignOut}
                className="text-gray-400 hover:text-gray-600"
                title="Sign out"
              >
                <LogOut className="w-4 h-4" />
              </button>
            </div>
          </div>
        </div>
      </header>

      <div className="max-w-7xl mx-auto px-4 sm:px-6 lg:px-8 py-6">
        {/* Quick nav */}
        <nav className="flex gap-1 mb-6 bg-white rounded-lg border border-gray-200 p-1">
          {navItems.map((item) => (
            <Link
              key={item.href}
              href={item.href}
              className="flex items-center gap-2 px-3 py-2 rounded-md text-sm font-medium text-gray-600 hover:bg-gray-50 hover:text-gray-900 transition-colors"
            >
              {typeof item.icon === 'string' ? (
                <span>{item.icon}</span>
              ) : (
                <item.icon className="w-4 h-4" />
              )}
              {item.label}
            </Link>
          ))}
        </nav>

        {children}
      </div>
    </div>
  );
}
