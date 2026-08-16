'use client';

interface ReminderConfigProps {
  value: string[];
  onChange: (value: string[]) => void;
}

const PRESET_OFFSETS = [
  { label: '7 days before', value: '7d' },
  { label: '3 days before', value: '3d' },
  { label: '1 day before', value: '1d' },
  { label: '2 hours before', value: '2h' },
  { label: '30 min before', value: '30m' },
];

export function ReminderConfig({ value, onChange }: ReminderConfigProps) {
  const toggle = (offset: string) => {
    if (value.includes(offset)) {
      onChange(value.filter((v) => v !== offset));
    } else {
      onChange([...value, offset]);
    }
  };

  return (
    <div className="flex flex-wrap gap-2">
      {PRESET_OFFSETS.map((preset) => (
        <button
          key={preset.value}
          type="button"
          onClick={() => toggle(preset.value)}
          className={`text-sm px-3 py-1.5 rounded-lg border transition-colors ${
            value.includes(preset.value)
              ? 'bg-brand-50 border-brand-300 text-brand-700'
              : 'bg-white border-gray-200 text-gray-600 hover:bg-gray-50'
          }`}
        >
          {preset.label}
        </button>
      ))}
    </div>
  );
}
