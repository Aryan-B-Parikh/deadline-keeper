'use client';

import { useState } from 'react';
import { type ExtractedEvent } from '@/lib/api';
import { cn, typeIcon, toLocalDatetimeString } from '@/lib/utils';
import { Sparkles, AlertCircle, CheckCircle2 } from 'lucide-react';

interface ExtractionPreviewProps {
  events: ExtractedEvent[];
  clarificationQuestion: string | null;
  onConfirm: (events: ExtractedEvent[]) => void;
  onCancel: () => void;
}

export function ExtractionPreview({ events, clarificationQuestion, onConfirm, onCancel }: ExtractionPreviewProps) {
  const [editedEvents, setEditedEvents] = useState(events);

  const updateEvent = (index: number, field: keyof ExtractedEvent, value: string) => {
    setEditedEvents((current) => current.map((event, eventIndex) =>
      eventIndex === index ? { ...event, [field]: value } : event
    ));
  };

  const inputClasses = "w-full text-sm bg-surface-elevated border border-border-strong rounded-xl px-3 py-2 text-text-primary placeholder:text-text-muted outline-none focus:ring-2 focus:ring-brand/20 focus:border-brand transition-all shadow-sm";
  const labelClasses = "block text-xs font-medium text-text-secondary mb-1.5 ml-1";

  return (
    <div className="bg-surface border border-border-subtle rounded-2xl p-6 sm:p-8 shadow-sm animate-in fade-in slide-in-from-bottom-4 duration-500">
      <div className="flex items-center gap-3 mb-6">
        <div className="w-10 h-10 rounded-xl bg-brand/10 text-brand flex items-center justify-center">
          <Sparkles className="w-5 h-5" />
        </div>
        <div>
          <h3 className="text-xl font-bold text-text-primary tracking-tight">AI Extracted Deadlines</h3>
          <p className="text-sm text-text-secondary">Review and edit before saving</p>
        </div>
      </div>

      {clarificationQuestion && (
        <div className="flex gap-3 bg-warning/10 border border-warning/20 text-warning text-sm rounded-xl p-4 mb-6 shadow-sm">
          <AlertCircle className="w-5 h-5 flex-shrink-0 mt-0.5" />
          <p className="leading-relaxed">{clarificationQuestion}</p>
        </div>
      )}

      <div className="space-y-4">
        {editedEvents.map((event, i) => {
          const confidence = event.aiConfidence;
          const confidenceLabel = confidence === null ? 'Not available' : `${Math.round(confidence * 100)}%`;

          return (
            <div key={`${event.title}-${i}`} className="bg-surface-hover/50 border border-border-subtle rounded-xl p-5 hover:border-border-strong transition-colors">
              <div className="flex items-center gap-3 mb-4">
                <div className="w-9 h-9 rounded-lg bg-surface border border-border-subtle flex items-center justify-center flex-shrink-0">
                  <span aria-hidden="true" className="text-lg">{typeIcon(event.type)}</span>
                </div>
                <input
                  type="text"
                  value={event.title}
                  onChange={(e) => updateEvent(i, 'title', e.target.value)}
                  aria-label={`Extracted event ${i + 1} title`}
                  className="flex-1 font-semibold text-text-primary bg-transparent border-b border-transparent hover:border-border-strong focus:border-brand outline-none transition-colors px-1 py-0.5 rounded-none"
                />
                {event.needsClarification && (
                  <span className="flex items-center gap-1 text-[11px] font-medium text-warning bg-warning/10 px-2 py-1 rounded-md flex-shrink-0">
                    <AlertCircle className="w-3 h-3" /> Review
                  </span>
                )}
              </div>

              <div className="grid grid-cols-2 gap-4">
                <div>
                  <label htmlFor={`event-type-${i}`} className={labelClasses}>Type</label>
                  <select
                    id={`event-type-${i}`}
                    value={event.type}
                    onChange={(e) => updateEvent(i, 'type', e.target.value)}
                    className={inputClasses}
                  >
                    <option value="exam">Exam</option>
                    <option value="submission">Submission</option>
                    <option value="hackathon">Hackathon</option>
                    <option value="other">Other</option>
                  </select>
                </div>
                <div className="col-span-2 sm:col-span-1">
                  <label htmlFor={`event-due-${i}`} className={labelClasses}>Due At</label>
                  <input
                    id={`event-due-${i}`}
                    type="datetime-local"
                    value={toLocalDatetimeString(event.dueAt)}
                    onChange={(e) => {
                      const localDt = e.target.value;
                      if (localDt) updateEvent(i, 'dueAt', new Date(localDt).toISOString());
                    }}
                    className={inputClasses}
                  />
                </div>
                <div className="col-span-2 sm:col-span-1">
                  <label htmlFor={`event-timezone-${i}`} className={labelClasses}>Timezone</label>
                  <input
                    id={`event-timezone-${i}`}
                    type="text"
                    value={event.timezone || ''}
                    onChange={(e) => updateEvent(i, 'timezone', e.target.value)}
                    placeholder="UTC"
                    className={inputClasses}
                  />
                </div>
              </div>

              <div className="mt-4 pt-4 border-t border-border-subtle flex items-center justify-between">
                <span className={cn(
                  'flex items-center gap-1.5 text-xs font-medium',
                  confidence === null ? 'text-text-secondary' : confidence >= 0.7 ? 'text-success' : 'text-warning'
                )}>
                  {confidence !== null && confidence >= 0.7 ? <CheckCircle2 className="w-3.5 h-3.5" /> : <AlertCircle className="w-3.5 h-3.5" />}
                  AI Confidence: {confidenceLabel}
                </span>
              </div>
            </div>
          );
        })}
      </div>

      <div className="flex flex-col-reverse sm:flex-row justify-end gap-3 mt-8 pt-6 border-t border-border-subtle">
        <button
          type="button"
          onClick={onCancel}
          className="px-5 py-2.5 border border-border-strong text-text-primary rounded-xl hover:bg-surface-hover transition-colors font-medium"
        >
          Cancel
        </button>
        <button
          type="button"
          onClick={() => onConfirm(editedEvents)}
          className="px-6 py-2.5 bg-brand text-white rounded-xl hover:bg-brand-hover shadow-sm hover:shadow transition-all font-medium flex items-center justify-center gap-2"
        >
          <CheckCircle2 className="w-4 h-4" />
          Confirm & Save
        </button>
      </div>
    </div>
  );
}
