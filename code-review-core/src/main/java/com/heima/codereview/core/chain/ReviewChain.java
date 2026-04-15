package com.heima.codereview.core.chain;

import com.heima.codereview.common.model.review.ReviewIssue;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.function.BiConsumer;

@Component
public class ReviewChain {

    private final List<Node> nodes;

    public ReviewChain(List<Node> nodes) {
        this.nodes = new CopyOnWriteArrayList<>(nodes);
    }

    public List<NodeResult> process(ReviewContext context, BiConsumer<Integer, Node> progressConsumer) {
        List<NodeResult> results = new ArrayList<>();
        for (int i = 0; i < nodes.size(); i++) {
            Node node = nodes.get(i);
            if (progressConsumer != null) {
                progressConsumer.accept(i, node);
            }
            NodeResult result = node.execute(context);
            results.add(result);
        }
        return results;
    }

    public List<Node> getNodes() {
        return nodes;
    }

    public static int scoreFromIssues(List<ReviewIssue> issues) {
        int score = 100;
        for (ReviewIssue issue : issues) {
            score -= switch (issue.severity()) {
                case "CRITICAL" -> 20;
                case "HIGH" -> 12;
                case "MEDIUM" -> 6;
                default -> 3;
            };
        }
        return Math.max(score, 0);
    }
}
