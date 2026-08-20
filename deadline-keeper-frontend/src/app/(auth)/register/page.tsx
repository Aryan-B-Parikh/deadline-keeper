'use client';

import { useState } from 'react';
import { useAuth } from '@/lib/auth';
import { useRouter } from 'next/navigation';
import { cn } from '@/lib/utils';
import { AlertCircle, ArrowRight, CheckCircle2, Mail } from 'lucide-react';
import Link from 'next/link';

export default function RegisterPage() {
  const { signUp, signInWithGoogle } = useAuth();
  const router = useRouter();
  const [email, setEmail] = useState('');
  const [password, setPassword] = useState('');
  const [error, setError] = useState('');
  const [success, setSuccess] = useState(false);
  const [loading, setLoading] = useState(false);

  const handleSubmit = async (e: React.FormEvent) => {
    e.preventDefault();
    setError('');
    setLoading(true);
    try {
      await signUp(email, password);
      setSuccess(true);
    } catch (err: any) {
      setError(err.message || 'Sign up failed');
    } finally {
      setLoading(false);
    }
  };

  const inputClasses = "w-full px-4 py-2.5 bg-surface-hover border border-border-strong rounded-xl text-text-primary placeholder:text-text-muted focus:outline-none focus:ring-2 focus:ring-brand/20 focus:border-brand transition-all shadow-sm";
  const labelClasses = "block text-sm font-medium text-text-secondary mb-1.5 ml-1";

  if (success) {
    return (
      <div className="min-h-screen flex items-center justify-center bg-background px-4 relative overflow-hidden">
        {/* Decorative Background Elements */}
        <div className="absolute top-0 left-1/2 -translate-x-1/2 w-full max-w-3xl h-64 bg-success/5 rounded-full blur-[100px] pointer-events-none" />
        
        <div className="max-w-md w-full bg-glass-strong rounded-2xl shadow-float border border-border-subtle p-8 text-center relative z-10 animate-in zoom-in-95 duration-500">
          <div className="inline-flex items-center justify-center w-16 h-16 rounded-2xl bg-success/10 text-success mb-6 shadow-sm border border-success/20">
            <Mail className="w-8 h-8" />
          </div>
          <h2 className="text-2xl font-bold text-text-primary mb-3">Check your email</h2>
          <p className="text-text-secondary mb-8 leading-relaxed">
            We&apos;ve sent you a confirmation link. Please verify your email to continue.
          </p>
          <Link href="/login" className="inline-flex items-center justify-center gap-2 w-full bg-surface-elevated border border-border-strong text-text-primary py-3 px-4 rounded-xl hover:bg-surface-hover transition-colors font-medium shadow-sm">
            Back to sign in
          </Link>
        </div>
      </div>
    );
  }

  return (
    <div className="min-h-screen flex items-center justify-center bg-background px-4 relative overflow-hidden">
      {/* Decorative Background Elements */}
      <div className="absolute top-0 left-1/2 -translate-x-1/2 w-full max-w-3xl h-64 bg-brand/5 rounded-full blur-[100px] pointer-events-none" />
      <div className="absolute bottom-0 right-0 w-96 h-96 bg-brand/5 rounded-full blur-[100px] pointer-events-none translate-x-1/3 translate-y-1/3" />

      <div className="max-w-[400px] w-full relative z-10 animate-in fade-in slide-in-from-bottom-4 duration-500">
        <div className="text-center mb-8">
          <div className="inline-flex items-center justify-center w-12 h-12 rounded-2xl bg-brand/10 text-brand text-2xl mb-4 shadow-sm">
            ⏰
          </div>
          <h1 className="text-3xl font-bold text-text-primary tracking-tight">DeadlineKeeper</h1>
          <p className="text-text-secondary mt-2">Create your account</p>
        </div>

        <div className="bg-glass-strong rounded-2xl shadow-float border border-border-subtle p-6 sm:p-8">
          <h2 className="text-xl font-semibold text-text-primary mb-6">Sign up</h2>

          {error && (
            <div className="flex items-start gap-2 bg-danger/10 border border-danger/20 text-danger text-sm rounded-xl p-3 mb-6">
              <AlertCircle className="w-4 h-4 shrink-0 mt-0.5" />
              <p>{error}</p>
            </div>
          )}

          <form onSubmit={handleSubmit} className="space-y-5">
            <div>
              <label className={labelClasses}>Email</label>
              <input
                type="email"
                value={email}
                onChange={(e) => setEmail(e.target.value)}
                required
                className={inputClasses}
                placeholder="you@example.com"
              />
            </div>

            <div>
              <label className={labelClasses}>Password</label>
              <input
                type="password"
                value={password}
                onChange={(e) => setPassword(e.target.value)}
                required
                minLength={6}
                className={inputClasses}
                placeholder="At least 6 characters"
              />
            </div>

            <button
              type="submit"
              disabled={loading}
              className="w-full bg-brand text-white py-3 px-4 rounded-xl hover:bg-brand-hover hover:shadow-soft transition-all disabled:opacity-50 disabled:hover:shadow-none font-medium flex items-center justify-center gap-2 group"
            >
              {loading ? 'Creating account...' : 'Create account'}
              {!loading && <ArrowRight className="w-4 h-4 group-hover:translate-x-1 transition-transform" />}
            </button>
          </form>

          <div className="relative my-6">
            <div className="absolute inset-0 flex items-center">
              <div className="w-full border-t border-border-strong" />
            </div>
            <div className="relative flex justify-center text-xs uppercase tracking-wider font-semibold">
              <span className="bg-surface-elevated px-3 text-text-muted">or continue with</span>
            </div>
          </div>

          <button
            onClick={() => signInWithGoogle()}
            className="w-full flex items-center justify-center gap-3 bg-surface-elevated border border-border-strong py-2.5 px-4 rounded-xl hover:bg-surface-hover hover:border-border-subtle transition-all font-medium text-text-primary shadow-sm"
          >
            <svg className="w-5 h-5" viewBox="0 0 24 24">
              <path fill="#4285F4" d="M22.56 12.25c0-.78-.07-1.53-.2-2.25H12v4.26h5.92a5.06 5.06 0 01-2.2 3.32v2.77h3.57c2.08-1.92 3.28-4.74 3.28-8.1z"/>
              <path fill="#34A853" d="M12 23c2.97 0 5.46-.98 7.28-2.66l-3.57-2.77c-.98.66-2.23 1.06-3.71 1.06-2.86 0-5.29-1.93-6.16-4.53H2.18v2.84C3.99 20.53 7.7 23 12 23z"/>
              <path fill="#FBBC05" d="M5.84 14.09c-.22-.66-.35-1.36-.35-2.09s.13-1.43.35-2.09V7.07H2.18C1.43 8.55 1 10.22 1 12s.43 3.45 1.18 4.93l2.85-2.22.81-.62z"/>
              <path fill="#EA4335" d="M12 5.38c1.62 0 3.06.56 4.21 1.64l3.15-3.15C17.45 2.09 14.97 1 12 1 7.7 1 3.99 3.47 2.18 7.07l3.66 2.84c.87-2.6 3.3-4.53 6.16-4.53z"/>
            </svg>
            Google
          </button>

          <p className="text-center text-sm text-text-secondary mt-8">
            Already have an account?{' '}
            <Link href="/login" className="text-brand hover:text-brand-hover font-semibold transition-colors">
              Sign in
            </Link>
          </p>
        </div>
      </div>
    </div>
  );
}
