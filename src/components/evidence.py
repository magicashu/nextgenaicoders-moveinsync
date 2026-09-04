from __future__ import annotations

import streamlit as st

from src.models.contracts import MetricEvidence


def evidence_drawer(evidence: MetricEvidence) -> None:
    with st.expander(f"Evidence {evidence.evidence_id}"):
        rows = {
            "Metric": f"{evidence.metric_id} · {evidence.metric_version}", "Value": _value(evidence),
            "Numerator / denominator": _ratio(evidence), "Window": evidence.window,
            "Population": evidence.population or "Unavailable", "Coverage": _coverage(evidence.coverage),
            "Comparison": evidence.comparison or "Unavailable", "Data version": evidence.data_version,
            "Warnings": "; ".join(evidence.warnings) or "None",
        }
        st.json(rows)


def _value(evidence: MetricEvidence) -> str:
    return "Unavailable" if evidence.value is None else f"{evidence.value:g}{evidence.unit}"


def _ratio(evidence: MetricEvidence) -> str:
    if evidence.numerator is None or evidence.denominator is None:
        return "Unavailable"
    return f"{evidence.numerator:g} / {evidence.denominator:g}"


def _coverage(coverage: float | None) -> str:
    return "Unavailable" if coverage is None else f"{coverage:.0%}"
