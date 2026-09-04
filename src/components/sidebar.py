from __future__ import annotations

import streamlit as st


def render_sidebar(data_source_label: str) -> str:
    with st.sidebar:
        st.title("MoveInSync")
        st.caption("Decision Copilot")
        page = st.radio("Navigate", ["Control Tower", "Investigation", "Recommendations", "Leadership Brief", "System / Trust"])
        st.divider()
        st.caption(data_source_label)
        st.caption("Development mode · structured mock services")
    return page
