from __future__ import annotations

import streamlit as st


def page_heading(title: str, subtitle: str) -> None:
    st.title(title)
    st.caption(subtitle)


def status_pill(label: str, value: str) -> None:
    st.markdown(f"**{label}:** {value}")
