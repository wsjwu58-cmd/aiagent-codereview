package com.heima.codereview.common.model.knowledge;

import java.util.List;

public record KnowledgeBatchDeleteRequest(List<KnowledgeDeleteItem> records) {
}
