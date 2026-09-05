# Dataset-only evaluation scenarios — for review

Status: backend implementation and test execution authorized by the subsequent user request. These remain dataset-only business scenarios; see backend acceptance tests for measured results rather than treating this plan as a scorecard.

This is the active shortlist, replacing the broader reference-derived scenario proposal in docs/agents-guide.md. Every scenario uses the existing official May–July 2026 files and M01–M18 contracts documented in [requirement.md](../docs/requirement.md). Brochure benchmarks, invented records, corrupted dataset variants, injected failures and hypothetical product capabilities are outside this shortlist.

The documented dataset profile establishes the basis for these questions. This planning review has not independently recomputed their answers. Numerical expected outputs must be verified against the original dataset before execution; no new result, threshold, formula or causal conclusion is specified here.

## Scenarios to review

| ID | Question we will ask | Actual dataset basis | What a good result must do |
|---|---|---|---|
| DS-01 | Why did delays increase at pinnacle-Slc during June 1–7? | G1; ride data; M01. Current window June 1–7, reference May 4–31, as-of June 8. | Compare the governed delayed-trip rate with its reference; investigate site/shift, vendors, reasons, feedback and billing. Explain supported concentration without asserting a proven cause. |
| DS-02 | Where within pinnacle-Slc was that delay increase concentrated? | G1; office, shift and direction fields; M01. Same periods as DS-01. | Identify the documented Clearwater Campus/morning-shift concentration; preserve the distinction between a group's delay rate and its share of delayed trips. |
| DS-03 | Was the pinnacle-Slc delay increase isolated to one vendor? | G1; vendor_id and delay data; M01. Same periods as DS-01. | Compare qualifying vendors in both periods and recognize the documented broad deterioration. Do not blame a single vendor. Retain minimum-volume qualifications. |
| DS-04 | How did recorded delay reasons change during the pinnacle-Slc spike? | G1; delay_reason and delay_minutes; M03. Same periods as DS-01. | Report employee/driver/traffic categories among delayed trips, with the correct denominator. Recorded reason categories are not independent proof of causation. |
| DS-05 | How did employee pickup and drop punctuality change for vanta-Aus through July? | G2; employee-leg planned/actual epochs and boarding eligibility; M04/M05. As-of August 1; documented May and final-July periods. | Calculate each metric on its eligible boarded legs, apply the approved ten-minute rule, retain missing-epoch exclusions and distinguish pickups from drops. |
| DS-06 | Did vanta-Aus no-shows improve while punctuality worsened? | G2; employee-leg is_no_show and trip punctuality; M06 with M01/M04/M05. Documented May–July comparison. | Preserve the documented improvement in no-shows alongside worsening punctuality. Do not force every domain into the same worsening narrative or require no-show legs to have boarded. |
| DS-07 | What was the dashboard-cancellation rate for pinnacle-Slc in June? | Employee legs, not_boarding_reason and planned-leg population; M07. | Use the governed cancellation definition, denominator and dedupe rules. Keep cancellations distinct from no-shows. No rise or causal explanation is presumed. |
| DS-08 | What seat occupancy do the pinnacle-Slc June trips show? | Ride actualemployee_cnt and actual_cab_capacity; M08. | Preserve trip-level occupancy semantics, the capacity cap and over-capacity quality flags. Do not infer savings or ghost trips. Do not introduce a group aggregation rule not settled by the contract. |
| DS-09 | What was median billed cost per trip for vanta-Sea's May billing cycle? | Documented billing fixture; bill_data; M09. | Exclude the documented negative adjustments and duplicates, aggregate retained lines per composite trip and then calculate the median. Report trip cost, not cost per employee. |
| DS-10 | Did billed trip cost also rise during pinnacle-Slc's delay deterioration? | G1; M09 billing evidence alongside M01 delay evidence. Use the documented billing cycles and G1 trip windows with their distinct labels. | Preserve the documented cost counterevidence. Do not assign billing-cycle costs to individual incident days or claim a financial penalty unsupported by billing evidence. |
| DS-11 | What is cost per billed kilometre for pinnacle-Slc's June billing period? | Positive-distance billing records; supported tenant capability; M10. | Apply the governed billed-cost/billed-distance eligibility and period. No value or trend is presumed. Do not substitute trip distance or an employee denominator. |
| DS-12 | What does vanta-Aus driver feedback show through July, and how representative is it? | G2; trip_feedback driver_rating and participation coverage; M11. | Preserve the documented trend and low-coverage caveat; apply rating-zero, placeholder and duplicate exclusions. Do not generalize respondent ratings to all commuters. |
| DS-13 | What are the mean driver and safety ratings for pinnacle-Slc in June? | trip_feedback rating columns; M12. | Evaluate the contracted rating dimensions separately, exclude non-positive ratings and state the applicable population and participation limitations. No rating level or trend is presumed. |
| DS-14 | Does pinnacle-Slc's sign-off alert change represent an operational improvement? | G3; actual EMPLOYEE_SIGN_OFF_TIME_VIOLATION pattern in May; M13/M14 exclusions. | Classify the documented change as a data-regime/data-quality issue, exclude the event as contracted and avoid an operational escalation or an invented safety improvement. |
| DS-15 | What were pinnacle-Slc's overall and Sev-1/2 alert rates in June? | alerts_data and ride populations; M13/M14. | Preserve events per 1,000 trips, severity eligibility and the sign-off exclusion. Distinguish alerts from confirmed incidents and event rates from affected-trip percentages. |
| DS-16 | Is there an eligible Sev-1/2 acknowledgement population for pinnacle-Slc in June, and what is P90 for catalyst-Sac's eligible June alerts? | Actual alert severity, start and acknowledgement timestamps; M15. | Pinnacle-Slc is unavailable for this period because it has no eligible severe alerts. Catalyst-Sac supplies a positive population. Report P90 only where eligible; neither mean resolution time nor a presumed target breach. |
| DS-17 | How did vanta-Aus device-unreachable alert rates change from May through July? | G2; DEVICE_NOT_REACHABLE events and trip populations; M16. | Compute the contracted rates from events and exposure. Distinguish monthly counts from normalized rates, and label this as an alert proxy rather than GPS coverage. |
| DS-18 | How did vanta-Aus EV trip share change from May through July? | G2; ride actual_cab_fuel_type; M17. | Preserve the documented EV-share trend using trips as the denominator. Do not convert it into fleet share, emissions avoided or monetary savings. |
| DS-19 | Among vanta-Aus trips with a women-travelling-alone alert, how often was an escort present in July? | Actual alert type, composite trip join and actual_escort; supported M18 capability. | Use distinct eligible composite trips and preserve the alert-conditioned population. Report descriptive escort presence without extending it to all women, all night trips or compliance. |
| DS-20 | Give operations and leadership a combined vanta-Aus brief as of August 1. | Full G2 across ride, employee, feedback, alert and billing data; M01/M04/M05/M06/M11/M16/M17 and documented capabilities. | Both views retain punctuality deterioration, feedback limitations, improved no-shows and increased EV share. Keep the observed office-concentration, low-feedback-coverage and unavailable-cost-per-km caveats. Current source reconciliation shows two offices, superseding the old single-office assumption. Any recommendation remains a supported draft. |

