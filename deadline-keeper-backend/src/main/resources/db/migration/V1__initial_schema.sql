-- DeadlineKeeper: Initial schema
-- Depends on Supabase auth.users table existing

-- Users table (extends Supabase auth.users)
CREATE TABLE users (
    id UUID PRIMARY KEY,
    email TEXT NOT NULL,
    display_name TEXT,
    timezone TEXT DEFAULT 'UTC',
    plan TEXT DEFAULT 'free',
    notification_prefs JSONB DEFAULT '{"channels":["email"],"default_offsets":["7d","1d","2h"]}',
    created_at TIMESTAMPTZ DEFAULT NOW(),
    updated_at TIMESTAMPTZ DEFAULT NOW()
);

-- Trigger to auto-create user row on Supabase signup
CREATE OR REPLACE FUNCTION public.handle_new_user()
RETURNS TRIGGER AS $$
BEGIN
    INSERT INTO public.users (id, email)
    VALUES (NEW.id, NEW.email);
    RETURN NEW;
END;
$$ LANGUAGE plpgsql SECURITY DEFINER;

CREATE TRIGGER on_auth_user_created
    AFTER INSERT ON auth.users
    FOR EACH ROW EXECUTE FUNCTION public.handle_new_user();

-- Events table
CREATE TABLE events (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    user_id UUID NOT NULL REFERENCES users(id),
    title TEXT NOT NULL,
    type TEXT NOT NULL CHECK (type IN ('exam', 'submission', 'hackathon', 'other')),
    due_date DATE NOT NULL,
    due_time TIME,
    timezone TEXT DEFAULT 'UTC',
    source TEXT NOT NULL CHECK (source IN ('manual', 'screenshot', 'pasted_text', 'email', 'calendar_sync')),
    source_reference TEXT,
    source_file_url TEXT,
    confidence_score REAL DEFAULT 1.0,
    status TEXT DEFAULT 'upcoming' CHECK (status IN ('upcoming', 'due_soon', 'overdue', 'done')),
    reminder_schedule TEXT[] DEFAULT '{"7d","1d","2h"}',
    notes TEXT,
    created_at TIMESTAMPTZ DEFAULT NOW(),
    updated_at TIMESTAMPTZ DEFAULT NOW()
);

CREATE INDEX idx_events_user_status ON events(user_id, status);
CREATE INDEX idx_events_due_date ON events(due_date);

-- Reminder logs (prevents duplicate fires)
CREATE TABLE reminder_logs (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    event_id UUID NOT NULL REFERENCES events(id) ON DELETE CASCADE,
    offset_fired TEXT NOT NULL,
    fired_at TIMESTAMPTZ DEFAULT NOW(),
    UNIQUE(event_id, offset_fired)
);

-- In-app notifications
CREATE TABLE notifications (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    user_id UUID NOT NULL REFERENCES users(id),
    event_id UUID REFERENCES events(id),
    title TEXT NOT NULL,
    message TEXT NOT NULL,
    is_read BOOLEAN DEFAULT FALSE,
    channel TEXT DEFAULT 'in_app',
    created_at TIMESTAMPTZ DEFAULT NOW()
);

CREATE INDEX idx_notifications_user_read ON notifications(user_id, is_read);

-- Google Calendar sync tokens
CREATE TABLE calendar_sync (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    user_id UUID NOT NULL REFERENCES users(id) UNIQUE,
    google_access_token TEXT,
    google_refresh_token TEXT,
    sync_token TEXT,
    last_synced_at TIMESTAMPTZ,
    created_at TIMESTAMPTZ DEFAULT NOW()
);

-- Row-Level Security
ALTER TABLE users ENABLE ROW LEVEL SECURITY;
ALTER TABLE events ENABLE ROW LEVEL SECURITY;
ALTER TABLE reminder_logs ENABLE ROW LEVEL SECURITY;
ALTER TABLE notifications ENABLE ROW LEVEL SECURITY;
ALTER TABLE calendar_sync ENABLE ROW LEVEL SECURITY;

CREATE POLICY "Users can view own profile" ON users
    FOR SELECT USING (auth.uid() = id);
CREATE POLICY "Users can update own profile" ON users
    FOR UPDATE USING (auth.uid() = id);

CREATE POLICY "Users can view own events" ON events
    FOR SELECT USING (auth.uid() = user_id);
CREATE POLICY "Users can insert own events" ON events
    FOR INSERT WITH CHECK (auth.uid() = user_id);
CREATE POLICY "Users can update own events" ON events
    FOR UPDATE USING (auth.uid() = user_id);
CREATE POLICY "Users can delete own events" ON events
    FOR DELETE USING (auth.uid() = user_id);

CREATE POLICY "Users can view own notifications" ON notifications
    FOR SELECT USING (auth.uid() = user_id);
CREATE POLICY "Users can update own notifications" ON notifications
    FOR UPDATE USING (auth.uid() = user_id);

CREATE POLICY "Users can view own calendar sync" ON calendar_sync
    FOR SELECT USING (auth.uid() = user_id);
CREATE POLICY "Users can insert own calendar sync" ON calendar_sync
    FOR INSERT WITH CHECK (auth.uid() = user_id);
CREATE POLICY "Users can update own calendar sync" ON calendar_sync
    FOR UPDATE USING (auth.uid() = user_id);
CREATE POLICY "Users can delete own calendar sync" ON calendar_sync
    FOR DELETE USING (auth.uid() = user_id);

-- Updated_at trigger function
CREATE OR REPLACE FUNCTION update_updated_at_column()
RETURNS TRIGGER AS $$
BEGIN
    NEW.updated_at = NOW();
    RETURN NEW;
END;
$$ LANGUAGE plpgsql;

CREATE TRIGGER update_users_updated_at
    BEFORE UPDATE ON users
    FOR EACH ROW EXECUTE FUNCTION update_updated_at_column();

CREATE TRIGGER update_events_updated_at
    BEFORE UPDATE ON events
    FOR EACH ROW EXECUTE FUNCTION update_updated_at_column();
