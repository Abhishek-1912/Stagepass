import { expect, test } from 'vitest'
import { buildIcs } from './calendar'

test('builds a VEVENT with UTC times and escaped text', () => {
  const ics = buildIcs({
    slug: 'sunburn-goa-abc123',
    title: 'Sunburn, Goa; Night',
    startsAt: '2026-12-20T19:00:00+05:30',
    endsAt: '2026-12-21T00:30:00+05:30',
    location: 'Vagator Beach, Goa',
    url: 'https://stagepass.example/events/sunburn-goa-abc123',
    description: 'Line one\nLine two',
  })

  expect(ics).toContain('BEGIN:VCALENDAR')
  expect(ics).toContain('DTSTART:20261220T133000Z')
  expect(ics).toContain('DTEND:20261220T190000Z')
  expect(ics).toContain('SUMMARY:Sunburn\\, Goa\\; Night')
  expect(ics).toContain('LOCATION:Vagator Beach\\, Goa')
  expect(ics).toContain('DESCRIPTION:Line one\\nLine two')
  expect(ics).toContain('UID:sunburn-goa-abc123@stagepass')
  expect(ics.split('\r\n').length).toBeGreaterThan(5)
})
