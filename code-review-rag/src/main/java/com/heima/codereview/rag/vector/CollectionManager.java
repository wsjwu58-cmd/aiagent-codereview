package com.heima.codereview.rag.vector;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

@Component
public class CollectionManager {

    private static final Logger log = LoggerFactory.getLogger(CollectionManager.class);

    @Value("${spring.ai.vectorstore.milvus.collection-name:code_review_knowledge}")
    private String defaultCollection;

    public String getDefaultCollection() {
        return defaultCollection;
    }
}
