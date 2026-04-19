package com.heima.codereview.core.agent.specialized;

import com.heima.codereview.common.model.review.ReviewReport;
import com.heima.codereview.core.agent.planning.SubTask;

import java.util.List;

public record ReviewSpecialistOutcome(
        ReviewReport report,
        String advisorOutput,
        String refactoredCode,
        String summary,
        List<SubTask> proposedSubTasks
) {

    public ReviewSpecialistOutcome {
        advisorOutput = advisorOutput == null ? "" : advisorOutput;
        refactoredCode = refactoredCode == null ? "" : refactoredCode;
        summary = summary == null ? "" : summary;
        proposedSubTasks = proposedSubTasks == null ? List.of() : List.copyOf(proposedSubTasks);
    }
}
