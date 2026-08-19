'use client';

import { type ReminderInput } from '@/lib/api';

interface ReminderConfigProps {
  value: ReminderInput[];
  onChange: (value: ReminderInput[]) => void;
}

const PRESET_OFFSETS = [
  { label: '7 days before', value: 604800 },
  { label: '3 days before', value: 259200 },
  { label: '1 day before', value: 86400 },
  { label: '2 hours before', value: 7200 },
  { label: '30 min before', value: 1800 },
];

export function ReminderConfig({ value = [], onChange }: ReminderConfigProps) {
  const toggle = (offsetSeconds: number) => {
    if (value.some((v) => v.offsetSeconds === offsetSeconds)) {
      onChange(value.filter((v) => v.offsetSeconds !== offsetSeconds));
    } else {
      onChange([...value, { offsetSeconds, channel: 'in_app' }]);
    }
  };

  return (
    <div className="flex flex-wrap gap-2">
      {PRESET_OFFSETS.map((preset) => {
        const isActive = value.some((v) => v.offsetSeconds === preset.value);
        return (
          <button
            key={preset.value}
            type="button"
            onClick={() => toggle(preset.value)}
            className={`text-sm px-3 py-1.5 rounded-lg border transition-colors ${
              isActive
                ? 'bg-brand-50 border-brand-300 text-brand-700'
                : 'bg-white border-gray-200 text-gray-600 hover:bg-gray-50'
            }`}
          >
            {preset.label}
          </button>
        );
      })}
    </div>
  );
}
