'use client';

import { useAuth } from '@/lib/auth';
import { Mail, Copy, CheckCircle2, ShieldAlert, Inbox as InboxIcon } from 'lucide-react';
import { useState } from 'react';

export default function InboxPage() {
  const { user } = useAuth();
  const [copied, setCopied] = useState(false);

  const handleCopy = () => {
    navigator.clipboard.writeText('deadlines@deadlinekeeper.com');
    setCopied(true);
    setTimeout(() => setCopied(false), 2000);
  };

  return (
    <div className="max-w-2xl mx-auto space-y-6 animate-in fade-in slide-in-from-bottom-4 duration-500">
      <div className="flex items-center gap-3">
        <div className="w-10 h-10 rounded-xl bg-brand/10 text-brand flex items-center justify-center shadow-sm">
          <InboxIcon className="w-5 h-5" />
        </div>
        <div>
          <h1 className="text-2xl font-bold text-text-primary tracking-tight">Inbox Forwarding</h1>
          <p className="text-sm text-text-secondary">Forward emails to auto-extract deadlines</p>
        </div>
      </div>

      <div className="bg-surface border border-border-subtle rounded-2xl p-6 sm:p-8 shadow-sm">
        <h2 className="text-lg font-semibold text-text-primary mb-2">Forward deadline emails</h2>
        <p className="text-text-secondary text-sm mb-8 leading-relaxed">
          Send or forward emails containing syllabus dates, assignments, or deadlines to the address below.
          Our AI will automatically extract the deadlines and add them to your account.
        </p>

        <div className="bg-surface-elevated rounded-xl p-5 border border-border-strong shadow-sm mb-8 relative overflow-hidden group">
          <div className="absolute top-0 right-0 w-32 h-32 bg-brand/5 rounded-full blur-2xl -translate-y-1/2 translate-x-1/3 pointer-events-none group-hover:bg-brand/10 transition-colors" />
          
          <label className="block text-xs font-semibold text-text-secondary uppercase tracking-wider mb-2 relative z-10">
            Your Forwarding Address
          </label>
          <div className="flex flex-col sm:flex-row sm:items-center justify-between gap-4 relative z-10">
            <code className="text-lg sm:text-xl font-mono font-semibold text-brand select-all bg-brand/5 px-2 py-1 rounded-lg">
              deadlines@deadlinekeeper.com
            </code>
            <button
              onClick={handleCopy}
              className="flex items-center justify-center gap-2 px-4 py-2 bg-surface border border-border-strong rounded-xl hover:bg-surface-hover hover:border-brand/50 transition-all text-sm font-medium text-text-primary shadow-sm"
            >
              {copied ? <CheckCircle2 className="w-4 h-4 text-success" /> : <Copy className="w-4 h-4 text-text-muted" />}
              {copied ? 'Copied' : 'Copy'}
            </button>
          </div>
        </div>

        <div className="space-y-4 mb-8">
          <h3 className="text-sm font-semibold text-text-primary uppercase tracking-wider">How it works</h3>
          <div className="grid gap-3">
            {[
              { num: '1', text: 'Forward an email with a deadline to the address above' },
              { num: '2', text: 'Our AI reads the email and extracts deadline information' },
              { num: '3', text: 'You receive a confirmation email with the extracted events' },
              { num: '4', text: 'Events appear in your dashboard automatically' },
            ].map((step) => (
              <div key={step.num} className="flex items-start gap-3 p-3 rounded-xl bg-surface-hover/50 border border-border-subtle hover:border-border-strong transition-colors">
                <div className="w-6 h-6 rounded-md bg-surface border border-border-strong text-text-primary flex items-center justify-center text-xs font-bold shrink-0">
                  {step.num}
                </div>
                <p className="text-sm text-text-secondary pt-0.5">{step.text}</p>
              </div>
            ))}
          </div>
        </div>

        <div className="flex items-start gap-3 p-4 bg-warning/10 border border-warning/20 rounded-xl shadow-sm">
          <ShieldAlert className="w-5 h-5 text-warning shrink-0 mt-0.5" />
          <p className="text-sm text-warning leading-relaxed">
            <strong className="font-semibold block mb-1">Security Note</strong>
            Make sure to send from your registered email address
            (<span className="font-medium">{user?.email}</span>). 
            For security, only emails originating from verified user addresses will be processed.
          </p>
        </div>
      </div>
    </div>
  );
}
