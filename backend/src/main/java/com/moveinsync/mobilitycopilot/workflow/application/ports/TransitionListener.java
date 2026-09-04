package com.moveinsync.mobilitycopilot.workflow.application.ports;

import com.moveinsync.mobilitycopilot.workflow.domain.TransitionEvent;

/** Receives every node transition so telemetry can open nested spans without living inside the engine. */
public interface TransitionListener {

    void onTransition(TransitionEvent event);

    TransitionListener NONE = event -> { };
}
