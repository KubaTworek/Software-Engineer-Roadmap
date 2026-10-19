package pl.jakubtworek.backend_engineering.stage_2.block_d.security;

import static pl.jakubtworek.backend_engineering.stage_2.block_d.security.SecurityDataFlow.SecurityControl.AUDIT;
import static pl.jakubtworek.backend_engineering.stage_2.block_d.security.SecurityDataFlow.SecurityControl.AUTHENTICATION;
import static pl.jakubtworek.backend_engineering.stage_2.block_d.security.SecurityDataFlow.SecurityControl.AUTHORIZATION;
import static pl.jakubtworek.backend_engineering.stage_2.block_d.security.SecurityDataFlow.SecurityControl.EGRESS_ALLOWLIST;
import static pl.jakubtworek.backend_engineering.stage_2.block_d.security.SecurityDataFlow.SecurityControl.ENCRYPTION_AT_REST;
import static pl.jakubtworek.backend_engineering.stage_2.block_d.security.SecurityDataFlow.SecurityControl.INPUT_VALIDATION;
import static pl.jakubtworek.backend_engineering.stage_2.block_d.security.SecurityDataFlow.SecurityControl.RATE_LIMIT;
import static pl.jakubtworek.backend_engineering.stage_2.block_d.security.SecurityDataFlow.SecurityControl.TLS;

import java.util.ArrayList;
import java.util.List;
import java.util.Set;

/** Executable minimum review. It fails closed when a required control is absent. */
public final class ThreatModelValidator {

    public Review review(List<SecurityDataFlow> flows) {
        if (flows == null || flows.isEmpty()) throw new IllegalArgumentException("at least one data flow is required");
        List<Violation> violations = new ArrayList<>();
        for (SecurityDataFlow flow : flows) {
            require(flow, TLS, violations, "every boundary-crossing flow must define transport protection");

            if (flow.source() == SecurityDataFlow.TrustZone.INTERNET) {
                require(flow, INPUT_VALIDATION, violations, "internet input must be validated");
                require(flow, RATE_LIMIT, violations, "internet input must have an abuse limit");
            }
            if (flow.accessLevel() != SecurityDataFlow.AccessLevel.PUBLIC) {
                require(flow, AUTHENTICATION, violations, "non-public flow requires authentication");
            }
            if (flow.accessLevel() == SecurityDataFlow.AccessLevel.PRIVILEGED) {
                require(flow, AUTHORIZATION, violations, "privileged flow requires authorization");
                require(flow, AUDIT, violations, "privileged flow requires an audit trail");
            }
            if (flow.destination() == SecurityDataFlow.TrustZone.DATA
                    && flow.sensitivity().requiresStrongProtection()) {
                require(flow, ENCRYPTION_AT_REST, violations, "sensitive data store requires encryption at rest");
            }
            if (flow.destination() == SecurityDataFlow.TrustZone.THIRD_PARTY) {
                require(flow, EGRESS_ALLOWLIST, violations, "third-party egress requires an allowlist");
            }
        }
        return new Review(violations.isEmpty(), violations);
    }

    private static void require(
            SecurityDataFlow flow,
            SecurityDataFlow.SecurityControl control,
            List<Violation> violations,
            String reason) {
        Set<SecurityDataFlow.SecurityControl> controls = flow.controls();
        if (!controls.contains(control)) violations.add(new Violation(flow.name(), control, reason));
    }

    public record Review(boolean approved, List<Violation> violations) {
        public Review {
            violations = List.copyOf(violations);
        }
    }

    public record Violation(String flow, SecurityDataFlow.SecurityControl missingControl, String reason) {
    }
}
