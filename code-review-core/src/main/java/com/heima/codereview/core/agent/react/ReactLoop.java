package com.heima.codereview.core.agent.react;

import com.heima.codereview.core.agent.conversational.ConversationContext;
import com.heima.codereview.core.agent.conversational.ReactStreamListener;
import com.heima.codereview.core.agent.conversational.SpecialistExecutionResult;
import com.heima.codereview.core.agent.conversational.SpecialistReport;
import com.heima.codereview.core.agent.specialized.SpecializedAgent;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.List;

@Service
public class ReactLoop {

    private static final int MAX_ITERATIONS = 8;

    public SpecialistExecutionResult execute(SpecializedAgent agent,
                                             String userMessage,
                                             ConversationContext context,
                                             ReactStreamListener listener) {
        List<ThinkingStep> steps = new ArrayList<>();
        List<ToolCallResult> toolResults = new ArrayList<>();
        ReactContext reactContext = new ReactContext(userMessage, context);
        ReactState state = ReactState.initial();
        String finalContent = "";
        boolean completed = false;

        while (state.iteration() < MAX_ITERATIONS) {
            int currentRound = state.iteration() + 1;
            ReactDecision decision = agent.decideNextAction(userMessage, reactContext, state);
            ThinkingStep thought = ThinkingStep.thinking(
                    agent.getId(),
                    agent.getName(),
                    "Round " + currentRound + "/" + MAX_ITERATIONS + ": "
                            + safe(decision.thought(), "collect more evidence and decide the next action")
            );
            steps.add(thought);
            listener.onStep(thought);

            if (decision.action() != ReactDecision.Action.TOOL || decision.toolName().isBlank()) {
                finalContent = resolveFinalContent(agent, userMessage, reactContext, toolResults, decision.finalAnswer());
                state = new ReactState(
                        steps,
                        toolResults,
                        currentRound,
                        state.consecutiveNoProgress(),
                        finalContent,
                        true,
                        safe(decision.terminationReason(), "decision_finish")
                );
                completed = true;
                break;
            }

            ToolCallResult toolResult = agent.callTool(decision.toolName(), decision.toolParams(), userMessage, reactContext, state);
            toolResults.add(toolResult);

            ThinkingStep toolStep = ThinkingStep.toolCall(
                    agent.getId(),
                    agent.getName(),
                    toolResult.toolName(),
                    toolResult.input(),
                    toolResult.output()
            );
            steps.add(toolStep);
            listener.onStep(toolStep);

            String observationText = agent.observeToolResult(toolResult, reactContext, state);
            ThinkingStep observation = ThinkingStep.observation(agent.getId(), agent.getName(), observationText);
            steps.add(observation);
            listener.onStep(observation);

            int consecutiveNoProgress = isLowValueObservation(toolResult)
                    || state.hasToolCall(toolResult.toolName(), toolResult.input())
                    ? state.consecutiveNoProgress() + 1
                    : 0;

            state = new ReactState(
                    steps,
                    toolResults,
                    currentRound,
                    consecutiveNoProgress,
                    observationText,
                    false,
                    ""
            );

            if (agent.shouldTerminate(state)) {
                finalContent = resolveFinalContent(agent, userMessage, reactContext, toolResults, "");
                state = new ReactState(
                        steps,
                        toolResults,
                        currentRound,
                        consecutiveNoProgress,
                        finalContent,
                        true,
                        state.isStuck() ? "stuck" : "enough_evidence"
                );
                completed = true;
                break;
            }
        }

        if (!completed) {
            finalContent = resolveFinalContent(agent, userMessage, reactContext, toolResults, "");
            state = new ReactState(
                    steps,
                    toolResults,
                    Math.max(state.iteration(), MAX_ITERATIONS),
                    state.consecutiveNoProgress(),
                    finalContent,
                    true,
                    "max_iterations"
            );
        }

        if (steps.isEmpty()
                || !"OBSERVATION".equals(steps.get(steps.size() - 1).type())
                || !finalContent.equals(steps.get(steps.size() - 1).content())) {
            ThinkingStep finalObservation = ThinkingStep.observation(agent.getId(), agent.getName(), finalContent);
            steps.add(finalObservation);
            listener.onStep(finalObservation);
        }

        return new SpecialistExecutionResult(steps, new SpecialistReport(agent.getId(), agent.getName(), finalContent));
    }

    private String resolveFinalContent(SpecializedAgent agent,
                                       String userMessage,
                                       ReactContext reactContext,
                                       List<ToolCallResult> toolResults,
                                       String plannedAnswer) {
        if (plannedAnswer != null && !plannedAnswer.isBlank()) {
            return plannedAnswer.trim();
        }
        return agent.generateAnalysis(userMessage, reactContext, toolResults);
    }

    private boolean isLowValueObservation(ToolCallResult toolResult) {
        String output = toolResult == null || toolResult.output() == null ? "" : toolResult.output().trim().toLowerCase();
        return output.isBlank()
                || output.contains("not found")
                || output.contains("failed")
                || output.contains("unavailable");
    }

    private String safe(String value, String fallback) {
        if (value == null || value.isBlank()) {
            return fallback;
        }
        return value;
    }
}
