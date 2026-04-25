package com.heima.codereview.rag.retrieval;

import com.heima.codereview.rag.model.ReviewRecord;
import com.heima.codereview.rag.vector.MilvusRepository;
import org.springframework.stereotype.Service;

import java.util.*;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * 混合检索服务 - 结合语义检索与关键词检索
 *
 * <p>核心思想：通过融合多种检索方法提升召回率
 * <ul>
 *   <li>语义检索：基于向量相似度（Milvus）</li>
 *   <li>关键词检索：基于BM25算法</li>
 *   <li>RRF融合：综合两种检索结果进行排序</li>
 * </ul>
 *
 * <p>融合公式：score(d) = Σ weight / (k + rank_i(d))
 * <p>其中 k=60，weight语义=1.0，weight关键词=1.15
 */
@Service
public class HybridSearch {

    /** BM25关键词召回的候选文档数量上限 */
    private static final int KEYWORD_CANDIDATE_LIMIT = 200;

    /** BM25词频饱和度参数 - 控制词频增长对得分的影响 */
    private static final double BM25_K1 = 1.2d;

    /** BM25文档长度归一化参数 - 0.75表示中等程度的归一化 */
    private static final double BM25_B = 0.75d;

    /** 分词正则：匹配中文词(2字及以上)或英文数字符号组合 */
    private static final Pattern TOKEN_PATTERN = Pattern.compile("[\\p{IsHan}]{2,}|[a-zA-Z0-9_./#:-]+");

    /** 向量数据库操作类 */
    private final MilvusRepository milvusRepository;

    public HybridSearch(MilvusRepository milvusRepository) {
        this.milvusRepository = milvusRepository;
    }

    /**
     * 混合检索入口
     *
     * @param query     用户查询文本
     * @param projectId 项目ID（用于过滤）
     * @param sessionId 会话ID（用于过滤）
     * @param topK      返回结果数量
     * @return 融合排序后的审查记录列表
     *
     * <p>检索流程：
     * <ol>
     *   <li>语义召回：从Milvus获取向量相似度最高的记录</li>
     *   <li>关键词召回：使用BM25算法在本地缓存中搜索</li>
     *   <li>RRF融合：对两种结果进行排名融合</li>
     *   <li>排序返回：按融合分数降序返回TopK</li>
     * </ol>
     */
    public List<ReviewRecord> search(String query, String projectId, String sessionId, int topK) {
        if (query == null || query.isBlank()) {
            return List.of();
        }
        int limit = Math.max(1, topK);

        List<ReviewRecord> nativeHybridResults = milvusRepository.hybridSearch(query, projectId, sessionId, limit);
        if (!nativeHybridResults.isEmpty()) {
            return nativeHybridResults.stream().limit(limit).toList();
        }

        // 第1步：语义检索（Milvus向量库）
        List<ReviewRecord> semanticResults = milvusRepository.search(query, projectId, sessionId, Math.max(limit * 3, limit));

        // 第2步：关键词检索（BM25）
        List<ReviewRecord> lexicalResults = keywordSearch(query, projectId, sessionId, Math.max(limit * 3, limit));

        // 第3步：RRF融合
        Map<String, Double> fusedScores = new HashMap<>();
        Map<String, ReviewRecord> merged = new LinkedHashMap<>();

        // 语义结果权重1.0，关键词结果权重1.15（关键词略优先）
        applyRrf(semanticResults, fusedScores, merged, 1.0d);
        applyRrf(lexicalResults, fusedScores, merged, 1.15d);

        // 第4步：按融合分数降序排序返回
        return fusedScores.entrySet().stream()
                .sorted((left, right) -> Double.compare(right.getValue(), left.getValue()))
                .map(entry -> merged.get(entry.getKey()))
                .limit(limit)
                .toList();
    }

