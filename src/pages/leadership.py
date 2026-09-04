from __future__ import annotations

import streamlit as st

from src.components.ui import page_heading
from src.services.mock_services import MockServices


def render(service: MockServices) -> None:
    brief = service.get_brief()
    page_heading("Leadership brief", "Forwardable summary using the same evidence IDs as the investigation")
    text = "\n".join([brief.title, "", brief.executive_summary, "", "Key findings:", *[f"- {item}" for item in brief.key_findings], "", f"Recommendation: {brief.recommendation}", f"Expected effect: {brief.expected_effect}", f"Risk: {brief.risk}", f"Confidence: {brief.confidence}", f"Caveat: {' '.join(brief.caveats)}", f"Evidence: {', '.join(brief.evidence_ids)}"])
    st.text_area("Brief", text, height=320)
    st.download_button("Download brief", text, file_name="leadership_brief.txt", mime="text/plain")
