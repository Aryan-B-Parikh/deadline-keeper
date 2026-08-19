'use client';

import { useState } from 'react';
import { type ExtractedEvent } from '@/lib/api';
import { cn, typeIcon, toLocalDatetimeString } from '@/lib/utils';

interface ExtractionPreviewProps {
  events: ExtractedEvent[];
  clarificationQuestion: string | null;
  onConfirm: (events: ExtractedEvent[]) => void;
  onCancel: () => void;
}

export function ExtractionPreview({ events, clarificationQuestion, onConfirm, onCancel }: ExtractionPreviewProps) {
  const [editedEvents, setEditedEvents] = useState(events);

  const updateEvent = (index: number, field: string, value: string) => {
    const updated = [...editedEvents];
    updated[index] = { ...updated[index], [field]: value };
    setEditedEvents(updated);
  };

  return (
    <div className="bg-white rounded-xl border border-gray-200 p-6">
      <h3 className="text-lg font-semibold text-gray-900 mb-2">Extracted Deadlines</h3>

      {clarificationQuestion && (
        <div className="bg-amber-50 border border-amber-200 text-amber-800 text-sm rounded-lg p-3 mb-4">
          ⚠️ {clarificationQuestion}
        </div>
      )}

      <div className="space-y-4">
        {editedEvents.map((event, i) => (
          <div key={i} className="border border-gray-200 rounded-lg p-4">
            <div className="flex items-center gap-2 mb-3">
              <span>{typeIcon(event.type)}</span>
              <input
                type="text"
                value={event.title}
                onChange={(e) => updateEvent(i, 'title', e.target.value)}
                className="flex-1 font-medium text-gray-900 border-b border-transparent hover:border-gray-300 focus:border-brand-500 outline-none"
              />
              {event.needsClarification && (
                <span className="text-xs text-amber-600 bg-amber-50 px-2 py-0.5 rounded">Needs review</span>
              )}
            </div>

            <div className="grid grid-cols-2 gap-3">
              <div>
                <label className="block text-xs text-gray-500 mb-1">Type</label>
                <select
                  value={event.type}
                  onChange={(e) => updateEvent(i, 'type', e.target.value)}
                  className="w-full text-sm border border-gray-200 rounded px-2 py-1 outline-none focus:border-brand-500"
                >
                  <option value="exam">Exam</option>
                  <option value="submission">Submission</option>
                  <option value="hackathon">Hackathon</option>
                  <option value="other">Other</option>
                </select>
              </div>
              <div className="col-span-2 sm:col-span-1">
                <label className="block text-xs text-gray-500 mb-1">Due At</label>
                <input
                  type="datetime-local"
                  value={toLocalDatetimeString(event.dueAt)}
                  onChange={(e) => {
                    const localDt = e.target.value;
                    if (localDt) {
                       const iso = new Date(localDt).toISOString();
                       updateEvent(i, 'dueAt', iso);
                    }
                  }}
                  className="w-full text-sm border border-gray-200 rounded px-2 py-1 outline-none focus:border-brand-500"
                />
              </div>
              <div className="col-span-2 sm:col-span-1">
                <label className="block text-xs text-gray-500 mb-1">Timezone</label>
                <input
                  type="text"
                  value={event.timezone || ''}
                  onChange={(e) => updateEvent(i, 'timezone', e.target.value)}
                  placeholder="UTC"
                  className="w-full text-sm border border-gray-200 rounded px-2 py-1 outline-none focus:border-brand-500"
                />
              </div>
            </div>

            <div className="mt-2">
              <span className={cn(
                'text-xs px-2 py-0.5 rounded-full font-medium',
                event.aiConfidence >= 0.7 ? 'bg-green-100 text-green-700' : 'bg-amber-100 text-amber-700'
              )}>
                Confidence: {Math.round(event.aiConfidence * 100)}%
              </span>
            </div>
          </div>
        ))}
      </div>

      <div className="flex gap-3 mt-6">
        <button
          onClick={() => onConfirm(editedEvents)}
          className="flex-1 bg-brand-600 text-white py-2 px-4 rounded-lg hover:bg-brand-700 transition-colors font-medium"
        >
          Confirm & Save
        </button>
        <button
          onClick={onCancel}
          className="px-4 py-2 border border-gray-300 text-gray-700 rounded-lg hover:bg-gray-50 transition-colors font-medium"
        >
          Cancel
        </button>
      </div>
    </div>
  );
}
