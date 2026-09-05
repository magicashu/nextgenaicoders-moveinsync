import { useState, useRef, useEffect } from 'react'

interface DateRange {
  from: string
  to: string
}

interface Props {
  value: DateRange
  onChange: (range: DateRange) => void
}

function getDaysInMonth(year: number, month: number) {
  return new Date(year, month + 1, 0).getDate()
}

function getFirstDayOfMonth(year: number, month: number) {
  return new Date(year, month, 1).getDay()
}

function fmt(d: Date) {
  return d.toISOString().slice(0, 10)
}

function parseDate(s: string): Date | null {
  if (!s) return null
  const d = new Date(s + 'T00:00:00')
  return isNaN(d.getTime()) ? null : d
}

function isoToDate(s: string) {
  return new Date(s + 'T00:00:00')
}

const MONTH_NAMES = ['January','February','March','April','May','June','July','August','September','October','November','December']
const DAY_NAMES = ['S','M','T','W','T','F','S']

function CalendarMonth({
  year, month, selecting, from, to,
  onDayClick, onDayHover,
}: {
  year: number
  month: number
  selecting: 'from' | 'to'
  from: string
  to: string
  onDayClick: (d: string) => void
  onDayHover: (d: string) => void
}) {
  const daysInMonth = getDaysInMonth(year, month)
  const firstDay = getFirstDayOfMonth(year, month)
  const cells: (number | null)[] = Array(firstDay).fill(null)
  for (let i = 1; i <= daysInMonth; i++) cells.push(i)
  while (cells.length % 7 !== 0) cells.push(null)

  const today = fmt(new Date())

  function dayStr(d: number) {
    return `${year}-${String(month + 1).padStart(2, '0')}-${String(d).padStart(2, '0')}`
  }

  function classFor(d: number | null) {
    if (!d) return 'cal-cell empty'
    const s = dayStr(d)
    const fromD = parseDate(from)
    const toD = parseDate(to)
    const cur = isoToDate(s)
    let cls = 'cal-cell'
    if (s === today) cls += ' today'
    if (s === from) cls += ' range-start'
    if (s === to) cls += ' range-end'
    if (fromD && toD && cur > fromD && cur < toD) cls += ' in-range'
    return cls
  }

  // week numbers
  const weeks: number[] = []
  for (let i = 0; i < cells.length; i += 7) {
    const first = cells.slice(i, i + 7).find(c => c !== null)
    if (first) {
      const d = new Date(year, month, first as number)
      const jan1 = new Date(d.getFullYear(), 0, 1)
      const week = Math.ceil(((d.getTime() - jan1.getTime()) / 86400000 + jan1.getDay() + 1) / 7)
      weeks.push(week)
    } else {
      weeks.push(0)
    }
  }

  return (
    <div className="cal-month">
      <div className="cal-month-title">
        <span>{MONTH_NAMES[month]}</span>
        <span style={{ marginLeft: 6, color: 'var(--mis-blue)' }}>{year}</span>
      </div>
      <div className="cal-grid">
        <div className="cal-week-num" />
        {DAY_NAMES.map((d, i) => (
          <div key={i} className="cal-day-name">{d}</div>
        ))}
        {cells.map((day, i) => {
          const weekIdx = Math.floor(i / 7)
          const isFirstInRow = i % 7 === 0
          return [
            isFirstInRow && <div key={`w${weekIdx}`} className="cal-week-num">{weeks[weekIdx] || ''}</div>,
            <div
              key={i}
              className={classFor(day)}
              onClick={() => day && onDayClick(dayStr(day))}
              onMouseEnter={() => day && onDayHover(dayStr(day))}
            >
              {day || ''}
            </div>
          ]
        })}
      </div>
    </div>
  )
}

