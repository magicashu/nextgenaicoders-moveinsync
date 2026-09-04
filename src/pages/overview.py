from __future__ import annotations

import plotly.express as px
import streamlit as st

from src.components.ui import page_heading
from src.components.network_map import render_network_map
from src.services.mock_services import MockServices


def render(service: MockServices) -> None:
    dashboard = service.get_dashboard()
    page_heading("Mobility Intelligence Control Tower", f"SIMULATION · Run {dashboard.run_id} · {dashboard.data_version}")
    issue = dashboard.priority_issue
    st.subheader("Top priority issue")
    left, right = st.columns([2, 1])
    with left:
        st.error(f"{issue.severity.upper()} · {issue.title}")
        st.write(f"**Current:** {issue.current_value:g}% · **Reference:** {issue.reference_value:g}% · **Gap:** {issue.gap:g} pp")
        st.write(f"Affected population: {issue.affected_population:,} · Confidence: {issue.confidence}")
        st.caption("Caveat: " + " ".join(issue.caveats))
    with right:
        if st.button("Investigate issue", type="primary"):
            st.session_state["page_hint"] = "Investigation"
            st.info("Open Investigation from the navigation to review structured findings and evidence.")
        st.caption("Evidence: " + ", ".join(issue.evidence_ids))
    st.subheader("KPI summary")
    cols = st.columns(len(dashboard.kpis))
    for col, kpi in zip(cols, dashboard.kpis, strict=True):
        with col:
            if kpi["value"] is None:
                st.metric(kpi["label"], "Unavailable", help=str(kpi["context"]))
            else:
                st.metric(kpi["label"], kpi["value"], str(kpi["context"]))
    st.subheader("What changed")
    changes = service.get_change_signals()
    change_columns = st.columns(len(changes))
    for column, signal in zip(change_columns, changes, strict=True):
        with column:
            st.metric(signal["label"], signal["change"])
            st.caption("Evidence: " + signal["evidence"])
    render_network_map(service.get_network_map())
    st.subheader("Agent activity")
    st.dataframe(dashboard.agent_activity, use_container_width=True, hide_index=True)
    st.subheader("Performance trend")
    figure = px.line(dashboard.trend, x="period", y="ota", markers=True, labels={"ota": "On-time arrival (%)", "period": "Period"})
    figure.add_hline(y=90, line_dash="dash", line_color="#d62728", annotation_text="Reference")
    figure.update_layout(height=300, margin=dict(l=0, r=0, t=10, b=0))
    st.plotly_chart(figure, use_container_width=True)
    st.subheader("Contributors")
    st.dataframe(dashboard.contributors, use_container_width=True, hide_index=True)
    st.subheader("Alerts")
    for alert in dashboard.alerts:
        st.warning(alert)