    /**
     * BM25关键词检索
     *
     * <p>BM25是一种经典的信息检索算法，综合考虑：
     * <ul>
     *   <li>词频（TF）：词在文档中出现次数越多，相关性越高</li>
     *   <li>逆文档频率（IDF）：词在语料库中越罕见，区分度越高</li>
     *   <li>文档长度归一化：避免长文档天然包含更多词的问题</li>
     * </ul>
     *
     * <p>公式：score = Σ IDF(qi) × (tf(qi,D) × (k1+1)) / (tf(qi,D) + k1×(1-b+b×|D|/avgdl))
     */
    private List<ReviewRecord> keywordSearch(String query, String projectId, String sessionId, int topK) {
        // 第1步：分词提取查询词
        List<String> queryTerms = extractTerms(query);
        if (queryTerms.isEmpty()) {
            return List.of();
        }

        // 第2步：获取候选文档（从本地缓存）
        List<ReviewRecord> candidates = milvusRepository.cachedRecords(projectId, sessionId).stream()
                .limit(KEYWORD_CANDIDATE_LIMIT)
                .toList();
        if (candidates.isEmpty()) {
            return List.of();
        }

        // 第3步：构建文档词频向量和计算平均文档长度
        List<ScoredRecord> scored = new ArrayList<>();
        Map<String, Integer> documentFrequency = new HashMap<>();
        List<DocumentTermVector> vectors = new ArrayList<>();
        double avgDocLength = 0.0d;

        for (ReviewRecord candidate : candidates) {
            Map<String, Integer> termFreq = buildTermFrequency(candidate.content());
            int docLength = termFreq.values().stream().mapToInt(Integer::intValue).sum();
            if (docLength == 0) {
                continue;
            }
            vectors.add(new DocumentTermVector(candidate, termFreq, docLength));
            avgDocLength += docLength;

            // 统计文档频率（包含每个查询词的文档数）
            for (String term : queryTerms) {
                if (termFreq.containsKey(term)) {
                    documentFrequency.merge(term, 1, Integer::sum);
                }
            }
        }

        if (vectors.isEmpty()) {
            return List.of();
        }
        avgDocLength = avgDocLength / vectors.size();

        // 第4步：计算每个文档的BM25得分
        for (DocumentTermVector vector : vectors) {
            double score = 0.0d;
            for (String term : queryTerms) {
                Integer frequency = vector.termFrequency().get(term);
                if (frequency == null || frequency <= 0) {
                    continue;
                }

                // 计算IDF（逆文档频率）
                int df = Math.max(1, documentFrequency.getOrDefault(term, 1));
                double idf = Math.log1p((vectors.size() - df + 0.5d) / (df + 0.5d));

                // 计算BM25得分
                double denominator = frequency + BM25_K1 * (1 - BM25_B + BM25_B * (vector.docLength() / Math.max(avgDocLength, 1.0d)));
                score += idf * (frequency * (BM25_K1 + 1)) / denominator;
            }

            // 额外加分：如果文档包含完整查询短语
            String normalizedContent = normalize(vector.record().content());
            String normalizedQuery = normalize(query);
            if (!normalizedQuery.isBlank() && normalizedContent.contains(normalizedQuery)) {
                score += 0.8d;
            }

            if (score > 0) {
                scored.add(new ScoredRecord(vector.record(), score));
            }
        }

        // 第5步：按得分降序返回
        return scored.stream()
                .sorted((left, right) -> Double.compare(right.score(), left.score()))
                .map(ScoredRecord::record)
                .limit(Math.max(1, topK))
                .toList();
    }

    /**
     * RRF（Reciprocal Rank Fusion）排名融合
     *
     * <p>一种简单而有效的多路召回融合方法。
     * 对于每条文档，将其排名转换为得分并累加。
     *
     * <p>公式：score(d) = Σ weight / (k + rank_i(d))
     *
     * @param results     检索结果列表（已按各自分数排序）
     * @param fusedScores 融合分数累积map
     * @param merged      文档ID到文档的映射
     * @param weight      该路检索的权重
     */
    private void applyRrf(List<ReviewRecord> results,
                          Map<String, Double> fusedScores,
                          Map<String, ReviewRecord> merged,
                          double weight) {
        for (int index = 0; index < results.size(); index++) {
            ReviewRecord record = results.get(index);
            merged.putIfAbsent(record.id(), record);

            // 排名转换为分数：排第1名得 weight/(k+1) 分，排第2名得 weight/(k+2) 分...
            double score = weight / (60.0d + index + 1);
            fusedScores.merge(record.id(), score, Double::sum);
        }
    }

    /**
     * 构建文档词频向量
     *
     * @param content 文档内容
     * @return 词→频次的映射
     */
    private Map<String, Integer> buildTermFrequency(String content) {
        Map<String, Integer> termFrequency = new HashMap<>();
        for (String term : extractTerms(content)) {
            termFrequency.merge(term, 1, Integer::sum);
        }
        return termFrequency;
    }

    /**
     * 文本分词提取
     *
     * <p>分词策略：
     * <ul>
     *   <li>中文：提取完整词（≤4字）+ 所有2-gram组合</li>
     *   <li>英文：直接提取作为token</li>
     * </ul>
     *
     * <p>示例："代码太乱" → ["代码", "代", "码太", "太乱", "乱"]
     */
    private List<String> extractTerms(String text) {
        String normalized = normalize(text);
        if (normalized.isBlank()) {
            return List.of();
        }
        List<String> terms = new ArrayList<>();
        Matcher matcher = TOKEN_PATTERN.matcher(normalized);
        while (matcher.find()) {
            String token = matcher.group();
            if (token == null || token.isBlank()) {
                continue;
            }
            // 中文处理：单字 + 所有2-gram
            if (token.codePoints().allMatch(Character::isIdeographic)) {
                if (token.length() <= 4) {
                    terms.add(token);
                }
                for (int index = 0; index + 1 < token.length(); index++) {
                    terms.add(token.substring(index, index + 2));
                }
                continue;
            }
            // 英文/数字处理：长度>1才保留
            if (token.length() > 1) {
                terms.add(token);
            }
        }
        return terms;
    }

    /**
     * 文本标准化
     *
     * <p>处理步骤：
     * <ul>
     *   <li>转小写</li>
     *   <li>替换换行符为空格</li>
     *   <li>去除首尾空白</li>
     * </ul>
     */
    private String normalize(String text) {
        return text == null ? "" : text.toLowerCase().replace('\r', ' ').replace('\n', ' ').trim();
    }

    /** 文档词频向量记录 */
    private record DocumentTermVector(ReviewRecord record, Map<String, Integer> termFrequency, int docLength) {
    }

    /** 带BM25得分的记录 */
    private record ScoredRecord(ReviewRecord record, double score) {
    }
}
