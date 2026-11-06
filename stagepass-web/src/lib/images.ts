/** Royalty-free placeholder photography (Unsplash license) used until real event images exist. */
const unsplash = (id: string, width = 1600) =>
  `https://images.unsplash.com/${id}?auto=format&fit=crop&w=${width}&q=75`

export const placeholderImages = {
  heroConcert: unsplash('photo-1470229722913-7c0e2dbbafd3'),
  festivalCrowd: unsplash('photo-1501281668745-f7f57925c3b4'),
  conference: unsplash('photo-1540575467063-178a50c2df87'),
  stageLights: unsplash('photo-1514525253161-7a46d19cd819'),
  partyConfetti: unsplash('photo-1492684223066-81342ee5ff30'),
  liveBand: unsplash('photo-1459749411175-04bf5292ceea'),
}

/** Inserts a Cloudinary transformation (e.g. `f_auto,q_auto,w_640`) after `/upload/`; other URLs pass through. */
export function cloudinary(url: string | null | undefined, transform: string): string | undefined {
  if (!url) return undefined
  return url.includes('/image/upload/') ? url.replace('/image/upload/', `/image/upload/${transform}/`) : url
}
