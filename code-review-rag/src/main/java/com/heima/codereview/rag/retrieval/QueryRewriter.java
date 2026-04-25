package com.heima.codereview.rag.retrieval;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.ai.chat.prompt.SystemPromptTemplate;
import org.springframework.core.io.ClassPathResource;
import org.springframework.stereotype.Service;

import java.util.Arrays;
import java.util.List;

/**
 * 查询重写服务 - 扩展用户查询以提升检索召回率
 *
 * <p>为什么需要Query Rewriting？
 * <ul>
 *   <li>用户表述多样：同一意图可能有多种表达方式</li>
 *   <li>语义扩展：中文/英文/专业术语可能混用</li>
 *   <li>同义词问题："代码审查"≈"code review"≈"代码检查"</li>
 * </ul>
 *
 * <p>实现方式：使用LLM根据Prompt模板生成3-5个相关查询词
 *
 * @see QueryRewriter#rewrite(String)
 */
@Service
public class QueryRewriter {

    private static final Logger log = LoggerFactory.getLogger(QueryRewriter.class);

    /** Prompt模板路径 */
    private static final String PROMPT_PATH = "prompt/query-rewriter-prompt.md";

    /** Spring AI聊天客户端 */
    private final ChatClient chatClient;

    public QueryRewriter(ChatClient.Builder chatClientBuilder) {
        this.chatClient = chatClientBuilder.build();
    }

    /**
     * 重写用户查询
     *
     * <p>使用LLM将原始查询扩展为多个相关查询词，
     * 用于后续的多路检索。
     *
     * <p>示例：
     * <ul>
     *   <li>输入："SQL注入怎么防范"</li>
     *   <li>输出：["SQL注入", "sql injection", "防止注入攻击", "参数化查询", "安全编码"]</li>
     * </ul>
     *
     * @param query 原始用户查询
     * @return 扩展后的查询词列表（最多5个）
     *
     * <p>处理流程：
     * <ol>
     *   <li>加载Prompt模板</li>
     *   <li>调用LLM生成扩展查询</li>
     *   <li>解析结果（逗号/换行分割）</li>
     *   <li>异常时返回原始查询作为兜底</li>
     * </ol>
     */
    public List<String> rewrite(String query) {
        if (query == null || query.isBlank()) {
            return List.of();
        }
        try {
            // 第1步：加载Prompt模板
            var resource = new ClassPathResource(PROMPT_PATH);
            var systemPrompt = new SystemPromptTemplate(resource).render();

            // 第2步：调用LLM
            var response = chatClient.prompt()
                    .system(systemPrompt)
                    .user(query)
                    .call()
                    .content();

            if (response == null || response.isBlank()) {
                return List.of(query);
            }

            // 第3步：解析结果（按逗号或换行分割）
            return Arrays.stream(response.split("[,\n]"))
                    .map(String::trim)
                    .filter(s -> !s.isBlank())
                    .limit(5)
                    .toList();
        } catch (Exception e) {
            // 兜底：重写失败时返回原始查询
            log.warn("Query rewriting failed, using original query: {}", query, e);
            return List.of(query);
        }
    }
}
