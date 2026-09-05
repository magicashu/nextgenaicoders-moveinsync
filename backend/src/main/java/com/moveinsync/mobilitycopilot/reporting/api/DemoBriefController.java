package com.moveinsync.mobilitycopilot.reporting.api;

import com.moveinsync.mobilitycopilot.reporting.domain.DecisionBrief;
import com.moveinsync.mobilitycopilot.workflow.application.WorkflowCoordinator;
import jakarta.validation.constraints.NotBlank;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.validation.annotation.Validated;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.time.LocalDate;

@Validated
@RestController
@ConditionalOnProperty(prefix = "mobility.demo", name = "enabled", havingValue = "true")
@RequestMapping("/api/v1/demo")
public class DemoBriefController {

    private final WorkflowCoordinator workflowCoordinator;

    public DemoBriefController(WorkflowCoordinator workflowCoordinator) {
        this.workflowCoordinator = workflowCoordinator;
    }

    @GetMapping("/brief")
    public DecisionBrief brief(
            @RequestHeader(name = "X-Business-Unit", defaultValue = "pinnacle-Slc") @NotBlank String businessUnit,
            @RequestParam(name = "asOf", defaultValue = "2026-06-08")
            @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate asOfDate) {
        return workflowCoordinator.createDemoBrief(businessUnit, asOfDate);
    }
}
