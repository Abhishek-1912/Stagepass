import { Moon, Sun } from 'lucide-react'
import { Button } from '@/components/ui/button'
import { useTheme } from './ThemeProvider'

export function ThemeToggle() {
  const { resolved, setTheme } = useTheme()
  const next = resolved === 'dark' ? 'light' : 'dark'

  return (
    <Button
      variant="ghost"
      size="icon"
      aria-label="Toggle theme"
      title={`Switch to ${next} mode`}
      onClick={() => setTheme(next)}
    >
      {resolved === 'dark' ? <Sun className="size-[18px]" /> : <Moon className="size-[18px]" />}
    </Button>
  )
}
