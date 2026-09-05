package com.moveinsync.mobilitycopilot.action.adapter.mock;

import com.moveinsync.mobilitycopilot.action.application.MockActionAdapter;
import com.moveinsync.mobilitycopilot.action.domain.ActionProposal;
import com.moveinsync.mobilitycopilot.action.domain.ActionType;

import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * The four allowlisted mock effects (D-018): watchlist entry, investigation ticket, vendor escalation
 * draft and communication draft. Nothing leaves the process; each adapter keeps a ledger for tests
 * and the demo trust panel.
 */
public final class MockAdapters {

    private MockAdapters() {
    }

    public static List<MockActionAdapter> all() {
        return List.of(new Watchlist(), new InvestigationTicket(), new VendorEscalationDraft(), new CommunicationDraft());
    }

    public abstract static class Ledger implements MockActionAdapter {
        final Map<String, String> effects = new ConcurrentHashMap<>();
        volatile boolean failNext;

        String record(String prefix, String idempotencyKey) {
            if (failNext) {
                failNext = false;
                throw new IllegalStateException(prefix + " system unavailable");
            }
            String reference = prefix + "-" + Integer.toHexString(idempotencyKey.hashCode());
            if (effects.putIfAbsent(idempotencyKey, reference) != null) {
                throw new IllegalStateException("Duplicate effect attempted for " + idempotencyKey);
            }
            return reference;
        }

        public int effectCount() {
            return effects.size();
        }

        public void failNext() {
            failNext = true;
        }
    }

    public static final class Watchlist extends Ledger {
        @Override public ActionType supports() { return ActionType.CREATE_SITE_SHIFT_WATCHLIST; }
        @Override public String perform(ActionProposal proposal, String key) { return record("WATCH", key); }
    }

    public static final class InvestigationTicket extends Ledger {
        @Override public ActionType supports() { return ActionType.CREATE_INVESTIGATION_TICKET; }
        @Override public String perform(ActionProposal proposal, String key) { return record("TICKET", key); }
    }

    public static final class VendorEscalationDraft extends Ledger {
        @Override public ActionType supports() { return ActionType.DRAFT_VENDOR_ESCALATION; }
        @Override public String perform(ActionProposal proposal, String key) { return record("ESCALATION-DRAFT", key); }
    }

    public static final class CommunicationDraft extends Ledger {
        @Override public ActionType supports() { return ActionType.DRAFT_COMMUNICATION; }
        @Override public String perform(ActionProposal proposal, String key) { return record("COMMS-DRAFT", key); }
    }
}
