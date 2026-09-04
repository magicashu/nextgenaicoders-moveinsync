from __future__ import annotations

from datetime import UTC, datetime

from src.data.adapter import SyntheticDataAdapter
from src.models.contracts import (
    ActionProposal, Anomaly, Dashboard, DecisionBrief, EvidencePackage, ExecutionReceipt,
    InvestigationResult, MetricEvidence,
    NetworkMap, NetworkMapPoint,
)


class MockServices:
    """Stable REST-shaped development responses; no frontend business calculations."""

    data_source_label = "Synthetic development dataset · DATA-MOCK-V1"

    def __init__(self) -> None:
        self.adapter = SyntheticDataAdapter()
        self.profile = self.adapter.profile()
        self._proposal = ActionProposal(
            "ACT-MOCK-001", "Initiate vendor recovery review", ["VENDOR-B", "ROUTE-R17"],
            "Vendor B and Route R17 are associated with the OTA gap in the reviewed period.",
            ["EV-001", "EV-002"], "Recover OTA toward the 90% reference after validated remediation.",
            "Operational disruption if route or vendor capacity changes are made without confirmation.", None, "pending",
        )

    def get_dashboard(self) -> Dashboard:
        issue = Anomaly("ANOM-001", "On-time arrival is below the reviewed reference", "high", "on_time_arrival",
                        78.0, 90.0, "SLA reference", -12.0, 1240, "medium", ["EV-001", "EV-002"],
                        ["Synthetic development scenario; validate against organizer data before use."])
        return Dashboard("RUN-MOCK-001", "DATA-MOCK-V1", issue,
            [{"label": "On-time arrival", "value": "78%", "context": "SLA 90%", "state": "alert"},
             {"label": "Previous period", "value": "85%", "context": "7 pp decline", "state": "alert"},
             {"label": "Cancellation rate", "value": None, "context": "Unavailable in current response", "state": "unavailable"},
             {"label": "Total trips", "value": "1,240", "context": "Reviewed population", "state": "normal"}],
            [{"period": "Week 1", "ota": 85}, {"period": "Week 2", "ota": 83}, {"period": "Week 3", "ota": 80}, {"period": "Week 4", "ota": 78}],
            [{"name": "Vendor B", "value": "61% OTA", "note": "Largest observed gap"}, {"name": "Route R17", "value": "High delay", "note": "Needs validation"}, {"name": "Evening shift", "value": "Degraded", "note": "Observed segment"}],
            ["High: OTA below reference", "Partial: cancellation rate unavailable"],
            [{"agent": "A1 Supervisor", "status": "Plan complete"}, {"agent": "A2 Investigator", "status": "Evidence gathered"}, {"agent": "A3 Evidence Critic", "status": "Caveat recorded"}])

    def get_network_map(self) -> NetworkMap:
        samples = self.adapter.location_samples()
        if not samples:
            return NetworkMap(False, "GPS/location data is unavailable in the current source.", [], [], [], [])
        points = [
            NetworkMapPoint(f"GPS-{index:03d}", float(sample["latitude"]), float(sample["longitude"]), "vehicle", str(sample["trip_id"]), "medium", "GPS / DATA QUALITY", "Affected operating area")
            for index, sample in enumerate(samples, start=1)
        ]
        anchor = samples[0]
        points.append(NetworkMapPoint("ANOM-MAP-001", float(anchor["latitude"]), float(anchor["longitude"]), "anomaly", "Reported priority anomaly", "high", "SLA / PERFORMANCE", "Affected operating area"))
        return NetworkMap(True, None, points, ["high", "medium"], ["GPS / DATA QUALITY", "SLA / PERFORMANCE"], ["Affected operating area"])

    def get_change_signals(self) -> list[dict[str, str]]:
        """Backend-shaped comparisons; the page only formats these values."""
        return [
            {"label": "On-time arrival", "change": "7 pp deterioration", "evidence": "EV-001"},
            {"label": "GPS coverage", "change": "Partial coverage noted", "evidence": "EV-002"},
        ]

    def get_investigation(self, issue_id: str) -> tuple[InvestigationResult, EvidencePackage]:
        result = InvestigationResult("TASK-MOCK-001", "partial",
            ["OTA is 78% for the reviewed period, versus a 90% reference.", "Vendor B OTA is 61% in the synthetic scenario."],
            ["EV-001", "EV-002"], ["Vendor B OTA = 61%.", "Overall OTA = 78%."],
            ["Vendor B is a major contributor to the observed gap."],
            ["Cancellation rate is unavailable in this response."], ["Confirm whether the Vendor B pattern persists in organizer data."])
        return result, EvidencePackage(result.evidence_ids, result.findings, result.direct_claims, result.inferred_claims, "medium",
            ["Segment association is not causal evidence.", "Synthetic development scenario."])

    def get_evidence(self, evidence_id: str) -> MetricEvidence:
        if evidence_id == "EV-002":
            return MetricEvidence("EV-002", "on_time_arrival", "mock-v1", 61.0, "%", 61.0, 100.0, "Reviewed period", {"vendor": "Vendor B"}, "vendor", {"reference": 90.0, "type": "SLA reference"}, 310, 0.96, ["Synthetic scenario"], "DATA-MOCK-V1")
        return MetricEvidence("EV-001", "on_time_arrival", "mock-v1", 78.0, "%", 78.0, 100.0, "Reviewed period", {"scope": "overall"}, "overall", {"reference": 90.0, "type": "SLA reference"}, 1240, 0.98, ["Synthetic scenario"], "DATA-MOCK-V1")

    def get_recommendations(self) -> list[ActionProposal]: return [self._proposal]

    def approve(self, action_id: str) -> ExecutionReceipt:
        self._proposal.status = "approved"
        return self._receipt(action_id, "approved")

    def reject(self, action_id: str) -> ExecutionReceipt:
        self._proposal.status = "rejected"
        return self._receipt(action_id, "rejected")

    def edit(self, action_id: str, changes: str) -> ExecutionReceipt:
        self._proposal.status = "edited"
        return self._receipt(action_id, "edited")

    def _receipt(self, action_id: str, status: str) -> ExecutionReceipt:
        return ExecutionReceipt(action_id, status, datetime.now(UTC).isoformat(), "IDEMP-MOCK-001", "AUD-MOCK-001")

    def get_brief(self) -> DecisionBrief:
        return DecisionBrief("Leadership brief: OTA recovery review", "On-time arrival is 78% in the reviewed synthetic scenario, 12 percentage points below the 90% reference.", ["Vendor B recorded 61% OTA.", "Route R17 and evening shifts warrant validation."], self._proposal.action_type, self._proposal.expected_effect, self._proposal.risk, "medium", ["Synthetic scenario; associations are not causal claims."], ["EV-001", "EV-002"])
