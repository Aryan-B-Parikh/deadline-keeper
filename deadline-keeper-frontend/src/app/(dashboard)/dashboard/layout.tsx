'use client';

import Link from 'next/link';
import { useAuth } from '@/lib/auth';
import { usePathname, useRouter } from 'next/navigation';
import { Calendar, Inbox, Settings, LogOut, LayoutDashboard, Plus, Command } from 'lucide-react';
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
    { href: '/dashboard', label: 'Overview', icon: LayoutDashboard },
    { href: '/dashboard/calendar', label: 'Calendar', icon: Calendar },
    { href: '/dashboard/inbox', label: 'Inbox', icon: Inbox },
    { href: '/dashboard/settings', label: 'Settings', icon: Settings },
  ];

  return <div className="min-h-screen bg-background flex flex-col md:flex-row">
    <aside className="hidden md:flex flex-col w-[248px] fixed inset-y-0 z-40 bg-surface shadow-neu border-r border-border-subtle/60">
      <div className="px-5 pt-6 pb-5"><Link href="/dashboard" className="flex items-center gap-3 group"><span className="flex h-9 w-9 items-center justify-center rounded-xl bg-brand text-white text-sm font-bold shadow-neu group-hover:shadow-neu-hover transition-all">DK</span><div><span className="block font-bold text-[15px] text-text-primary tracking-tight">DeadlineKeeper</span><span className="block text-[10px] font-semibold uppercase tracking-[0.14em] text-text-muted mt-0.5">Command center</span></div></Link></div>
      <div className="px-4 pb-5"><Link href="/dashboard/events/new" className="w-full flex items-center justify-between gap-2 bg-brand text-white min-h-[46px] px-4 rounded-2xl shadow-neu hover:shadow-neu-hover hover:-translate-y-0.5 active:translate-y-0 active:shadow-neu-inset-strong transition-all duration-200 font-bold text-sm"><span className="flex items-center gap-2"><Plus className="w-4 h-4" /> Add deadline</span><kbd className="hidden lg:inline-flex text-[10px] px-1.5 py-0.5 rounded-md bg-white/10 border border-white/10 font-medium">N</kbd></Link></div>
      <nav className="flex-1 px-3 space-y-2 overflow-y-auto">{navItems.map(item => { const active = pathname === item.href; return <Link key={item.href} href={item.href} className={cn('relative flex items-center gap-3 px-3 min-h-[44px] rounded-xl text-sm font-semibold transition-all duration-200', active ? 'bg-surface text-brand shadow-neu-inset font-bold' : 'text-text-secondary hover:bg-surface-hover hover:text-text-primary hover:shadow-neu')}><item.icon className={cn('w-[18px] h-[18px]', active ? 'text-brand' : 'text-text-muted')} />{item.label}{active && <span className="absolute right-3 w-1.5 h-1.5 rounded-full bg-brand shadow-[0_0_0_4px_rgb(var(--primary)/.08)]" />}</Link>; })}</nav>
      <div className="p-4 border-t border-border-subtle"><div className="flex items-center justify-between px-2"><div className="flex items-center gap-3 truncate"><div className="w-9 h-9 bg-surface text-brand shadow-neu-inset rounded-full flex items-center justify-center text-sm font-bold flex-shrink-0">{user?.email?.charAt(0).toUpperCase() || '?'}</div><div className="truncate"><p className="text-sm font-semibold text-text-primary truncate">{user?.email?.split('@')[0] || 'User'}</p><p className="text-[10px] font-semibold uppercase tracking-wider text-text-muted truncate">Free plan</p></div></div><button onClick={handleSignOut} className="text-text-muted hover:text-danger transition-all p-2 rounded-lg hover:shadow-neu-inset active:shadow-neu-inset-strong" aria-label="Sign out"><LogOut className="w-4 h-4" /></button></div></div>
    </aside>

    <div className="flex-1 md:pl-[248px] flex flex-col min-h-screen">
      <header className={cn('md:hidden sticky top-0 z-30 transition-all duration-200', scrolled ? 'bg-glass border-b border-border-subtle shadow-sm' : 'bg-transparent')}><div className="px-4 flex items-center justify-between h-16"><Link href="/dashboard" className="flex items-center gap-2"><span className="flex h-8 w-8 items-center justify-center rounded-lg bg-brand text-white text-[10px] font-bold shadow-neu">DK</span><span className="font-bold text-[15px] text-text-primary">DeadlineKeeper</span></Link><div className="w-8 h-8 bg-surface text-brand shadow-neu-inset rounded-full flex items-center justify-center text-sm font-bold">{user?.email?.charAt(0).toUpperCase() || '?'}</div></div></header>
      <header className={cn('hidden md:flex sticky top-0 z-30 h-16 items-center justify-end px-8 transition-all duration-200', scrolled ? 'bg-glass border-b border-border-subtle shadow-sm' : 'bg-transparent')}><div className="flex items-center gap-2 text-text-muted text-xs"><Command className="w-3.5 h-3.5 text-brand" /> Your deadlines, organized.</div></header>
      <main className="flex-1 px-4 sm:px-6 lg:px-8 py-7 pb-24 md:pb-8"><div className="max-w-6xl mx-auto">{children}</div></main>
    </div>

    <nav className="md:hidden fixed bottom-0 left-0 right-0 z-40 bg-surface shadow-neu border-t border-border-subtle/60 pb-safe"><div className="flex items-center justify-around px-2 h-16">{navItems.map(item => { const active = pathname === item.href; return <Link key={item.href} href={item.href} className={cn('flex flex-col items-center justify-center min-w-[44px] min-h-[44px] w-full h-full gap-1 rounded-xl transition-all', active ? 'text-brand shadow-neu-inset' : 'text-text-secondary hover:text-text-primary')}><item.icon className="w-5 h-5" /><span className="text-[10px] font-semibold">{item.label}</span></Link>; })}</div></nav>
    <Link href="/dashboard/events/new" className="md:hidden fixed bottom-20 right-4 z-40 bg-brand text-white w-14 h-14 rounded-2xl flex items-center justify-center shadow-neu-hover hover:-translate-y-0.5 active:translate-y-0 active:shadow-neu-inset-strong transition-all duration-200" aria-label="Add deadline"><Plus className="w-6 h-6" /></Link>
  </div>;
}
