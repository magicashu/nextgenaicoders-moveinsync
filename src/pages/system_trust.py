from __future__ import annotations

import streamlit as st

from src.components.ui import page_heading
from src.services.mock_services import MockServices


def render(service: MockServices) -> None:
    dashboard = service.get_dashboard()
    page_heading("System / Trust", "Run metadata, evidence coverage, and audit readiness")
    st.json({"run_id": dashboard.run_id, "data_version": dashboard.data_version, "metric_version": "mock-v1", "evidence_count": 2, "coverage": "98% overall / 96% Vendor B", "confidence": dashboard.priority_issue.confidence, "latency": "Mock response", "audit_id": "AUD-MOCK-001", "dataset_discovered_files": len(service.profile.csv_files), "dataset_path": str(service.profile.source_path)})
    st.warning("This is a synthetic development source. No mock threshold or scenario should be treated as an official business rule.")
