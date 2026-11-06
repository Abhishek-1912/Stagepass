/** Minimal RFC 5545 calendar file for "Add to calendar". */
export interface CalendarEvent {
  slug: string
  title: string
  startsAt: string
  endsAt: string
  location?: string
  url: string
  description?: string
}

function utc(iso: string): string {
  return new Date(iso).toISOString().replace(/[-:]/g, '').replace(/\.\d{3}/, '')
}

function text(value: string): string {
  return value.replace(/\\/g, '\\\\').replace(/;/g, '\\;').replace(/,/g, '\\,').replace(/\r?\n/g, '\\n')
}

export function buildIcs(e: CalendarEvent): string {
  const lines = [
    'BEGIN:VCALENDAR',
    'VERSION:2.0',
    'PRODID:-//Stagepass//Events//EN',
    'CALSCALE:GREGORIAN',
    'BEGIN:VEVENT',
    `UID:${e.slug}@stagepass`,
    `DTSTAMP:${utc(new Date().toISOString())}`,
    `DTSTART:${utc(e.startsAt)}`,
    `DTEND:${utc(e.endsAt)}`,
    `SUMMARY:${text(e.title)}`,
    e.location ? `LOCATION:${text(e.location)}` : null,
    e.description ? `DESCRIPTION:${text(e.description)}` : null,
    `URL:${e.url}`,
    'END:VEVENT',
    'END:VCALENDAR',
  ].filter(Boolean)
  return `${lines.join('\r\n')}\r\n`
}

export function downloadIcs(e: CalendarEvent) {
  const blob = new Blob([buildIcs(e)], { type: 'text/calendar;charset=utf-8' })
  const href = URL.createObjectURL(blob)
  const a = document.createElement('a')
  a.href = href
  a.download = `${e.slug}.ics`
  a.click()
  URL.revokeObjectURL(href)
}
