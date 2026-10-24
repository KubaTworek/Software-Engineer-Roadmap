package pl.jakubtworek.lld.workflow.domain;

/**
 * Dwie rzeczywiste strategie zgodne z jednym kontraktem, bez klas dla każdej roli.
 */
public enum ReviewerPolicies implements ReviewerPolicy {

    ALLOW_REUSE {

        @Override
        public boolean permits(UserId reviewer, ReviewRound round) {
            return true;
        }
    }
    , DISTINCT_PER_ROUND {

        @Override
        public boolean permits(UserId reviewer, ReviewRound round) {
            return round.approvals().stream().noneMatch(a -> a.reviewer().equals(reviewer));
        }
    }

}
