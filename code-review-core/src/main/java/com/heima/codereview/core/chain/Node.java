package com.heima.codereview.core.chain;

public interface Node {
    String getName();

    NodeResult execute(ReviewContext context);
}
