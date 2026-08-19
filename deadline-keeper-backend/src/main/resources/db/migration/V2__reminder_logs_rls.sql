-- DeadlineKeeper: RLS policies for reminder_logs
-- reminder_logs had RLS enabled in V1 but no policies, making it unreadable
-- even by the owning user. Access is granted via the owning event's user_id.

ALTER TABLE reminder_logs ENABLE ROW LEVEL SECURITY;

CREATE POLICY "Users can view own reminder logs" ON reminder_logs
    FOR SELECT USING (
        EXISTS (SELECT 1 FROM events e WHERE e.id = reminder_logs.event_id AND e.user_id = auth.uid())
    );

CREATE POLICY "Users can insert own reminder logs" ON reminder_logs
    FOR INSERT WITH CHECK (
        EXISTS (SELECT 1 FROM events e WHERE e.id = reminder_logs.event_id AND e.user_id = auth.uid())
    );

CREATE POLICY "Users can update own reminder logs" ON reminder_logs
    FOR UPDATE USING (
        EXISTS (SELECT 1 FROM events e WHERE e.id = reminder_logs.event_id AND e.user_id = auth.uid())
    );

CREATE POLICY "Users can delete own reminder logs" ON reminder_logs
    FOR DELETE USING (
        EXISTS (SELECT 1 FROM events e WHERE e.id = reminder_logs.event_id AND e.user_id = auth.uid())
    );
