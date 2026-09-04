package com.moveinsync.mobilitycopilot.action.application;

import com.moveinsync.mobilitycopilot.action.domain.ActionProposal;
import com.moveinsync.mobilitycopilot.action.domain.ActionType;

/** A mocked downstream system. Adapters are pure functions of the proposal plus an idempotency key. */
public interface MockActionAdapter {

    ActionType supports();

    /** Performs the mock effect once and returns an external reference. Throwing means "no effect produced". */
    String perform(ActionProposal proposal, String idempotencyKey);
}
