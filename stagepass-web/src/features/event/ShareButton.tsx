import { Copy, Share2 } from 'lucide-react'
import { toast } from 'sonner'
import { Button } from '@/components/ui/button'
import {
  DropdownMenu,
  DropdownMenuContent,
  DropdownMenuItem,
  DropdownMenuSeparator,
  DropdownMenuTrigger,
} from '@/components/ui/dropdown-menu'

async function copy(url: string) {
  try {
    await navigator.clipboard.writeText(url)
    toast.success('Link copied')
  } catch {
    toast.error('Couldn’t copy the link')
  }
}

/** Share menu: WhatsApp, X and Copy link everywhere, plus the system share sheet where the browser has one. */
export function ShareButton({ url, title, className }: { url: string; title: string; className?: string }) {
  const canShare = typeof navigator !== 'undefined' && typeof navigator.share === 'function'
  const message = encodeURIComponent(`${title} ${url}`)
  return (
    <DropdownMenu>
      <DropdownMenuTrigger asChild>
        <Button variant="outline" className={className}>
          <Share2 /> Share
        </Button>
      </DropdownMenuTrigger>
      <DropdownMenuContent align="end" className="w-48">
        <DropdownMenuItem asChild>
          <a href={`https://wa.me/?text=${message}`} target="_blank" rel="noopener noreferrer">
            WhatsApp
          </a>
        </DropdownMenuItem>
        <DropdownMenuItem asChild>
          <a
            href={`https://twitter.com/intent/tweet?url=${encodeURIComponent(url)}&text=${encodeURIComponent(title)}`}
            target="_blank"
            rel="noopener noreferrer"
          >
            X (Twitter)
          </a>
        </DropdownMenuItem>
        <DropdownMenuItem onSelect={() => copy(url)}>
          <Copy /> Copy link
        </DropdownMenuItem>
        {canShare && (
          <>
            <DropdownMenuSeparator />
            <DropdownMenuItem
              onSelect={() => navigator.share({ title, text: `${title} on Stagepass`, url }).catch(() => {})}
            >
              <Share2 /> More options…
            </DropdownMenuItem>
          </>
        )}
      </DropdownMenuContent>
    </DropdownMenu>
  )
}
