type Props = { evidenceIds: string[]; onOpen: (evidenceId: string) => void }

/** Clickable evidence references. Rendered as text, never as HTML from the model. */
export function EvidenceChips({ evidenceIds, onOpen }: Props) {
  if (evidenceIds.length === 0) return <span className="chip chip--missing">no evidence</span>
  return (
    <span className="chips">
      {evidenceIds.map((id) => (
        <button key={id} type="button" className="chip" onClick={() => onOpen(id)} title={id}>
          {shortEvidence(id)}
        </button>
      ))}
    </span>
  )
}

export function shortEvidence(id: string): string {
  const parts = id.split(':')
  const metric = parts.find((p) => /^m\d\d_/.test(p))
  const tail = parts.slice(3).filter((p) => !/^\d{4}-\d{2}-\d{2}$/.test(p)).join(' · ')
  return `${metric ? metric.slice(0, 3).toUpperCase() : parts[0]}${tail ? ` · ${tail}` : ''}`
}
