from __future__ import annotations

import streamlit as st

from src.components.evidence import evidence_drawer
from src.components.ui import page_heading
from src.services.mock_services import MockServices


def render(service: MockServices) -> None:
    page_heading("Investigation", "Structured findings, evidence, and explicit uncertainty")
    result, package = service.get_investigation("ANOM-001")
    st.progress(70, text=f"Investigation status: {result.status}")
    st.subheader("Findings")
    for finding in result.findings: st.write("• " + finding)
    direct, inferred = st.columns(2)
    with direct:
        st.subheader("Direct claims")
        for claim in package.direct_claims: st.success("DIRECT: " + claim)
    with inferred:
        st.subheader("Inferred claims")
        for claim in package.inferred_claims: st.info("INFERENCE: " + claim)
    st.subheader("Evidence")
    for evidence_id in package.evidence_ids: evidence_drawer(service.get_evidence(evidence_id))
    st.subheader("Caveats and unresolved questions")
    for item in package.caveats + result.quality_warnings + result.unresolved_questions: st.warning(item)
