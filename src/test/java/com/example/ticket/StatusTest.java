package com.example.ticket;
import com.example.ticket.domain.Status;
import org.junit.jupiter.api.Test;
import static org.assertj.core.api.Assertions.assertThat;
class StatusTest {
    @Test void verifiesAllStatePairs() {
        for (Status source : Status.values()) for (Status target : Status.values()) {
            boolean allowed = (source == Status.OPEN && target == Status.IN_PROGRESS)
                || (source == Status.IN_PROGRESS && target == Status.RESOLVED)
                || (source == Status.RESOLVED && (target == Status.CLOSED || target == Status.IN_PROGRESS));
            assertThat(source.canTransitionTo(target)).isEqualTo(allowed);
        }
    }
}
