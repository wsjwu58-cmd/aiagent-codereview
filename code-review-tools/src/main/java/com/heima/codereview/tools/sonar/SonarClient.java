package com.heima.codereview.tools.sonar;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestTemplate;
import org.springframework.http.*;
import org.springframework.util.LinkedMultiValueMap;
import org.springframework.util.MultiValueMap;

import java.util.ArrayList;
import java.util.List;

@Component
public class SonarClient {

    private static final Logger log = LoggerFactory.getLogger(SonarClient.class);
    private static final ObjectMapper OBJECT_MAPPER = new ObjectMapper();

    @Value("${sonar.url:http://localhost:9000}")
    private String sonarUrl;

    @Value("${sonar.token:}")
    private String token;

    @Value("${sonar.project-key:}")
    private String defaultProjectKey;

    public String scan(String projectKey, String branch) {
        String actualProjectKey = (projectKey != null && !projectKey.isBlank()) ? projectKey : defaultProjectKey;
        
        if (actualProjectKey == null || actualProjectKey.isBlank()) {
            return "未提供项目标识，无法执行 Sonar 扫描。";
        }

        try {
            String apiUrl = String.format("%s/api/issues/search", sonarUrl);
            
            String urlWithParams = apiUrl + "?projectKeys=" + actualProjectKey 
                    + "&types=VULNERABILITY,BUG,CODE_SMELL"
                    + "&severities=CRITICAL,MAJOR"
                    + "&languages=JAVA,PYTHON,JAVASCRIPT,TYPESCRIPT,GO,KOTLIN"
                    + "&limit=50";

            if (branch != null && !branch.isBlank()) {
                urlWithParams += "&branch=" + branch;
            }

            HttpHeaders headers = new HttpHeaders();
            if (token != null && !token.isBlank()) {
                headers.setBearerAuth(token);
            }
            headers.setContentType(MediaType.APPLICATION_JSON);

            RestTemplate restTemplate = new RestTemplate();
            HttpEntity<String> entity = new HttpEntity<>(headers);

            log.info("开始调用 SonarQube API: {}", urlWithParams);
            
            ResponseEntity<String> response = restTemplate.exchange(
                    urlWithParams,
                    HttpMethod.GET,
                    entity,
                    String.class
            );

            return formatSonarResults(response.getBody(), actualProjectKey);

        } catch (Exception e) {
            log.error("Sonar扫描失败: {}", e.getMessage());
            return "Sonar扫描失败: " + e.getMessage();
        }
    }

    private String formatSonarResults(String json, String projectKey) {
        if (json == null || json.isBlank()) {
            return "Sonar扫描返回为空";
        }

        try {
            JsonNode root = OBJECT_MAPPER.readTree(json);
            JsonNode issues = root.path("issues");

            if (issues.isMissingNode() || issues.isArray() == false) {
                return "未从 SonarQube 获取到问题数据";
            }

            List<String> results = new ArrayList<>();
            int totalCount = root.path("total").asInt(0);
            
            results.add("========== SonarQube 扫描结果 ==========");
            results.add("项目: " + projectKey);
            results.add("总问题数: " + totalCount);
            results.add("");

            int criticalCount = 0;
            int highCount = 0;
            int majorCount = 0;
            int displayed = 0;

            for (JsonNode issue : issues) {
                if (displayed >= 20) {
                    break;
                }

                String severity = issue.path("severity").asText();
                String type = issue.path("type").asText();
                String message = issue.path("message").asText();
                String rule = issue.path("rule").asText();
                String file = issue.path("component").asText();
                int line = issue.path("line").asInt(0);

                // 简化文件路径
                if (file.contains(":")) {
                    file = file.substring(file.lastIndexOf(":") + 1);
                }

                StringBuilder sb = new StringBuilder();
                sb.append("[").append(severity).append("] ");
                sb.append("(").append(type).append(")");
                results.add(sb.toString());
                results.add("  文件: " + file + (line > 0 ? ":" + line : ""));
                results.add("  问题: " + message);
                results.add("  规则: " + rule);
                results.add("");

                if ("CRITICAL".equals(severity)) {
                    criticalCount++;
                } else if ("HIGH".equals(severity)) {
                    highCount++;
                } else if ("MAJOR".equals(severity)) {
                    majorCount++;
                }

                displayed++;
            }

            results.add("========== 问题汇总 ==========");
            results.add("CRITICAL: " + criticalCount + " | HIGH: " + highCount + " | MAJOR: " + majorCount);
            
            if (totalCount > 20) {
                results.add("");
                results.add("(仅显示前20条问题，更多问题请访问 SonarQube)");
            }

            return String.join("\n", results);

        } catch (Exception e) {
            log.error("解析Sonar结果失败: {}", e.getMessage());
            return "解析Sonar结果失败: " + e.getMessage();
        }
    }
}