export function DateRangePicker({ value, onChange }: Props) {
  const [open, setOpen] = useState(false)
  const [selecting, setSelecting] = useState<'from' | 'to'>('from')
  const [hoverDate, setHoverDate] = useState('')
  const [leftYear, setLeftYear] = useState(() => {
    const d = parseDate(value.from)
    return d ? d.getFullYear() : new Date().getFullYear()
  })
  const [leftMonth, setLeftMonth] = useState(() => {
    const d = parseDate(value.from)
    return d ? d.getMonth() : new Date().getMonth()
  })
  const ref = useRef<HTMLDivElement>(null)

  // Right calendar is always one month ahead
  const rightMonth = (leftMonth + 1) % 12
  const rightYear = leftMonth === 11 ? leftYear + 1 : leftYear

  useEffect(() => {
    function handleClick(e: MouseEvent) {
      if (ref.current && !ref.current.contains(e.target as Node)) setOpen(false)
    }
    document.addEventListener('mousedown', handleClick)
    return () => document.removeEventListener('mousedown', handleClick)
  }, [])

  function handleDayClick(d: string) {
    if (selecting === 'from') {
      onChange({ from: d, to: d })
      setSelecting('to')
    } else {
      if (d < value.from) {
        onChange({ from: d, to: value.from })
      } else {
        onChange({ from: value.from, to: d })
      }
      setSelecting('from')
      setOpen(false)
    }
  }

  function handleToday() {
    const t = fmt(new Date())
    onChange({ from: t, to: t })
    setSelecting('from')
    setOpen(false)
  }

  function prevMonth() {
    if (leftMonth === 0) { setLeftMonth(11); setLeftYear(y => y - 1) }
    else setLeftMonth(m => m - 1)
  }

  function nextMonth() {
    if (leftMonth === 11) { setLeftMonth(0); setLeftYear(y => y + 1) }
    else setLeftMonth(m => m + 1)
  }

  const displayFrom = value.from || 'Start date'
  const displayTo = value.to || 'End date'

  // For hover preview
  const previewTo = selecting === 'to' && hoverDate ? hoverDate : value.to

  return (
    <div className="drp-wrapper" ref={ref}>
      <button
        className="drp-trigger"
        type="button"
        onClick={() => setOpen(o => !o)}
      >
        <svg width="13" height="13" viewBox="0 0 14 14" fill="none">
          <rect x="1" y="2" width="12" height="11" rx="2" stroke="currentColor" strokeWidth="1.3"/>
          <path d="M1 6h12" stroke="currentColor" strokeWidth="1.3"/>
          <path d="M4 1v2M10 1v2" stroke="currentColor" strokeWidth="1.3" strokeLinecap="round"/>
        </svg>
        <span>{displayFrom === displayTo ? displayFrom : `${displayFrom} → ${displayTo}`}</span>
        <svg width="10" height="10" viewBox="0 0 10 10" fill="none" style={{ opacity: 0.5 }}>
          <path d="M2 3.5l3 3 3-3" stroke="currentColor" strokeWidth="1.4" strokeLinecap="round"/>
        </svg>
      </button>

      {open && (
        <div className="drp-popover">
          <div className="drp-nav">
            <button className="drp-nav-btn" onClick={prevMonth} type="button">‹</button>
            <div className="drp-months">
              <CalendarMonth
                year={leftYear} month={leftMonth}
                selecting={selecting} from={value.from} to={previewTo}
                onDayClick={handleDayClick} onDayHover={setHoverDate}
              />
              <div className="drp-divider" />
              <CalendarMonth
                year={rightYear} month={rightMonth}
                selecting={selecting} from={value.from} to={previewTo}
                onDayClick={handleDayClick} onDayHover={setHoverDate}
              />
            </div>
            <button className="drp-nav-btn" onClick={nextMonth} type="button">›</button>
          </div>
          <div className="drp-footer">
            <button className="drp-today-btn" type="button" onClick={handleToday}>Today</button>
            <button className="drp-today-btn" type="button" onClick={handleToday}>Today</button>
          </div>
        </div>
      )}
    </div>
  )
}
