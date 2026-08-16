'use client';

import { useAuth } from '@/lib/auth';
import { config } from 'process';

export default function InboxPage() {
  const { user } = useAuth();

  return (
    <div className="max-w-2xl mx-auto">
      <h1 className="text-2xl font-bold text-gray-900 mb-6">Inbox Forwarding</h1>

      <div className="bg-white rounded-xl border border-gray-200 p-6">
        <h2 className="text-lg font-semibold text-gray-900 mb-2">Forward deadline emails</h2>
        <p className="text-gray-500 text-sm mb-6">
          Send or forward emails containing deadlines to the address below.
          We&apos;ll automatically extract the deadlines and add them to your account.
        </p>

        <div className="bg-gray-50 rounded-lg p-4 border border-gray-200">
          <label className="block text-xs font-medium text-gray-500 mb-1">Forward emails to</label>
          <div className="flex items-center gap-2">
            <code className="text-lg font-mono font-semibold text-brand-600 select-all">
              deadlines@deadlinekeeper.com
            </code>
            <button
              onClick={() => navigator.clipboard.writeText('deadlines@deadlinekeeper.com')}
              className="text-xs text-gray-400 hover:text-gray-600 px-2 py-1 border border-gray-200 rounded"
            >
              Copy
            </button>
          </div>
        </div>

        <div className="mt-6 space-y-3">
          <h3 className="text-sm font-medium text-gray-700">How it works</h3>
          <ol className="text-sm text-gray-500 space-y-2 list-decimal list-inside">
            <li>Forward an email with a deadline to the address above</li>
            <li>Our system reads the email and extracts deadline info</li>
            <li>You get a confirmation email with the extracted events</li>
            <li>Events appear in your dashboard automatically</li>
          </ol>
        </div>

        <div className="mt-6 p-4 bg-amber-50 border border-amber-200 rounded-lg">
          <p className="text-sm text-amber-700">
            <strong>Note:</strong> Make sure to send from your registered email address
            ({user?.email}). Only emails from verified users will be processed.
          </p>
        </div>
      </div>
    </div>
  );
}
