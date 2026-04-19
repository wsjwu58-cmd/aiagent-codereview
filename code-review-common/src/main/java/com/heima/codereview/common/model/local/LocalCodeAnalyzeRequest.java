package com.heima.codereview.common.model.local;

import java.util.List;

public record LocalCodeAnalyzeRequest(
        String folderPath,
        List<String> fileFilters,
        Boolean autoWrite
) {
}
