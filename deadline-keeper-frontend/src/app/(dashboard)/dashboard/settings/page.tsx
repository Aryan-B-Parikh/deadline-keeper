'use client';

import { useState, useEffect } from 'react';
import { userApi, calendarApi, type UserProfile } from '@/lib/api';
import { useAuth } from '@/lib/auth';

export default function SettingsPage() {
  const { user } = useAuth();
  const [profile, setProfile] = useState<UserProfile | null>(null);
  const [loading, setLoading] = useState(true);
  const [saving, setSaving] = useState(false);
  const [displayName, setDisplayName] = useState('');
  const [timezone, setTimezone] = useState('UTC');
  const [emailNotifications, setEmailNotifications] = useState(true);
  const [message, setMessage] = useState('');

  useEffect(() => {
    if (!user) return;
    userApi.getProfile().then((p) => {
      setProfile(p);
      setDisplayName(p.displayName || '');
      setTimezone(p.timezone);
      setEmailNotifications(p.notificationPrefs?.channels?.includes('email') ?? true);
    }).finally(() => setLoading(false));
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
      setMessage('Settings saved!');
    } catch (err: any) {
      setMessage(err.message || 'Failed to save');
    } finally {
      setSaving(false);
    }
  };

  const handleDisconnectCalendar = async () => {
    if (!confirm('Disconnect Google Calendar? Synced events will remain.')) return;
    try {
      await calendarApi.disconnect();
      setMessage('Google Calendar disconnected');
    } catch (err: any) {
      setMessage(err.message || 'Failed to disconnect');
    }
  };

  if (loading) return <div className="text-center text-gray-400 py-12">Loading...</div>;

  return (
    <div className="max-w-2xl mx-auto">
      <h1 className="text-2xl font-bold text-gray-900 mb-6">Settings</h1>

      {message && (
        <div className={`mb-4 p-3 rounded-lg text-sm ${
          message.includes('Failed') ? 'bg-red-50 text-red-700' : 'bg-green-50 text-green-700'
        }`}>
          {message}
        </div>
      )}

      <div className="space-y-6">
        {/* Profile */}
        <div className="bg-white rounded-xl border border-gray-200 p-6">
          <h2 className="text-lg font-semibold text-gray-900 mb-4">Profile</h2>
          <div className="space-y-4">
            <div>
              <label className="block text-sm font-medium text-gray-700 mb-1">Email</label>
              <input
                type="email"
                value={profile?.email || ''}
                disabled
                className="w-full px-3 py-2 border border-gray-200 rounded-lg bg-gray-50 text-gray-500"
              />
            </div>
            <div>
              <label className="block text-sm font-medium text-gray-700 mb-1">Display Name</label>
              <input
                type="text"
                value={displayName}
                onChange={(e) => setDisplayName(e.target.value)}
                className="w-full px-3 py-2 border border-gray-300 rounded-lg focus:ring-2 focus:ring-brand-500 focus:border-transparent outline-none"
                placeholder="Your name"
              />
            </div>
            <div>
              <label className="block text-sm font-medium text-gray-700 mb-1">Timezone</label>
              <input
                type="text"
                value={timezone}
                onChange={(e) => setTimezone(e.target.value)}
                className="w-full px-3 py-2 border border-gray-300 rounded-lg focus:ring-2 focus:ring-brand-500 focus:border-transparent outline-none"
                placeholder="e.g., America/New_York"
              />
            </div>
          </div>
        </div>

        {/* Notifications */}
        <div className="bg-white rounded-xl border border-gray-200 p-6">
          <h2 className="text-lg font-semibold text-gray-900 mb-4">Notifications</h2>
          <label className="flex items-center gap-3 cursor-pointer">
            <input
              type="checkbox"
              checked={emailNotifications}
              onChange={(e) => setEmailNotifications(e.target.checked)}
              className="w-4 h-4 text-brand-600 rounded border-gray-300 focus:ring-brand-500"
            />
            <div>
              <span className="text-sm font-medium text-gray-700">Email notifications</span>
              <p className="text-xs text-gray-500">Receive reminder emails before deadlines</p>
            </div>
          </label>
        </div>

        {/* Connected accounts */}
        <div className="bg-white rounded-xl border border-gray-200 p-6">
          <h2 className="text-lg font-semibold text-gray-900 mb-4">Connected Accounts</h2>
          <div className="flex items-center justify-between">
            <div className="flex items-center gap-3">
              <span className="text-xl">📅</span>
              <div>
                <span className="text-sm font-medium text-gray-700">Google Calendar</span>
                <p className="text-xs text-gray-500">Sync your calendar events</p>
              </div>
            </div>
            <div className="flex gap-2">
              <button
                onClick={() => window.open(calendarApi.startSync(), '_self')}
                className="text-xs px-3 py-1.5 bg-brand-50 text-brand-700 rounded-lg hover:bg-brand-100 transition-colors"
              >
                Connect
              </button>
              <button
                onClick={handleDisconnectCalendar}
                className="text-xs px-3 py-1.5 text-red-600 hover:bg-red-50 rounded-lg transition-colors"
              >
                Disconnect
              </button>
            </div>
          </div>
        </div>

        {/* Plan info */}
        <div className="bg-white rounded-xl border border-gray-200 p-6">
          <div className="flex items-center justify-between">
            <div>
              <h2 className="text-lg font-semibold text-gray-900">Plan</h2>
              <p className="text-sm text-gray-500 mt-1">
                Current plan: <span className="capitalize font-medium">{profile?.plan || 'free'}</span>
              </p>
            </div>
            <span className="px-3 py-1 bg-brand-50 text-brand-700 text-sm font-medium rounded-lg">
              {profile?.plan === 'free' ? 'Free' : 'Pro'}
            </span>
          </div>
        </div>

        <button
          onClick={handleSave}
          disabled={saving}
          className="w-full bg-brand-600 text-white py-2 px-4 rounded-lg hover:bg-brand-700 transition-colors disabled:opacity-50 font-medium"
        >
          {saving ? 'Saving...' : 'Save Settings'}
        </button>
      </div>
    </div>
  );
}
