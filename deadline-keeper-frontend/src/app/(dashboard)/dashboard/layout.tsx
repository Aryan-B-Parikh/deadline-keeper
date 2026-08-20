'use client';

import Link from 'next/link';
import { useAuth } from '@/lib/auth';
import { usePathname, useRouter } from 'next/navigation';
import { Calendar, Inbox, Settings, LogOut, LayoutDashboard, Plus } from 'lucide-react';
import { cn } from '@/lib/utils';
import { useState, useEffect } from 'react';

export default function DashboardLayout({ children }: { children: React.ReactNode }) {
  const { user, signOut } = useAuth();
  const router = useRouter();
  const pathname = usePathname();
  const [scrolled, setScrolled] = useState(false);
  useEffect(() => { const onScroll = () => setScrolled(window.scrollY > 10); window.addEventListener('scroll', onScroll); return () => window.removeEventListener('scroll', onScroll); }, []);
  const handleSignOut = async () => { await signOut(); router.push('/login'); };
  const navItems = [
    { href: '/dashboard', label: 'Dashboard', icon: LayoutDashboard },
    { href: '/dashboard/calendar', label: 'Calendar', icon: Calendar },
    { href: '/dashboard/inbox', label: 'Inbox', icon: Inbox },
    { href: '/dashboard/settings', label: 'Settings', icon: Settings },
  ];

  return <div className="min-h-screen bg-background flex flex-col md:flex-row">
    <aside className="hidden md:flex flex-col w-64 fixed inset-y-0 z-40 bg-glass-strong border-r border-border-subtle">
      <div className="p-6"><Link href="/dashboard" className="flex items-center gap-3 group"><span className="flex h-9 w-9 items-center justify-center rounded-xl bg-text-primary text-white text-base">⏰</span><span className="font-bold text-lg text-text-primary tracking-tight">DeadlineKeeper</span></Link></div>
      <div className="px-4 pb-5"><Link href="/dashboard/events/new" className="w-full flex items-center justify-center gap-2 bg-brand text-white min-h-[44px] px-4 rounded-xl hover:bg-brand-hover hover:-translate-y-0.5 shadow-sm transition-all duration-200 font-semibold text-sm"><Plus className="w-5 h-5" /> Add deadline</Link></div>
      <nav className="flex-1 px-4 space-y-1 overflow-y-auto">{navItems.map(item => { const active = pathname === item.href; return <Link key={item.href} href={item.href} className={cn('flex items-center gap-3 px-3 min-h-[44px] rounded-xl text-sm font-medium transition-all duration-200', active ? 'bg-text-primary text-white shadow-sm' : 'text-text-secondary hover:bg-surface-hover hover:text-text-primary')}><item.icon className={cn('w-5 h-5', active ? 'text-white' : 'text-text-muted')} />{item.label}</Link>; })}</nav>
      <div className="p-4 border-t border-border-subtle"><div className="flex items-center justify-between px-2"><div className="flex items-center gap-3 truncate"><div className="w-9 h-9 bg-brand/10 text-brand border border-brand/15 rounded-full flex items-center justify-center text-sm font-bold flex-shrink-0">{user?.email?.charAt(0).toUpperCase() || '?'}</div><div className="truncate"><p className="text-sm font-semibold text-text-primary truncate">{user?.email?.split('@')[0] || 'User'}</p><p className="text-xs text-text-muted truncate">Free Plan</p></div></div><button onClick={handleSignOut} className="text-text-muted hover:text-danger transition-colors p-2 rounded-lg hover:bg-surface-hover" aria-label="Sign out"><LogOut className="w-4 h-4" /></button></div></div>
    </aside>

    <div className="flex-1 md:pl-64 flex flex-col min-h-screen">
      <header className={cn('md:hidden sticky top-0 z-30 transition-all duration-200', scrolled ? 'bg-glass border-b border-border-subtle shadow-sm' : 'bg-transparent')}><div className="px-4 flex items-center justify-between h-16"><Link href="/dashboard" className="flex items-center gap-2"><span className="flex h-8 w-8 items-center justify-center rounded-lg bg-text-primary text-white text-sm">⏰</span><span className="font-bold text-lg text-text-primary">DeadlineKeeper</span></Link><div className="w-8 h-8 bg-brand/10 text-brand rounded-full flex items-center justify-center text-sm font-bold">{user?.email?.charAt(0).toUpperCase() || '?'}</div></div></header>
      <header className={cn('hidden md:flex sticky top-0 z-30 h-16 items-center justify-end px-8 transition-all duration-200', scrolled ? 'bg-glass border-b border-border-subtle shadow-sm' : 'bg-transparent')} />
      <main className="flex-1 px-4 sm:px-6 lg:px-8 py-6 pb-24 md:pb-6"><div className="max-w-5xl mx-auto">{children}</div></main>
    </div>

    <nav className="md:hidden fixed bottom-0 left-0 right-0 z-40 bg-glass-strong border-t border-border-subtle pb-safe"><div className="flex items-center justify-around px-2 h-16">{navItems.map(item => { const active = pathname === item.href; return <Link key={item.href} href={item.href} className={cn('flex flex-col items-center justify-center min-w-[44px] min-h-[44px] w-full h-full gap-1 transition-colors', active ? 'text-brand' : 'text-text-secondary hover:text-text-primary')}><item.icon className="w-5 h-5" /><span className="text-[10px] font-semibold">{item.label}</span></Link>; })}</div></nav>
    <Link href="/dashboard/events/new" className="md:hidden fixed bottom-20 right-4 z-40 bg-text-primary text-white w-14 h-14 rounded-2xl flex items-center justify-center shadow-float hover:scale-105 active:scale-95 transition-all duration-200" aria-label="Add deadline"><Plus className="w-6 h-6" /></Link>
  </div>;
}
