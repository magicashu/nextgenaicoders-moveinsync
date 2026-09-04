from __future__ import annotations

import plotly.graph_objects as go
import streamlit as st

from src.models.contracts import NetworkMap


def render_network_map(network: NetworkMap) -> None:
    """Render only the backend-provided capability and point payload."""
    st.subheader("Live network / map")
    if not network.available:
        st.info(network.reason or "Location data is unavailable for this source.")
        return

    severity = st.selectbox("Severity", ["All", *network.supported_severities], key="map-severity")
    category = st.selectbox("Category", ["All", *network.supported_categories], key="map-category")
    visible = [
        point for point in network.points
        if (severity == "All" or point.severity == severity)
        and (category == "All" or point.category == category)
    ]
    if not visible:
        st.info("No map points match the current filters.")
        return

    vehicle_points = [point for point in visible if point.point_type == "vehicle"]
    anomaly_points = [point for point in visible if point.point_type == "anomaly"]
    figure = go.Figure()
    _add_points(figure, vehicle_points, "#1f77b4", "circle", "Active trips / vehicles")
    _add_points(figure, anomaly_points, "#d62728", "diamond", "Anomaly markers")
    center = visible[0]
    figure.update_layout(
        mapbox={"style": "open-street-map", "center": {"lat": center.latitude, "lon": center.longitude}, "zoom": 10},
        margin={"l": 0, "r": 0, "t": 0, "b": 0}, height=330, legend={"orientation": "h", "y": 1.02},
    )
    st.plotly_chart(figure, use_container_width=True, config={"scrollZoom": False})
    st.caption("Affected areas: " + ", ".join(network.affected_areas))


def _add_points(figure: go.Figure, points: list, color: str, symbol: str, name: str) -> None:
    if not points:
        return
    figure.add_trace(go.Scattermapbox(
        lat=[point.latitude for point in points], lon=[point.longitude for point in points], mode="markers", name=name,
        marker={"size": 11, "color": color, "symbol": symbol},
        text=[f"{point.label}<br>{point.route_label or 'Area unavailable'}" for point in points], hoverinfo="text",
    ))
