import { Component, type ReactNode } from 'react'
import { Button } from '@/components/ui/button'

const CHUNK_ERROR = /dynamically imported module|Importing a module script failed|Loading chunk|error loading dynamically imported/i

interface Props {
  children: ReactNode
  /** Defaults to a full page reload. */
  onReload?: () => void
}

/**
 * Catches a lazy page whose code file is gone (a new version was deployed while the tab was open) and offers a
 * reload. Any other error is rethrown to the next boundary.
 */
export class LazyBoundary extends Component<Props, { error: Error | null }> {
  state = { error: null as Error | null }

  static getDerivedStateFromError(error: Error) {
    return { error }
  }

  render() {
    const { error } = this.state
    if (!error) return this.props.children
    if (!CHUNK_ERROR.test(error.message)) throw error
    return (
      <div className="mx-auto max-w-md space-y-4 px-4 py-20 text-center">
        <h1 className="text-2xl font-bold">A new version of Stagepass is available</h1>
        <p className="text-muted-foreground">Reload the page to continue.</p>
        <Button onClick={() => (this.props.onReload ?? (() => window.location.reload()))()}>Reload</Button>
      </div>
    )
  }
}
