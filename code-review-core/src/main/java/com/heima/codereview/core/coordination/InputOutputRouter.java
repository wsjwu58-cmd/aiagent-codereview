package com.heima.codereview.core.coordination;

import org.springframework.stereotype.Component;

@Component
public class InputOutputRouter {

    public String route(String inputType) {
        if ("review".equalsIgnoreCase(inputType)) {
            return "FlowAgent";
        }
        if ("chat".equalsIgnoreCase(inputType)) {
            return "AdvisorAgent";
        }
        return "FlowAgent";
    }
}
