package com.heima.codereview.core.agent.react;

import com.heima.codereview.core.agent.conversational.SpecialistReport;

import java.util.List;

public record ReactResult(
        List<ThinkingStep> steps,
        String finalContent,
        String summary,
        List<SpecialistReport> reports
) {
}
