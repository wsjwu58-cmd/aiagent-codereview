package com.heima.codereview.core.agent.conversational;

import com.heima.codereview.core.agent.react.ThinkingStep;

import java.util.List;

public record SpecialistExecutionResult(List<ThinkingStep> steps, SpecialistReport report) {
}
