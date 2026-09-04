import { render, screen } from '@testing-library/react'
import { describe, expect, it, vi } from 'vitest'
import { MorningBriefPage } from './MorningBriefPage'

vi.mock('../../core/api', async () => {
  const actual = await vi.importActual<typeof import('../../core/api')>('../../core/api')
  return { ...actual, fetchDemoBrief: async () => actual.fixtureBrief }
})

describe('MorningBriefPage', () => {
  it('renders a governed metric and approval boundary', async () => {
    render(<MorningBriefPage />)
    expect(await screen.findByText(/delayed-trip rate increased/i)).toBeInTheDocument()
    expect(screen.getByRole('button', { name: /review before approval/i })).toBeDisabled()
  })
})
