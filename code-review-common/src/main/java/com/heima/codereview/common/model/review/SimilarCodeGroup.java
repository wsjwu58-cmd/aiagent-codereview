package com.heima.codereview.common.model.review;

public record SimilarCodeGroup(String groupId, double similarity, java.util.List<String> codeBlocks) {
}