DS-20's unavailable cost-per-km caveat comes from the actual vanta-Aus billing data and documented capability assessment. It is not an added hypothetical unsupported-feature scenario.

## How each scenario evaluates the four agents

For the selected authorized tenant and period, use the same unmodified dataset evidence throughout the chain. A scenario's business question does not authorize access to another tenant.

| Agent | Expected contribution | How we will judge it |
|---|---|---|
| Supervisor | Select the relevant registered workers and preserve the scenario's scope, comparison periods and required alternative analyses. | Deterministic task/scope/capability checks; reviewed relevance rubric where several plans are valid. Do not require one incidental tool order. |
| Investigator | Obtain the contracted metrics and return findings with evidence, units, population, eligibility, period and limitations. | Compare with independently verified deterministic results. A model cannot establish the numerical oracle. |
| Evidence Critic | Accept supported findings; identify unsupported interpretations, mismatched populations and missing material caveats in the actual agent output. | Human-reviewed claim/evidence labels, deterministic citation/value checks, and semantic review of causal language. Track unjustified rejection as well as missed problems. No fabricated evidence mutation is included in this shortlist. |
| Briefing / Action | Explain the verified result clearly and preserve qualifications across operations and leadership wording. Draft a next step only where evidence and existing policy support it. | Claim/evidence consistency, caveat retention, audience consistency and a reviewed usefulness rubric. No external actions are executed. |

## Dataset handling and pass criteria

- Join on `(business_unit, trip_id)`, apply approved normalization/dedupe and retain the documented data-quality exclusions. Use naturally occurring records only; do not manufacture edge cases.
- For DS-01–06, DS-09–10, DS-12, DS-14 and DS-17–20, use the documented golden findings as candidate qualitative expectations. Verify every numerical expectation before using it as a test oracle.
- DS-07/08/11/13/15/16 are questions supported by actual fields and approved contracts, not predeclared anomalies. Their expected numerical outcomes remain unset until computation. A healthy or qualified result can be correct.
- DS-08 stays at the contracted trip grain unless the approved aggregation is unambiguous. Any unsettled contract detail must be resolved before an evaluator assigns pass/fail; it must not become a hidden assumption.
- A scenario passes only when its applicable deterministic assertions hold and its conclusion faithfully reflects evidence. Report per-agent outcomes separately from downstream guard outcomes. A guard rescuing an incorrect suggestion does not erase that agent's mistake.
- Semantic quality is reviewed using the existing proposed rubric, with no newly invented acceptance percentage. No current agent performance or dataset result is claimed by this plan.

## Suggested review sequence

Review DS-01 through DS-06 first: they form the clearest delay investigation and contrasting vanta-Aus trends. Then review financial/feedback questions DS-07 through DS-13, safety/tracking/sustainability questions DS-14 through DS-19, and finish with the combined DS-20 brief.

These 20 scenarios are the entire active shortlist for this review. Other evaluation families remain outside this user's current dataset-only planning scope.
