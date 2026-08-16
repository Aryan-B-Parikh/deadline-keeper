import { redirect } from 'next/navigation';
import { useAuth } from '@/lib/auth';

export function useRequireAuth() {
  const { user, loading } = useAuth();
  if (!loading && !user) redirect('/login');
  return { user, loading };
}
