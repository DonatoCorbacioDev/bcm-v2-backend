# ADR-0004: Separate FastAPI service for ML/AI, not embedded in the Java monolith

**Status:** Accepted
**Date:** 2026-06 (bcm-v2-ml introduced), documented 2026-07-22

## Context

BCM needs forecasting (renewal/revenue prediction), anomaly detection, a contract risk score,
and LLM-based clause-risk analysis. This is a deliberate exception to
[ADR-0001](0001-modular-monolith.md)'s default of "one deployable" — worth its own record for
why this specific piece was split out when nothing else was.

## Decision

A separate service (`bcm-v2-ml`, FastAPI/Python) hosts all ML/AI functionality. The Java
backend calls it over internal HTTP, authenticated with a shared `X-Internal-Api-Key` header
checked on every proxied call (`MlProxyService`), and treats it as an unreliable dependency:
results are cached (`ml_result_cache`, V16) and refreshed on a schedule
(`MlCacheRefresher`/`RiskScoreRefresher`), and a failed or slow ML call degrades the affected
feature rather than the request.

## Alternatives considered

- **Java ML libraries** (Smile, DL4J, Tribuo). Rejected: the Python data-science ecosystem
  (Prophet for time-series forecasting, scikit-learn's `IsolationForest` for anomaly
  detection) is far more mature, documented, and battle-tested for these specific problems
  than their JVM equivalents; reimplementing or wrapping them in Java would be strictly worse
  for no benefit.
- **Embed Python in the JVM** via a bridge (Jep, GraalPy). Rejected: operationally fragile,
  packaging/deployment complexity (native Python extensions like `numpy`/`scipy` inside a JVM
  bridge is a known source of pain), and it would still leave the Java process doing
  Python-shaped work, undermining the point of the split.
- **A managed cloud ML/AI API** for forecasting and clause analysis. Rejected primarily for
  data residency: contract text and financial data would leave the deployment boundary to a
  third-party processor, a real question under GDPR for the target market (Italian SMBs) —
  see [ADR-0005](0005-local-llm-via-ollama.md), which reaches the same conclusion for the LLM
  piece specifically.

## Consequences

- **Positive:** right tool for the job — Python's ML ecosystem, without forcing the whole
  application into Python or the ML code into Java.
- **Positive:** independently deployable and scalable; the backend already treats it as
  optional/degradable rather than load-bearing, so an ML outage doesn't take down contract
  management.
- **Negative:** two services to build, test, deploy, and monitor instead of one — two CI
  pipelines, two sets of dependencies to keep patched, a network hop and its latency, and an
  internal-auth surface (the shared API key) that didn't exist before.
- **Negative:** the ML service's own security posture matters independently — `docs/SECURITY.md`
  notes the internal-API-key check is disabled if `INTERNAL_API_KEY` is left empty on that
  service, which must be set whenever it's reachable outside the backend's trusted network.
