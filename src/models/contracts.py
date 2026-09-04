from __future__ import annotations

from dataclasses import dataclass, field


@dataclass(frozen=True)
class MetricEvidence:
    evidence_id: str
    metric_id: str
    metric_version: str
    value: float | None
    unit: str
    numerator: float | None
    denominator: float | None
    window: str
    filters: dict[str, str]
    grain: str
    comparison: dict[str, float | str] | None
    population: int | None
    coverage: float | None
    warnings: list[str]
    data_version: str


@dataclass(frozen=True)
class Anomaly:
    anomaly_id: str
    title: str
    severity: str
    metric_id: str
    current_value: float | None
    reference_value: float | None
    reference_type: str | None
    gap: float | None
    affected_population: int | None
    confidence: str
    evidence_ids: list[str]
    caveats: list[str]


@dataclass(frozen=True)
class InvestigationResult:
    task_id: str
    status: str
    findings: list[str]
    evidence_ids: list[str]
    direct_claims: list[str]
    inferred_claims: list[str]
    quality_warnings: list[str]
    unresolved_questions: list[str]


@dataclass(frozen=True)
class EvidencePackage:
    evidence_ids: list[str]
    findings: list[str]
    direct_claims: list[str]
    inferred_claims: list[str]
    confidence: str
    caveats: list[str]


@dataclass
class ActionProposal:
    action_id: str
    action_type: str
    target_ids: list[str]
    reason: str
    evidence_ids: list[str]
    expected_effect: str
    risk: str
    expires_at: str | None
    status: str


@dataclass(frozen=True)
class DecisionBrief:
    title: str
    executive_summary: str
    key_findings: list[str]
    recommendation: str | None
    expected_effect: str | None
    risk: str | None
    confidence: str
    caveats: list[str]
    evidence_ids: list[str]


@dataclass(frozen=True)
class ExecutionReceipt:
    action_id: str
    status: str
    executed_at: str
    idempotency_key: str
    audit_id: str


@dataclass(frozen=True)
class Dashboard:
    run_id: str
    data_version: str
    priority_issue: Anomaly
    kpis: list[dict[str, object]]
    trend: list[dict[str, object]]
    contributors: list[dict[str, object]]
    alerts: list[str]
    agent_activity: list[dict[str, str]]


@dataclass(frozen=True)
class NetworkMapPoint:
    point_id: str
    latitude: float
    longitude: float
    point_type: str
    label: str
    severity: str | None
    category: str | None
    route_label: str | None


@dataclass(frozen=True)
class NetworkMap:
    available: bool
    reason: str | None
    points: list[NetworkMapPoint]
    supported_severities: list[str]
    supported_categories: list[str]
    affected_areas: list[str]
