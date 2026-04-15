package com.heima.codereview.core.agent;

import com.heima.codereview.common.model.review.ReviewReport;
import com.heima.codereview.core.chain.Node;
import org.springframework.stereotype.Component;

import java.util.Map;

@Component
public class FlowAgent extends BaseAgent {

    private final ReviewAgent reviewAgent;
    private final AdvisorAgent advisorAgent;
    private final RefactorAgent refactorAgent;
    private final SummarizerAgent summarizerAgent;

    public FlowAgent(ReviewAgent reviewAgent,
                     AdvisorAgent advisorAgent,
                     RefactorAgent refactorAgent,
                     SummarizerAgent summarizerAgent) {
        this.reviewAgent = reviewAgent;
        this.advisorAgent = advisorAgent;
        this.refactorAgent = refactorAgent;
        this.summarizerAgent = summarizerAgent;
    }

    @Override
    public String getName() {
        return "流程编排Agent";
    }

    public FlowResult execute(AgentExecutionContext context, AgentEventListener listener) {
        FlowResult result = new FlowResult();
        String sessionId = context.sessionId();

        listener.onEvent(sessionId, "agent_start", Map.of("agentId", getId(), "name", getName()));
        listener.onEvent(sessionId, "agent_start", Map.of("agentId", reviewAgent.getId(), "name", reviewAgent.getName()));

        ReviewReport reviewReport = reviewAgent.execute(context, (index, node) -> sendChainProgress(sessionId, index, node, listener));
        result.setReport(reviewReport);

        listener.onEvent(sessionId, "agent_complete", Map.of("agentId", reviewAgent.getId()));
        listener.onEvent(sessionId, "agent_start", Map.of("agentId", advisorAgent.getId(), "name", advisorAgent.getName()));

        String advisorOutput = advisorAgent.execute(context.reviewContext(), reviewReport);
        streamText(sessionId, advisorAgent.getId(), advisorOutput, listener);
        result.setAdvisorOutput(advisorOutput);
        result.getAgentOutputs().put(advisorAgent.getId(), advisorOutput);

        listener.onEvent(sessionId, "agent_complete", Map.of("agentId", advisorAgent.getId()));
        listener.onEvent(sessionId, "agent_start", Map.of("agentId", refactorAgent.getId(), "name", refactorAgent.getName()));

        String refactoredCode = refactorAgent.execute(context.reviewContext(), advisorOutput);
        streamText(sessionId, refactorAgent.getId(), refactoredCode, listener);
        result.setRefactoredCode(refactoredCode);
        result.getAgentOutputs().put(refactorAgent.getId(), refactoredCode);

        listener.onEvent(sessionId, "agent_complete", Map.of("agentId", refactorAgent.getId()));
        listener.onEvent(sessionId, "agent_start", Map.of("agentId", summarizerAgent.getId(), "name", summarizerAgent.getName()));

        String summary = summarizerAgent.execute(context.reviewContext(), reviewReport, advisorOutput, refactoredCode);
        streamText(sessionId, summarizerAgent.getId(), summary, listener);
        result.setSummary(summary);
        result.getAgentOutputs().put(summarizerAgent.getId(), summary);

        listener.onEvent(sessionId, "agent_complete", Map.of("agentId", summarizerAgent.getId()));
        listener.onEvent(sessionId, "done", Map.of("reviewId", context.reviewContext().reviewId()));
        return result;
    }

    private void sendChainProgress(String sessionId, int currentIndex, Node node, AgentEventListener listener) {
        listener.onEvent(sessionId, "chain_node", Map.of(
                "current", currentIndex + 1,
                "nodeName", node.getName()
        ));
    }

    private void streamText(String sessionId, String agentId, String content, AgentEventListener listener) {
        for (char c : content.toCharArray()) {
            listener.onEvent(sessionId, "agent_stream", Map.of("agentId", agentId, "content", String.valueOf(c)));
        }
    }
}
