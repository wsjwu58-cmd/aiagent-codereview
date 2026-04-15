package com.heima.codereview.core.agent.conversational;

import com.heima.codereview.core.agent.BaseAgent;
import com.heima.codereview.core.agent.react.ReactLoop;
import com.heima.codereview.core.agent.react.ReactResult;
import com.heima.codereview.core.agent.react.ThinkingStep;
import com.heima.codereview.core.agent.specialized.SpecializedAgent;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.List;

@Component
public class ReActAgent extends BaseAgent {

    private final ConversationalCoordinator coordinator;
    private final SimpleAgent simpleAgent;
    private final ReactLoop reactLoop;

    public ReActAgent(ConversationalCoordinator coordinator, SimpleAgent simpleAgent, ReactLoop reactLoop) {
        this.coordinator = coordinator;
        this.simpleAgent = simpleAgent;
        this.reactLoop = reactLoop;
    }

    @Override
    public String getName() {
        return "深度分析 Agent";
    }

    public ReactResult analyze(String userMessage, ConversationContext context, ReactStreamListener listener) {
        CoordinationDecision decision = coordinator.analyze(userMessage, context);
        if (decision.type() == CoordinationType.SIMPLE_ANSWER) {
            String content = simpleAgent.respond(userMessage, context);
            List<ThinkingStep> steps = List.of(
                    ThinkingStep.thinking(getId(), getName(), "问题复杂度较低，直接切换到快速回答模式。"),
                    ThinkingStep.observation(getId(), getName(), content)
            );
            ReactResult result = new ReactResult(steps, content, decision.rationale(), List.of());
            listener.onComplete(result);
            return result;
        }

        List<ThinkingStep> allSteps = new ArrayList<>();
        List<SpecialistReport> reports = new ArrayList<>();
        for (SpecializedAgent specialist : coordinator.resolveSpecialists(decision)) {
            listener.onAgentStart(specialist.getId(), specialist.getName());
            SpecialistExecutionResult execution = reactLoop.execute(specialist, userMessage, context, listener);
            allSteps.addAll(execution.steps());
            reports.add(execution.report());
            listener.onAgentComplete(execution.report());
        }

        String finalContent = coordinator.summarize(userMessage, context, decision, reports);
        ReactResult result = new ReactResult(allSteps, finalContent, decision.rationale(), reports);
        listener.onComplete(result);
        return result;
    }
}
