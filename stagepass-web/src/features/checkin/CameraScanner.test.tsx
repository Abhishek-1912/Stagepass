import { act, render } from '@testing-library/react'
import { afterEach, beforeEach, expect, test, vi } from 'vitest'

type OnSuccess = (text: string) => void

const lib = vi.hoisted(() => {
  const stop = vi.fn<() => Promise<void>>(() => Promise.resolve())
  const clear = vi.fn<() => void>()
  const state: { onSuccess?: OnSuccess; resolveStart?: () => void; stop: typeof stop; clear: typeof clear; scanning: boolean } = {
    stop,
    clear,
    scanning: false,
  }
  class Html5Qrcode {
    get isScanning() {
      return state.scanning
    }
    start(_camera: unknown, _config: unknown, onSuccess: OnSuccess) {
      state.onSuccess = onSuccess
      return new Promise<void>((resolve) => {
        state.resolveStart = () => {
          state.scanning = true
          resolve()
        }
      })
    }
    stop() {
      state.scanning = false
      return state.stop()
    }
    clear() {
      state.clear()
    }
  }
  return { state, Html5Qrcode }
})
vi.mock('html5-qrcode', () => ({ Html5Qrcode: lib.Html5Qrcode }))

import { CameraScanner } from './CameraScanner'

beforeEach(() => {
  lib.state.stop.mockClear()
  lib.state.scanning = false
  lib.state.onSuccess = undefined
})
afterEach(() => vi.useRealTimers())

test('camera started after leaving the page is still stopped', async () => {
  const { unmount } = render(<CameraScanner onCode={vi.fn()} paused={false} />)
  unmount() // e.g. gate changed or navigated away while the permission prompt was open
  await act(async () => {
    lib.state.resolveStart?.()
  })
  expect(lib.state.stop).toHaveBeenCalledTimes(1)
})

test('a ticket kept in view is not scanned again after its result clears', async () => {
  vi.useFakeTimers()
  const onCode = vi.fn()
  const { rerender } = render(<CameraScanner onCode={onCode} paused={false} />)
  await act(async () => {
    lib.state.resolveStart?.()
  })

  lib.state.onSuccess?.('tok')
  expect(onCode).toHaveBeenCalledTimes(1)
  rerender(<CameraScanner onCode={onCode} paused />)
  for (let t = 0; t < 25; t++) {
    vi.advanceTimersByTime(100) // result on screen for 2.5 s; the camera keeps seeing the same ticket
    lib.state.onSuccess?.('tok')
  }
  rerender(<CameraScanner onCode={onCode} paused={false} />)
  vi.advanceTimersByTime(500)
  lib.state.onSuccess?.('tok')
  expect(onCode).toHaveBeenCalledTimes(1)

  lib.state.onSuccess?.('another')
  expect(onCode).toHaveBeenCalledTimes(2)
})
