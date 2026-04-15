package com.heima.codereview.tools.refactor;

import org.springframework.stereotype.Component;

import java.util.List;

@Component
public class CodeRefactorer {

    public String refactor(String originalCode, List<String> suggestions) {
        String code = originalCode == null ? "" : originalCode;
        String merged = String.join("; ", suggestions);
        return "// 自动重构建议: " + merged + "\n" + code.replace("System.out.println", "log.info");
    }
}
