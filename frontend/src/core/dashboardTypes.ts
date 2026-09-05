import type { MetricResult } from './contracts'
export type RankingRow = { member: string; currentValue: number | null; baselineValue: number | null; delta: number | null; currentNumerator: number; currentDenominator: number; shareOfCurrentNumerator: number; qualified: boolean }
export type Ranking = { rows: RankingRow[]; caveats: string[]; evidenceId: string; source: string }
export type Charts = { metric: MetricResult; onTime: MetricResult; vendors: Ranking; sites: Ranking; shifts: Ranking; noShow: MetricResult; cost: MetricResult; trend: { points: { date: string; value: number | null }[]; source: string } }
