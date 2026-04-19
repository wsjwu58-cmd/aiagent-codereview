package com.heima.codereview.core.agent.planning;

import com.heima.codereview.core.agent.conversational.SpecialistReport;

import java.util.List;

public record TaskExecutionResult(
        SubTask task,
        String agentId,
        String agentName,
        boolean success,
        String output,
        String failureReason,
        ReflectionResult reflection,
        List<SubTask> proposedSubTasks,
        SpecialistReport report
) {

    public TaskExecutionResult {
        agentId = agentId == null ? "" : agentId;
        agentName = agentName == null ? "" : agentName;
        output = output == null ? "" : output;
        failureReason = failureReason == null ? "" : failureReason;
        reflection = reflection == null ? ReflectionResult.ok() : reflection;
        proposedSubTasks = proposedSubTasks == null ? List.of() : List.copyOf(proposedSubTasks);
    }
}
