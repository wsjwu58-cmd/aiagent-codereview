package com.heima.codereview.tools.sonar;

import org.springframework.stereotype.Component;

@Component
public class SonarResultParser {

    public String parseSummary(String rawJson) {
        if (rawJson == null || rawJson.isBlank()) {
            return "Sonar扫描无结果";
        }
        return "Sonar扫描完成，结果摘要: " + rawJson;
    }
}
