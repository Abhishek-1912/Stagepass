import { Globe } from 'lucide-react'
import { useEffect } from 'react'
import { Link, useParams } from 'react-router'
import { EventCard } from '@/components/events/EventCard'
import { PageLoader } from '@/components/feedback/PageLoader'
import { Button } from '@/components/ui/button'
import { usePublicOrganizer } from '@/features/discovery/api'
import { cloudinary } from '@/lib/images'

export function OrganizerProfilePage() {
  const { slug } = useParams()
  const { data, isPending } = usePublicOrganizer(slug)

  useEffect(() => {
    if (data) document.title = `${data.organizer.name} · Stagepass`
  }, [data])

  if (isPending) return <PageLoader />
  if (!data) {
    return (
      <div className="mx-auto max-w-md space-y-4 px-4 py-24 text-center">
        <h1 className="text-2xl font-bold">Organizer not found</h1>
        <Button asChild className="rounded-full">
          <Link to="/explore">Explore events</Link>
        </Button>
      </div>
    )
  }

  const { organizer, upcoming, pastEvents } = data
  return (
    <div className="mx-auto max-w-7xl space-y-10 px-4 py-12 sm:px-6">
      <header className="flex flex-wrap items-center gap-5">
        {organizer.logoUrl ? (
          <img src={cloudinary(organizer.logoUrl, 'f_auto,q_auto,w_200')} alt="" className="size-20 rounded-2xl object-cover" />
        ) : (
          <span className="bg-gradient-brand grid size-20 place-items-center rounded-2xl text-3xl font-bold text-white">{organizer.name[0]}</span>
        )}
        <div className="min-w-0 flex-1 space-y-1">
          <h1 className="text-3xl font-bold">{organizer.name}</h1>
          {organizer.bio && <p className="max-w-2xl text-muted-foreground">{organizer.bio}</p>}
          <p className="text-sm text-muted-foreground">
            {upcoming.length} upcoming · {pastEvents} past {pastEvents === 1 ? 'event' : 'events'}
          </p>
        </div>
        {organizer.website && (
          <Button asChild variant="outline" className="rounded-full">
            <a href={organizer.website} target="_blank" rel="noopener noreferrer nofollow">
              <Globe /> Website
            </a>
          </Button>
        )}
      </header>
      <section className="space-y-4">
        <h2 className="text-2xl font-bold">Upcoming events</h2>
        {upcoming.length ? (
          <div className="grid gap-5 sm:grid-cols-2 lg:grid-cols-3">
            {upcoming.map((e) => (
              <EventCard key={e.id} event={e} />
            ))}
          </div>
        ) : (
          <p className="rounded-2xl border border-dashed border-border p-8 text-center text-muted-foreground">
            No upcoming events right now.
          </p>
        )}
      </section>
    </div>
  )
}
