# Architecture reference branch

This documentation transfer updates Java-branch only. Implementation remains on Java-branch-2; no backend/frontend source, tests, scripts, dependencies, runtime configuration, migrations, API contracts or datasets were transferred.

The HLD, 18-node design, repository map and reviews describe the Java-branch-2 working baseline as reviewed on 2026-09-05, including its then-uncommitted improvements. Feature completion and recorded test/latency results apply to that implementation, not to this branch's older scaffold.

Use the architecture here for decisions and planning. Use Java-branch-2 to run or develop the integrated application. Source paths and API 0.3.0 references in the architecture point to that implementation; the actual code and contract files on Java-branch intentionally remain at their previous versions.

## Included references

- [Decision register through D-049](hackathon-decision-register.md)
- [High-level architecture](high-level-design.md)
- [Detailed 18-node design](detailed-solution-architecture-plan.md)
- [Repository responsibility map](project-structure.md)
- [Standalone architecture illustration](high-level-design-visual.html)
- [Editable SVG diagram](architecture/mobility-decision-copilot-hld.svg)
- [Governed dataset/metric contracts](dataset-profile-and-capability-matrix.md)
- [Component and verification review](component-node-review-2026-09-05.md)
- [Plan alignment review](plan-alignment-review-2026-09-05.md)
- [Earlier project readiness review](project-readiness-review-2026-09-05.md)

The readiness report is an earlier assessment; later component and architecture reviews supersede its implementation status. The existing PNG diagram is historical. The reviewed root plan.md remains in the implementation workspace and is not adopted as this branch's master plan.

The original README setup below its branch notice still describes the scaffold code actually present here. No branch merge or implementation cherry-pick was performed. See D-049 for the user-authorized boundary.
