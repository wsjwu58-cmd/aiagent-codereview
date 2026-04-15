package com.heima.codereview.api.service;

import com.heima.codereview.common.model.template.ReviewTemplate;
import org.springframework.stereotype.Service;

import java.util.List;

@Service
public class ReviewTemplateService {

    public List<ReviewTemplate> listBuiltInTemplates() {
        return List.of(
                template("tpl-java-standard", "Java标准审查", "Java 17+项目默认审查模板", "java", 12),
                template("tpl-spring-best-practice", "Spring Boot最佳实践", "Spring应用开发规范模板", "java", 8),
                template("tpl-security", "安全漏洞扫描", "面向通用项目的安全专项检查", "all", 15),
                template("tpl-performance", "性能问题检测", "面向通用项目的性能专项检查", "all", 10),
                template("tpl-style", "代码风格检查", "面向通用项目的风格规范检查", "all", 6)
        );
    }

    private ReviewTemplate template(String id, String name, String desc, String lang, int size) {
        ReviewTemplate t = new ReviewTemplate();
        t.setId(id);
        t.setName(name);
        t.setDescription(desc);
        t.setLanguage(lang);
        t.setEnabled(true);
        t.setRules(java.util.stream.IntStream.rangeClosed(1, size)
                .mapToObj(i -> {
                    ReviewTemplate.RuleConfig cfg = new ReviewTemplate.RuleConfig();
                    cfg.setRuleId(id + "-rule-" + i);
                    cfg.setRuleName(name + "规则" + i);
                    cfg.setSeverity(i <= 2 ? "CRITICAL" : i <= 5 ? "HIGH" : i <= 9 ? "MEDIUM" : "LOW");
                    cfg.setParams(java.util.Map.of("weight", i));
                    return cfg;
                }).toList());
        return t;
    }
}
