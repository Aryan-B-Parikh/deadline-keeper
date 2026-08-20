'use client';

import { useState, useEffect } from 'react';
import { userApi, calendarApi, type UserProfile } from '@/lib/api';
import { useAuth } from '@/lib/auth';
import { cn } from '@/lib/utils';
import { User, Inbox as InboxIcon, Bell, Calendar as CalendarIcon, Zap, CheckCircle2, Copy } from 'lucide-react';

function errorMessage(error: unknown, fallback: string): string {
  return error instanceof Error ? error.message : fallback;
}

export default function SettingsPage() {
  const { user } = useAuth();
  const [profile, setProfile] = useState<UserProfile | null>(null);
  const [forwardingAddress, setForwardingAddress] = useState('');
  const [loading, setLoading] = useState(true);
  const [saving, setSaving] = useState(false);
  const [displayName, setDisplayName] = useState('');
  const [timezone, setTimezone] = useState('UTC');
  const [emailNotifications, setEmailNotifications] = useState(true);
  const [message, setMessage] = useState('');
  const [copied, setCopied] = useState(false);

  useEffect(() => {
    if (!user) return;

    Promise.all([userApi.getProfile(), userApi.getForwardingAddress()])
      .then(([p, forwarding]) => {
        setProfile(p);
        setDisplayName(p.displayName || '');
        setTimezone(p.timezone);
        setEmailNotifications(p.notificationPrefs?.channels?.includes('email') ?? true);
        setForwardingAddress(forwarding.address);
      })
      .catch((error: unknown) => setMessage(errorMessage(error, 'Failed to load settings')))
      .finally(() => setLoading(false));
  }, [user]);

  const handleSave = async () => {
    setSaving(true);
    setMessage('');
    try {
      const updated = await userApi.updateProfile({
        displayName: displayName || undefined,
        timezone,
        notificationPrefs: {
          channels: emailNotifications ? ['email'] : [],
          default_offsets: profile?.notificationPrefs?.default_offsets || ['7d', '1d', '2h'],
        },
      });
      setProfile(updated);
      setMessage('Settings saved successfully.');
      setTimeout(() => setMessage(''), 3000);
    } catch (error: unknown) {
      setMessage(errorMessage(error, 'Failed to save settings'));
    } finally {
      setSaving(false);
    }
  };

  const handleDisconnectCalendar = async () => {
    if (!window.confirm('Disconnect Google Calendar? Synced events will remain.')) return;
    try {
      await calendarApi.disconnect();
      setMessage('Google Calendar disconnected.');
    } catch (error: unknown) {
      setMessage(errorMessage(error, 'Failed to disconnect calendar'));
    }
  };

  const handleCopyForwarding = () => {
    if (!forwardingAddress) return;
    navigator.clipboard.writeText(forwardingAddress).then(() => {
      setCopied(true);
      setTimeout(() => setCopied(false), 2000);
    });
  };

  if (loading) {
    return (
      <div className="max-w-2xl mx-auto space-y-6 animate-pulse">
        <div className="h-10 w-40 bg-surface-hover rounded-xl mb-8" />
        <div className="h-[250px] bg-surface-hover rounded-2xl border border-border-subtle" />
        <div className="h-[150px] bg-surface-hover rounded-2xl border border-border-subtle" />
      </div>
    );
  }

  const inputClasses = "w-full px-4 py-2.5 bg-surface-elevated border border-border-strong rounded-xl text-text-primary placeholder:text-text-muted focus:outline-none focus:ring-2 focus:ring-brand/20 focus:border-brand transition-all shadow-sm";
  const labelClasses = "block text-sm font-medium text-text-secondary mb-1.5 ml-1";
  const sectionClasses = "bg-surface border border-border-subtle rounded-2xl p-6 sm:p-8 shadow-sm transition-all hover:shadow-soft";

  return (
    <div className="max-w-2xl mx-auto pb-12 animate-in fade-in slide-in-from-bottom-4 duration-500">
      <div className="flex items-center gap-3 mb-8">
        <div className="w-10 h-10 rounded-xl bg-brand/10 text-brand flex items-center justify-center shadow-sm">
          <User className="w-5 h-5" />
        </div>
        <div>
          <h1 className="text-2xl font-bold text-text-primary tracking-tight">Settings</h1>
          <p className="text-sm text-text-secondary">Manage your account and preferences</p>
        </div>
      </div>

      {message && (
        <div className={cn(
          "mb-6 p-4 rounded-xl text-sm font-medium border shadow-sm flex items-center gap-2",
          message.includes('saved') || message.includes('disconnected') 
            ? "bg-success/10 text-success border-success/20" 
            : "bg-warning/10 text-warning border-warning/20"
        )} role="status">
          <CheckCircle2 className="w-4 h-4" />
          {message}
        </div>
      )}

      <div className="space-y-6">
        <section className={sectionClasses} aria-labelledby="profile-heading">
          <div className="flex items-center gap-2 mb-6">
            <User className="w-5 h-5 text-text-muted" />
            <h2 id="profile-heading" className="text-lg font-semibold text-text-primary">Profile</h2>
          </div>
          <div className="space-y-5">
            <div>
              <label className={labelClasses} htmlFor="email">Email</label>
              <input id="email" type="email" value={profile?.email || ''} disabled className={cn(inputClasses, "opacity-70 cursor-not-allowed")} />
            </div>
            <div>
              <label className={labelClasses} htmlFor="display-name">Display Name</label>
              <input id="display-name" type="text" maxLength={100} value={displayName} onChange={(e) => setDisplayName(e.target.value)} className={inputClasses} placeholder="Your name" />
            </div>
            <div>
              <label className={labelClasses} htmlFor="timezone">Timezone</label>
              <input id="timezone" type="text" value={timezone} onChange={(e) => setTimezone(e.target.value)} className={inputClasses} placeholder="e.g., Asia/Kolkata" />
            </div>
          </div>
        </section>

        <section className={sectionClasses} aria-labelledby="inbox-heading">
          <div className="flex items-center gap-2 mb-2">
            <InboxIcon className="w-5 h-5 text-text-muted" />
            <h2 id="inbox-heading" className="text-lg font-semibold text-text-primary">Inbox Forwarding</h2>
          </div>
          <p className="text-sm text-text-secondary mb-5">Forward an email to this address to automatically extract deadlines.</p>
          <div className="flex flex-col sm:flex-row gap-3">
            <input
              type="email"
              readOnly
              value={forwardingAddress}
              aria-label="Your DeadlineKeeper forwarding address"
              className={cn(inputClasses, "flex-1 min-w-0 bg-surface-hover")}
            />
            <button
              type="button"
              onClick={handleCopyForwarding}
              className="flex items-center justify-center gap-2 px-5 py-2.5 border border-border-strong text-text-primary rounded-xl hover:bg-surface-hover hover:border-brand/50 transition-all font-medium whitespace-nowrap"
              disabled={!forwardingAddress}
            >
              {copied ? <CheckCircle2 className="w-4 h-4 text-success" /> : <Copy className="w-4 h-4 text-text-muted" />}
              {copied ? 'Copied' : 'Copy'}
            </button>
          </div>
        </section>

        <section className={sectionClasses} aria-labelledby="notifications-heading">
          <div className="flex items-center gap-2 mb-5">
            <Bell className="w-5 h-5 text-text-muted" />
            <h2 id="notifications-heading" className="text-lg font-semibold text-text-primary">Notifications</h2>
          </div>
          <label className="flex items-center gap-4 cursor-pointer p-4 rounded-xl border border-border-subtle bg-surface-hover/50 hover:border-border-strong transition-colors">
            <input 
              type="checkbox" 
              checked={emailNotifications} 
              onChange={(e) => setEmailNotifications(e.target.checked)} 
              className="w-5 h-5 text-brand rounded border-border-strong focus:ring-brand/20 bg-surface transition-all" 
            />
            <div>
              <span className="block text-sm font-medium text-text-primary mb-0.5">Email notifications</span>
              <span className="block text-xs text-text-secondary">Receive reminder emails before upcoming deadlines</span>
            </div>
          </label>
        </section>

        <section className={sectionClasses} aria-labelledby="calendar-heading">
          <div className="flex items-center gap-2 mb-5">
            <CalendarIcon className="w-5 h-5 text-text-muted" />
            <h2 id="calendar-heading" className="text-lg font-semibold text-text-primary">Connected Accounts</h2>
          </div>
          <div className="flex flex-col sm:flex-row sm:items-center justify-between gap-4 p-4 rounded-xl border border-border-subtle bg-surface-hover/50">
            <div className="flex items-center gap-3">
              <div className="w-10 h-10 bg-white rounded-lg flex items-center justify-center shadow-sm shrink-0">
                <span className="text-xl" aria-hidden="true">📅</span>
              </div>
              <div>
                <span className="block text-sm font-medium text-text-primary mb-0.5">Google Calendar</span>
                <span className="block text-xs text-text-secondary">Sync your deadlines to Google Calendar</span>
              </div>
            </div>
            <div className="flex gap-2">
              <button 
                type="button" 
                onClick={() => window.open(calendarApi.startSync(), '_self')} 
                className="px-4 py-2 bg-brand/10 text-brand rounded-lg hover:bg-brand/20 transition-colors text-sm font-medium"
              >
                Connect
              </button>
              <button 
                type="button" 
                onClick={handleDisconnectCalendar} 
                className="px-4 py-2 text-danger hover:bg-danger/10 rounded-lg transition-colors text-sm font-medium"
              >
                Disconnect
              </button>
            </div>
          </div>
        </section>

        <section className={sectionClasses} aria-labelledby="plan-heading">
          <div className="flex items-center justify-between">
            <div className="flex items-center gap-3">
              <div className="w-10 h-10 bg-brand/10 text-brand rounded-lg flex items-center justify-center shadow-sm shrink-0">
                <Zap className="w-5 h-5" />
              </div>
              <div>
                <h2 id="plan-heading" className="text-sm font-semibold text-text-primary mb-0.5">Current Plan</h2>
                <p className="text-xs text-text-secondary">You are currently on the <span className="capitalize font-medium text-text-primary">{profile?.plan || 'free'}</span> tier</p>
              </div>
            </div>
            <span className="px-4 py-1.5 bg-brand text-white text-sm font-bold tracking-wide uppercase rounded-lg shadow-sm">
              {profile?.plan === 'free' ? 'Free' : 'Pro'}
            </span>
          </div>
        </section>

        <div className="pt-4 pb-12">
          <button 
            type="button" 
            onClick={handleSave} 
            disabled={saving} 
            className="w-full sm:w-auto px-8 py-3 bg-brand text-white rounded-xl hover:bg-brand-hover shadow-float hover:shadow-soft hover:-translate-y-0.5 transition-all disabled:opacity-50 disabled:transform-none font-medium text-center"
          >
            {saving ? 'Saving Changes...' : 'Save Settings'}
          </button>
        </div>
      </div>
    </div>
  );
}
