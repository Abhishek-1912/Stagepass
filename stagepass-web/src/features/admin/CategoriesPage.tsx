import { Plus } from 'lucide-react'
import { useState } from 'react'
import { toast } from 'sonner'
import { PageLoader } from '@/components/feedback/PageLoader'
import { Button } from '@/components/ui/button'
import { isApiError } from '@/lib/api/ApiError'
import type { Category } from '@/lib/api/types'
import { useAdminCategories, useSaveCategory } from './api'

const input =
  'h-9 rounded-lg border border-input bg-background px-2.5 text-sm outline-none focus-visible:ring-[3px] focus-visible:ring-ring/50'

function useSave() {
  const save = useSaveCategory()
  return async (id: string | undefined, changes: Partial<Omit<Category, 'id'>>, message: string) => {
    try {
      await save.mutateAsync({ id, input: changes })
      toast.success(message)
      return true
    } catch (e) {
      toast.error(isApiError(e) ? (e.fieldErrors[0]?.message ?? e.detail) : 'Could not save the category')
      return false
    }
  }
}

function Row({ c }: { c: Category }) {
  const save = useSave()
  const [name, setName] = useState(c.name)
  const [icon, setIcon] = useState(c.icon ?? '')
  const [sort, setSort] = useState(String(c.sortOrder))
  const dirty = name !== c.name || icon !== (c.icon ?? '') || sort !== String(c.sortOrder)

  return (
    <tr className="border-t border-border">
      <td className="p-3"><input aria-label={`${c.name} name`} className={input} value={name} onChange={(e) => setName(e.target.value)} /></td>
      <td className="p-3 font-mono text-xs text-muted-foreground">{c.slug}</td>
      <td className="p-3"><input aria-label={`${c.name} icon`} className={`${input} w-28`} value={icon} onChange={(e) => setIcon(e.target.value)} /></td>
      <td className="p-3"><input aria-label={`${c.name} order`} inputMode="numeric" className={`${input} w-16`} value={sort} onChange={(e) => setSort(e.target.value)} /></td>
      <td className="p-3">
        <label className="inline-flex items-center gap-2 text-sm">
          <input type="checkbox" checked={c.active} onChange={(e) => save(c.id, { active: e.target.checked }, e.target.checked ? 'Category shown' : 'Category hidden')} />
          {c.active ? 'Shown' : 'Hidden'}
        </label>
      </td>
      <td className="p-3 text-right">
        <Button size="sm" variant="outline" disabled={!dirty} onClick={() => save(c.id, { name, icon, sortOrder: Number(sort) }, 'Category saved')}>
          Save
        </Button>
      </td>
    </tr>
  )
}

export function CategoriesPage() {
  const { data, isPending } = useAdminCategories()
  const save = useSave()
  const [name, setName] = useState('')
  const [slug, setSlug] = useState('')

  async function add() {
    if (await save(undefined, { name, slug, sortOrder: (data?.length ?? 0) + 1 }, 'Category added')) {
      setName('')
      setSlug('')
    }
  }

  return (
    <div className="space-y-6">
      <div>
        <h2 className="text-2xl font-bold">Categories</h2>
        <p className="text-muted-foreground">Hide a category instead of deleting it; existing events keep it.</p>
      </div>
      <div className="flex flex-wrap items-end gap-2 rounded-2xl border border-border bg-card p-4">
        <label className="space-y-1 text-sm">
          <span className="block font-medium">Name</span>
          <input className={input} value={name} onChange={(e) => { setName(e.target.value); setSlug(e.target.value.toLowerCase().replace(/[^a-z0-9]+/g, '-').replace(/^-|-$/g, '')) }} />
        </label>
        <label className="space-y-1 text-sm">
          <span className="block font-medium">Slug</span>
          <input className={input} value={slug} onChange={(e) => setSlug(e.target.value)} />
        </label>
        <Button onClick={add} disabled={!name.trim() || !slug.trim()}>
          <Plus /> Add category
        </Button>
      </div>
      {isPending ? (
        <PageLoader />
      ) : (
        <div className="overflow-x-auto rounded-2xl border border-border bg-card">
          <table className="w-full text-left">
            <thead className="text-xs uppercase tracking-wider text-muted-foreground">
              <tr>
                <th className="p-3">Name</th>
                <th className="p-3">Slug</th>
                <th className="p-3">Icon</th>
                <th className="p-3">Order</th>
                <th className="p-3">Visibility</th>
                <th className="p-3" />
              </tr>
            </thead>
            <tbody>{data?.map((c) => <Row key={c.id} c={c} />)}</tbody>
          </table>
        </div>
      )}
    </div>
  )
}
