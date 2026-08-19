'use client';

import { useState, useRef, useEffect } from 'react';
import { eventApi, type ExtractionResult, type ExtractedEvent, type ReminderInput } from '@/lib/api';
import { ExtractionPreview } from '@/components/ExtractionPreview';
import { ReminderConfig } from '@/components/ReminderConfig';
import { useRouter } from 'next/navigation';

type InputTab = 'manual' | 'screenshot' | 'paste';

export default function NewEventPage() {
  const router = useRouter();
  const [tab, setTab] = useState<InputTab>('manual');

  const [title, setTitle] = useState('');
  const [type, setType] = useState('other');
  const [dueAt, setDueAt] = useState('');
  const [timezone, setTimezone] = useState('UTC');
  const [reminders, setReminders] = useState<ReminderInput[]>([
    { offsetSeconds: 604800, channel: 'in_app' },
    { offsetSeconds: 86400, channel: 'in_app' },
    { offsetSeconds: 7200, channel: 'in_app' },
  ]);
  const [notes, setNotes] = useState('');

  const [extracting, setExtracting] = useState(false);
  const [extractionResult, setExtractionResult] = useState<ExtractionResult | null>(null);
  const [pastedText, setPastedText] = useState('');
  const [selectedFile, setSelectedFile] = useState<File | null>(null);
  const fileInputRef = useRef<HTMLInputElement>(null);

  useEffect(() => {
    // datetime-local is interpreted in the browser's local timezone. Keep the
    // stored IANA timezone aligned with that input to prevent silent time shifts.
    setTimezone(Intl.DateTimeFormat().resolvedOptions().timeZone || 'UTC');
  }, []);

  const handleManualSubmit = async (e: React.FormEvent) => {
    e.preventDefault();
    try {
      const isoDueAt = new Date(dueAt).toISOString();
      await eventApi.create({
        title: title.trim(),
        type,
        dueAt: isoDueAt,
        timezone,
        reminders,
        notes: notes.trim() || null,
      });
      router.push('/dashboard');
    } catch (err: unknown) {
      alert(err instanceof Error ? err.message : 'Failed to create event');
    }
  };

  const handleScreenshotExtract = async () => {
    if (!selectedFile) return;

    setExtracting(true);
    try {
      const formData = new FormData();
      formData.append('screenshot', selectedFile);
      const result = await eventApi.extract(formData);
      setExtractionResult(result);
    } catch (err: unknown) {
      alert(err instanceof Error ? err.message : 'Extraction failed');
    } finally {
      setExtracting(false);
    }
  };

  const handleTextExtract = async () => {
    if (!pastedText.trim()) return;

    setExtracting(true);
    try {
      const formData = new FormData();
      formData.append('pastedText', pastedText);
      const result = await eventApi.extract(formData);
      setExtractionResult(result);
    } catch (err: unknown) {
      alert(err instanceof Error ? err.message : 'Extraction failed');
    } finally {
      setExtracting(false);
    }
  };

  const handleConfirmExtraction = async (events: ExtractedEvent[]) => {
    try {
      await eventApi.confirmExtract({
        events: events.map((event) => ({
          title: event.title,
          type: event.type,
          dueAt: event.dueAt,
          timezone: event.timezone,
          reminders: [
            { offsetSeconds: 604800, channel: 'in_app' },
            { offsetSeconds: 86400, channel: 'in_app' },
            { offsetSeconds: 7200, channel: 'in_app' },
          ],
        })),
        sourceType: tab === 'screenshot' ? 'screenshot' : 'pasted_text',
      });
      router.push('/dashboard');
    } catch (err: unknown) {
      alert(err instanceof Error ? err.message : 'Failed to save events');
    }
  };

  if (extractionResult) {
    return (
      <div className="max-w-2xl mx-auto">
        <ExtractionPreview
          events={extractionResult.events}
          clarificationQuestion={extractionResult.clarificationQuestion}
          onConfirm={handleConfirmExtraction}
          onCancel={() => setExtractionResult(null)}
        />
      </div>
    );
  }

  return (
    <div className="max-w-2xl mx-auto">
      <h1 className="text-2xl font-bold text-gray-900 mb-6">Add Deadline</h1>

      <div className="flex border-b border-gray-200 mb-6 overflow-x-auto">
        {[
          { key: 'manual', label: '✏️ Manual' },
          { key: 'screenshot', label: '📸 Screenshot' },
          { key: 'paste', label: '📋 Paste Text' },
        ].map(({ key, label }) => (
          <button
            key={key}
            type="button"
            onClick={() => setTab(key as InputTab)}
            aria-selected={tab === key}
            role="tab"
            className={`px-4 py-2 text-sm font-medium border-b-2 whitespace-nowrap transition-colors ${
              tab === key
                ? 'border-brand-600 text-brand-600'
                : 'border-transparent text-gray-500 hover:text-gray-700'
            }`}
          >
            {label}
          </button>
        ))}
      </div>

      {tab === 'manual' && (
        <form onSubmit={handleManualSubmit} className="space-y-4">
          <div>
            <label htmlFor="event-title" className="block text-sm font-medium text-gray-700 mb-1">Title *</label>
            <input id="event-title" type="text" value={title} onChange={(e) => setTitle(e.target.value)} required maxLength={200} className="w-full px-3 py-2 border border-gray-300 rounded-lg focus:ring-2 focus:ring-brand-500 focus:border-transparent outline-none" placeholder="e.g., CS101 Final Exam" />
          </div>

          <div className="grid grid-cols-1 sm:grid-cols-2 gap-4">
            <div>
              <label htmlFor="event-type" className="block text-sm font-medium text-gray-700 mb-1">Type *</label>
              <select id="event-type" value={type} onChange={(e) => setType(e.target.value)} className="w-full px-3 py-2 border border-gray-300 rounded-lg focus:ring-2 focus:ring-brand-500 focus:border-transparent outline-none">
                <option value="exam">📝 Exam</option>
                <option value="submission">📋 Submission</option>
                <option value="hackathon">💻 Hackathon</option>
                <option value="other">📌 Other</option>
              </select>
            </div>

            <div>
              <label htmlFor="event-timezone" className="block text-sm font-medium text-gray-700 mb-1">Timezone</label>
              <input id="event-timezone" type="text" value={timezone} readOnly aria-describedby="event-timezone-help" className="w-full px-3 py-2 border border-gray-200 bg-gray-50 text-gray-600 rounded-lg outline-none" />
              <p id="event-timezone-help" className="text-xs text-gray-400 mt-1">Uses your device timezone for this local date/time.</p>
            </div>
          </div>

          <div>
            <label htmlFor="event-due-at" className="block text-sm font-medium text-gray-700 mb-1">Due At *</label>
            <input id="event-due-at" type="datetime-local" value={dueAt} onChange={(e) => setDueAt(e.target.value)} required className="w-full px-3 py-2 border border-gray-300 rounded-lg focus:ring-2 focus:ring-brand-500 focus:border-transparent outline-none" />
          </div>

          <div>
            <label className="block text-sm font-medium text-gray-700 mb-2">Reminders</label>
            <ReminderConfig value={reminders} onChange={setReminders} />
          </div>

          <div>
            <label htmlFor="event-notes" className="block text-sm font-medium text-gray-700 mb-1">Notes</label>
            <textarea id="event-notes" value={notes} onChange={(e) => setNotes(e.target.value)} rows={3} maxLength={2000} className="w-full px-3 py-2 border border-gray-300 rounded-lg focus:ring-2 focus:ring-brand-500 focus:border-transparent outline-none resize-none" placeholder="Any additional notes..." />
          </div>

          <div className="flex flex-col sm:flex-row gap-3">
            <button type="submit" className="flex-1 bg-brand-600 text-white py-2 px-4 rounded-lg hover:bg-brand-700 transition-colors font-medium">Save Event</button>
            <button type="button" onClick={() => router.push('/dashboard')} className="px-4 py-2 border border-gray-300 text-gray-700 rounded-lg hover:bg-gray-50 transition-colors">Cancel</button>
          </div>
        </form>
      )}

      {tab === 'screenshot' && (
        <div className="space-y-4">
          <div className="border-2 border-dashed border-gray-300 rounded-xl p-8 text-center hover:border-brand-400 transition-colors">
            <input ref={fileInputRef} type="file" accept="image/*" onChange={(e) => setSelectedFile(e.target.files?.[0] ?? null)} className="hidden" id="screenshot-upload" />
            <label htmlFor="screenshot-upload" className="cursor-pointer block">
              <div className="text-4xl mb-3" aria-hidden="true">📸</div>
              {selectedFile ? <p className="text-brand-600 font-medium break-all">{selectedFile.name}</p> : <><p className="text-gray-600 font-medium">Click to upload a screenshot</p><p className="text-sm text-gray-400 mt-1">PNG, JPEG or other image formats</p></>}
            </label>
          </div>
          <button type="button" onClick={handleScreenshotExtract} disabled={extracting || !selectedFile} className="w-full bg-brand-600 text-white py-2 px-4 rounded-lg hover:bg-brand-700 transition-colors disabled:opacity-50 font-medium">
            {extracting ? 'Extracting deadlines...' : 'Extract Deadlines'}
          </button>
        </div>
      )}

      {tab === 'paste' && (
        <div className="space-y-4">
          <label htmlFor="deadline-text" className="sr-only">Deadline text</label>
          <textarea id="deadline-text" value={pastedText} onChange={(e) => setPastedText(e.target.value)} rows={8} maxLength={20000} className="w-full px-3 py-2 border border-gray-300 rounded-lg focus:ring-2 focus:ring-brand-500 focus:border-transparent outline-none resize-none font-mono text-sm" placeholder={'Paste the deadline text here...\n\ne.g., "The final project submission is due on December 15th at 11:59 PM"'} />
          <button type="button" onClick={handleTextExtract} disabled={extracting || !pastedText.trim()} className="w-full bg-brand-600 text-white py-2 px-4 rounded-lg hover:bg-brand-700 transition-colors disabled:opacity-50 font-medium">
            {extracting ? 'Extracting deadlines...' : 'Extract Deadlines'}
          </button>
        </div>
      )}
    </div>
  );
}
