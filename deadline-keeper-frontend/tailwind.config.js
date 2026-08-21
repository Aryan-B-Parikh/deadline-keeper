/** @type {import('tailwindcss').Config} */
module.exports = {
  content: ['./src/**/*.{js,ts,jsx,tsx,mdx}'],
  theme: {
    extend: {
      colors: {
        background: 'rgb(var(--background) / <alpha-value>)',
        foreground: 'rgb(var(--foreground) / <alpha-value>)',
        surface: {
          DEFAULT: 'rgb(var(--surface) / <alpha-value>)',
          hover: 'rgb(var(--surface-hover) / <alpha-value>)',
          elevated: 'rgb(var(--surface-elevated) / <alpha-value>)',
          glass: 'rgb(var(--surface-glass) / <alpha-value>)',
        },
        text: {
          primary: 'rgb(var(--text-primary) / <alpha-value>)',
          secondary: 'rgb(var(--text-secondary) / <alpha-value>)',
          muted: 'rgb(var(--text-muted) / <alpha-value>)',
        },
        border: {
          subtle: 'rgb(var(--border-subtle) / <alpha-value>)',
          strong: 'rgb(var(--border-strong) / <alpha-value>)',
          glass: 'rgb(var(--border-glass) / <alpha-value>)',
        },
        brand: {
          DEFAULT: 'rgb(var(--primary) / <alpha-value>)',
          hover: 'rgb(var(--primary-hover) / <alpha-value>)',
          soft: 'rgb(var(--primary-soft) / <alpha-value>)',
          50: '#f5efff',
          100: '#ede3ff',
          200: '#dcc7ff',
          300: '#c4a7ff',
          400: '#a978ff',
          500: '#8b5cf6',
          600: '#6d28d9',
          700: '#5b21b6',
          800: '#4c1d95',
          900: '#3b0764',
        },
        clay: {
          peach: 'rgb(var(--peach) / <alpha-value>)',
          blue: 'rgb(var(--blue) / <alpha-value>)',
          lime: 'rgb(var(--lime) / <alpha-value>)',
          lavender: 'rgb(var(--lavender) / <alpha-value>)',
          pink: 'rgb(var(--pink) / <alpha-value>)',
          mint: 'rgb(var(--mint) / <alpha-value>)',
        },
        status: {
          success: 'rgb(var(--success) / <alpha-value>)',
          warning: 'rgb(var(--warning) / <alpha-value>)',
          danger: 'rgb(var(--danger) / <alpha-value>)',
        }
      },
    },
  },
  plugins: [],
};
