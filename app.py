from __future__ import annotations

import streamlit as st

from src.components.sidebar import render_sidebar
from src.pages import leadership, overview, recommendations, system_trust
from src.pages.investigation import render as render_investigation
from src.services.mock_services import MockServices


st.set_page_config(page_title="MoveInSync Decision Copilot", page_icon="🧭", layout="wide")


@st.cache_resource
def services() -> MockServices:
    return MockServices()


def main() -> None:
    service = services()
    page = render_sidebar(service.data_source_label)
    pages = {
        "Control Tower": overview.render,
        "Investigation": render_investigation,
        "Recommendations": recommendations.render,
        "Leadership Brief": leadership.render,
        "System / Trust": system_trust.render,
    }
    pages[page](service)


if __name__ == "__main__":
    main()
