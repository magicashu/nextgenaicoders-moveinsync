from __future__ import annotations

import streamlit as st

from src.components.ui import page_heading
from src.services.mock_services import MockServices


def render(service: MockServices) -> None:
    page_heading("Recommendations", "Approval is recorded by the service; the frontend never executes an operational action.")
    for proposal in service.get_recommendations():
        st.subheader(proposal.action_type)
        st.write(proposal.reason)
        st.caption("Targets: " + ", ".join(proposal.target_ids) + " · Evidence: " + ", ".join(proposal.evidence_ids))
        left, middle, right = st.columns(3)
        with left:
            if st.button("Approve", key=f"approve-{proposal.action_id}"):
                _show_receipt(service.approve(proposal.action_id))
        with middle:
            if st.button("Reject", key=f"reject-{proposal.action_id}"):
                _show_receipt(service.reject(proposal.action_id))
        with right:
            edit = st.text_input("Edit note", key=f"edit-{proposal.action_id}")
            if st.button("Save edit", key=f"save-{proposal.action_id}"):
                _show_receipt(service.edit(proposal.action_id, edit))
        st.info(f"Expected effect: {proposal.expected_effect}\n\nRisk: {proposal.risk}\n\nStatus: {proposal.status}")


def _show_receipt(receipt: object) -> None:
    st.success("Decision recorded in mock mode.")
    st.json(receipt.__dict__)
