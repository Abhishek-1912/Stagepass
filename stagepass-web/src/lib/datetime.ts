/** Events are scheduled in India Standard Time (UTC+05:30, no daylight saving). */
const IST_OFFSET_MINUTES = 330

/** `'2026-12-20T19:00'` (value of an `<input type="datetime-local">`, read as IST) → ISO string with +05:30. */
export function istInputToIso(value: string): string {
  const withSeconds = value.length === 16 ? `${value}:00` : value.slice(0, 19)
  return `${withSeconds}+05:30`
}

/** Any ISO timestamp → `'YYYY-MM-DDTHH:mm'` in IST, for a datetime-local input. */
export function isoToIstInput(iso: string): string {
  const ms = new Date(iso).getTime() + IST_OFFSET_MINUTES * 60_000
  return new Date(ms).toISOString().slice(0, 16)
}

const dateFormatter = new Intl.DateTimeFormat('en-IN', {
  timeZone: 'Asia/Kolkata',
  weekday: 'short',
  day: 'numeric',
  month: 'short',
  year: 'numeric',
})

const timeFormatter = new Intl.DateTimeFormat('en-IN', {
  timeZone: 'Asia/Kolkata',
  hour: 'numeric',
  minute: '2-digit',
  hour12: true,
})

/** e.g. `Sun, 20 Dec 2026 · 7:00 PM` */
export function formatEventDate(iso: string): string {
  const date = new Date(iso)
  const parts = dateFormatter.formatToParts(date)
  const get = (type: Intl.DateTimeFormatPartTypes) => parts.find((p) => p.type === type)?.value ?? ''
  const time = timeFormatter.format(date).replace(/\s?(am|pm)$/i, (m) => ` ${m.trim().toUpperCase()}`)
  return `${get('weekday')}, ${get('day')} ${get('month')} ${get('year')} · ${time}`
}
