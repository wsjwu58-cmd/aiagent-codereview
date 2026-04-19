package com.heima.codereview.core.agent.planning;

import java.util.List;

public record ReflectionResult(
        boolean satisfied,
        String gap,
        List<SubTask> remediationTasks
) {

    public ReflectionResult {
        gap = gap == null ? "" : gap;
        remediationTasks = remediationTasks == null ? List.of() : List.copyOf(remediationTasks);
    }

    public static ReflectionResult ok() {
        return new ReflectionResult(true, "", List.of());
    }

    public static ReflectionResult gap(String gap, List<SubTask> remediationTasks) {
        return new ReflectionResult(false, gap, remediationTasks);
    }
}
