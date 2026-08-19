'use client';

import { useState, useEffect } from 'react';
import { userApi, calendarApi, type UserProfile } from '@/lib/api';
import { useAuth } from '@/lib/auth';

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
      setMessage('Settings saved.');
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

  if (loading) return <div className="text-center text-gray-400 py-12">Loading...</div>;

  return (
    <div className="max-w-2xl mx-auto">
      <h1 className="text-2xl font-bold text-gray-900 mb-6">Settings</h1>

      {message && (
        <div className="mb-4 p-3 rounded-lg text-sm bg-gray-50 text-gray-700" role="status">
          {message}
        </div>
      )}

      <div className="space-y-6">
        <section className="bg-white rounded-xl border border-gray-200 p-6" aria-labelledby="profile-heading">
          <h2 id="profile-heading" className="text-lg font-semibold text-gray-900 mb-4">Profile</h2>
          <div className="space-y-4">
            <div>
              <label className="block text-sm font-medium text-gray-700 mb-1" htmlFor="email">Email</label>
              <input id="email" type="email" value={profile?.email || ''} disabled className="w-full px-3 py-2 border border-gray-200 rounded-lg bg-gray-50 text-gray-500" />
            </div>
            <div>
              <label className="block text-sm font-medium text-gray-700 mb-1" htmlFor="display-name">Display Name</label>
              <input id="display-name" type="text" maxLength={100} value={displayName} onChange={(e) => setDisplayName(e.target.value)} className="w-full px-3 py-2 border border-gray-300 rounded-lg focus:ring-2 focus:ring-brand-500 focus:border-transparent outline-none" placeholder="Your name" />
            </div>
            <div>
              <label className="block text-sm font-medium text-gray-700 mb-1" htmlFor="timezone">Timezone</label>
              <input id="timezone" type="text" value={timezone} onChange={(e) => setTimezone(e.target.value)} className="w-full px-3 py-2 border border-gray-300 rounded-lg focus:ring-2 focus:ring-brand-500 focus:border-transparent outline-none" placeholder="e.g., Asia/Kolkata" />
            </div>
          </div>
        </section>

        <section className="bg-white rounded-xl border border-gray-200 p-6" aria-labelledby="inbox-heading">
          <h2 id="inbox-heading" className="text-lg font-semibold text-gray-900 mb-2">Inbox forwarding</h2>
          <p className="text-sm text-gray-500 mb-4">Forward an email to this address to create deadlines from its contents.</p>
          <div className="flex gap-2 items-center">
            <input
              type="email"
              readOnly
              value={forwardingAddress}
              aria-label="Your DeadlineKeeper forwarding address"
              className="flex-1 min-w-0 px-3 py-2 border border-gray-200 rounded-lg bg-gray-50 text-gray-700 text-sm"
            />
            <button
              type="button"
              onClick={() => navigator.clipboard.writeText(forwardingAddress).then(() => setMessage('Forwarding address copied.'))}
              className="text-sm px-3 py-2 border border-gray-300 rounded-lg hover:bg-gray-50 transition-colors"
              disabled={!forwardingAddress}
            >
              Copy
            </button>
          </div>
        </section>

        <section className="bg-white rounded-xl border border-gray-200 p-6" aria-labelledby="notifications-heading">
          <h2 id="notifications-heading" className="text-lg font-semibold text-gray-900 mb-4">Notifications</h2>
          <label className="flex items-center gap-3 cursor-pointer">
            <input type="checkbox" checked={emailNotifications} onChange={(e) => setEmailNotifications(e.target.checked)} className="w-4 h-4 text-brand-600 rounded border-gray-300 focus:ring-brand-500" />
            <div>
              <span className="text-sm font-medium text-gray-700">Email notifications</span>
              <p className="text-xs text-gray-500">Receive reminder emails before deadlines</p>
            </div>
          </label>
        </section>

        <section className="bg-white rounded-xl border border-gray-200 p-6" aria-labelledby="calendar-heading">
          <h2 id="calendar-heading" className="text-lg font-semibold text-gray-900 mb-4">Connected Accounts</h2>
          <div className="flex items-center justify-between gap-4">
            <div className="flex items-center gap-3">
              <span className="text-xl" aria-hidden="true">📅</span>
              <div>
                <span className="text-sm font-medium text-gray-700">Google Calendar</span>
                <p className="text-xs text-gray-500">Sync your calendar events</p>
              </div>
            </div>
            <div className="flex gap-2">
              <button type="button" onClick={() => window.open(calendarApi.startSync(), '_self')} className="text-xs px-3 py-1.5 bg-brand-50 text-brand-700 rounded-lg hover:bg-brand-100 transition-colors">Connect</button>
              <button type="button" onClick={handleDisconnectCalendar} className="text-xs px-3 py-1.5 text-red-600 hover:bg-red-50 rounded-lg transition-colors">Disconnect</button>
            </div>
          </div>
        </section>

        <section className="bg-white rounded-xl border border-gray-200 p-6" aria-labelledby="plan-heading">
          <div className="flex items-center justify-between">
            <div>
              <h2 id="plan-heading" className="text-lg font-semibold text-gray-900">Plan</h2>
              <p className="text-sm text-gray-500 mt-1">Current plan: <span className="capitalize font-medium">{profile?.plan || 'free'}</span></p>
            </div>
            <span className="px-3 py-1 bg-brand-50 text-brand-700 text-sm font-medium rounded-lg">{profile?.plan === 'free' ? 'Free' : 'Pro'}</span>
          </div>
        </section>

        <button type="button" onClick={handleSave} disabled={saving} className="w-full bg-brand-600 text-white py-2 px-4 rounded-lg hover:bg-brand-700 transition-colors disabled:opacity-50 font-medium">
          {saving ? 'Saving...' : 'Save Settings'}
        </button>
      </div>
    </div>
  );
}
