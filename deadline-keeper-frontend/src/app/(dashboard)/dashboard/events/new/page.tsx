'use client';

import { useState, useRef } from 'react';
import { eventApi, type ExtractionResult, type ExtractedEvent } from '@/lib/api';
import { ExtractionPreview } from '@/components/ExtractionPreview';
import { ReminderConfig } from '@/components/ReminderConfig';
import { useRouter } from 'next/navigation';

type InputTab = 'manual' | 'screenshot' | 'paste';

export default function NewEventPage() {
  const router = useRouter();
  const [tab, setTab] = useState<InputTab>('manual');

  // Manual form state
  const [title, setTitle] = useState('');
  const [type, setType] = useState('other');
  const [dueAt, setDueAt] = useState('');
  const [timezone, setTimezone] = useState('UTC');
  const [reminders, setReminders] = useState<string[]>(['7d', '1d', '2h']);
  const [notes, setNotes] = useState('');

  // Extraction state
  const [extracting, setExtracting] = useState(false);
  const [extractionResult, setExtractionResult] = useState<ExtractionResult | null>(null);
  const [pastedText, setPastedText] = useState('');
  const [selectedFile, setSelectedFile] = useState<File | null>(null);
  const fileInputRef = useRef<HTMLInputElement>(null);

  // Manual submit
  const handleManualSubmit = async (e: React.FormEvent) => {
    e.preventDefault();
    try {
      const isoDueAt = new Date(dueAt).toISOString();
      await eventApi.create({
        title,
        type,
        dueAt: isoDueAt,
        timezone,
        reminderSchedule: reminders,
        notes: notes || null,
      });
      router.push('/dashboard');
    } catch (err: any) {
      alert(err.message || 'Failed to create event');
    }
  };

  // Screenshot extraction
  const handleScreenshotExtract = async () => {
    if (!selectedFile) return;

    setExtracting(true);
    try {
      const formData = new FormData();
      formData.append('screenshot', selectedFile);
      const result = await eventApi.extract(formData);
      setExtractionResult(result);
    } catch (err: any) {
      alert(err.message || 'Extraction failed');
    } finally {
      setExtracting(false);
    }
  };

  // Text extraction
  const handleTextExtract = async () => {
    if (!pastedText.trim()) return;

    setExtracting(true);
    try {
      const formData = new FormData();
      formData.append('pastedText', pastedText);
      const result = await eventApi.extract(formData);
      setExtractionResult(result);
    } catch (err: any) {
      alert(err.message || 'Extraction failed');
    } finally {
      setExtracting(false);
    }
  };

  // Confirm extraction
  const handleConfirmExtraction = async (events: ExtractedEvent[]) => {
    try {
      await eventApi.confirmExtract({
        events: events.map((e) => ({
          title: e.title,
          type: e.type,
          dueAt: e.dueAt,
          timezone: e.timezone,
          reminderSchedule: ['7d', '1d', '2h'],
        })),
        sourceType: tab === 'screenshot' ? 'screenshot' : 'pasted_text',
      });
      router.push('/dashboard');
    } catch (err: any) {
      alert(err.message || 'Failed to save events');
    }
  };

  // If extraction result is showing
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

      {/* Tabs */}
      <div className="flex border-b border-gray-200 mb-6">
        {[
          { key: 'manual', label: '✏️ Manual', },
          { key: 'screenshot', label: '📸 Screenshot' },
          { key: 'paste', label: '📋 Paste Text' },
        ].map(({ key, label }) => (
          <button
            key={key}
            onClick={() => setTab(key as InputTab)}
            className={`px-4 py-2 text-sm font-medium border-b-2 transition-colors ${
              tab === key
                ? 'border-brand-600 text-brand-600'
                : 'border-transparent text-gray-500 hover:text-gray-700'
            }`}
          >
            {label}
          </button>
        ))}
      </div>

      {/* Manual entry */}
      {tab === 'manual' && (
        <form onSubmit={handleManualSubmit} className="space-y-4">
          <div>
            <label className="block text-sm font-medium text-gray-700 mb-1">Title *</label>
            <input
              type="text"
              value={title}
              onChange={(e) => setTitle(e.target.value)}
              required
              className="w-full px-3 py-2 border border-gray-300 rounded-lg focus:ring-2 focus:ring-brand-500 focus:border-transparent outline-none"
              placeholder="e.g., CS101 Final Exam"
            />
          </div>

          <div className="grid grid-cols-2 gap-4">
            <div>
              <label className="block text-sm font-medium text-gray-700 mb-1">Type *</label>
              <select
                value={type}
                onChange={(e) => setType(e.target.value)}
                className="w-full px-3 py-2 border border-gray-300 rounded-lg focus:ring-2 focus:ring-brand-500 focus:border-transparent outline-none"
              >
                <option value="exam">📝 Exam</option>
                <option value="submission">📋 Submission</option>
                <option value="hackathon">💻 Hackathon</option>
                <option value="other">📌 Other</option>
              </select>
            </div>

            <div>
              <label className="block text-sm font-medium text-gray-700 mb-1">Timezone</label>
              <input
                type="text"
                value={timezone}
                onChange={(e) => setTimezone(e.target.value)}
                className="w-full px-3 py-2 border border-gray-300 rounded-lg focus:ring-2 focus:ring-brand-500 focus:border-transparent outline-none"
                placeholder="UTC"
              />
            </div>
          </div>

          <div className="grid grid-cols-2 gap-4">
            <div>
              <label className="block text-sm font-medium text-gray-700 mb-1">Due At *</label>
              <input
                type="datetime-local"
                value={dueAt}
                onChange={(e) => setDueAt(e.target.value)}
                required
                className="w-full px-3 py-2 border border-gray-300 rounded-lg focus:ring-2 focus:ring-brand-500 focus:border-transparent outline-none"
              />
            </div>
          </div>

          <div>
            <label className="block text-sm font-medium text-gray-700 mb-2">Reminders</label>
            <ReminderConfig value={reminders} onChange={setReminders} />
          </div>

          <div>
            <label className="block text-sm font-medium text-gray-700 mb-1">Notes</label>
            <textarea
              value={notes}
              onChange={(e) => setNotes(e.target.value)}
              rows={3}
              className="w-full px-3 py-2 border border-gray-300 rounded-lg focus:ring-2 focus:ring-brand-500 focus:border-transparent outline-none resize-none"
              placeholder="Any additional notes..."
            />
          </div>

          <div className="flex gap-3">
            <button
              type="submit"
              className="flex-1 bg-brand-600 text-white py-2 px-4 rounded-lg hover:bg-brand-700 transition-colors font-medium"
            >
              Save Event
            </button>
            <button
              type="button"
              onClick={() => router.push('/dashboard')}
              className="px-4 py-2 border border-gray-300 text-gray-700 rounded-lg hover:bg-gray-50 transition-colors"
            >
              Cancel
            </button>
          </div>
        </form>
      )}

      {/* Screenshot upload */}
      {tab === 'screenshot' && (
        <div className="space-y-4">
          <div className="border-2 border-dashed border-gray-300 rounded-xl p-8 text-center hover:border-brand-400 transition-colors">
            <input
              ref={fileInputRef}
              type="file"
              accept="image/*"
              onChange={(e) => setSelectedFile(e.target.files?.[0] ?? null)}
              className="hidden"
              id="screenshot-upload"
            />
            <label htmlFor="screenshot-upload" className="cursor-pointer">
              <div className="text-4xl mb-3">📸</div>
              {selectedFile ? (
                <p className="text-brand-600 font-medium">{selectedFile.name}</p>
              ) : (
                <>
                  <p className="text-gray-600 font-medium">Click to upload a screenshot</p>
                  <p className="text-sm text-gray-400 mt-1">or drag and drop</p>
                </>
              )}
            </label>
          </div>

          <button
            onClick={handleScreenshotExtract}
            disabled={extracting || !selectedFile}
            className="w-full bg-brand-600 text-white py-2 px-4 rounded-lg hover:bg-brand-700 transition-colors disabled:opacity-50 font-medium"
          >
            {extracting ? 'Extracting deadlines...' : 'Extract Deadlines'}
          </button>
        </div>
      )}

      {/* Paste text */}
      {tab === 'paste' && (
        <div className="space-y-4">
          <textarea
            value={pastedText}
            onChange={(e) => setPastedText(e.target.value)}
            rows={8}
            className="w-full px-3 py-2 border border-gray-300 rounded-lg focus:ring-2 focus:ring-brand-500 focus:border-transparent outline-none resize-none font-mono text-sm"
            placeholder="Paste the deadline text here...&#10;&#10;e.g., &quot;The final project submission is due on December 15th at 11:59 PM&quot;"
          />

          <button
            onClick={handleTextExtract}
            disabled={extracting || !pastedText.trim()}
            className="w-full bg-brand-600 text-white py-2 px-4 rounded-lg hover:bg-brand-700 transition-colors disabled:opacity-50 font-medium"
          >
            {extracting ? 'Extracting deadlines...' : 'Extract Deadlines'}
          </button>
        </div>
      )}
    </div>
  );
}
