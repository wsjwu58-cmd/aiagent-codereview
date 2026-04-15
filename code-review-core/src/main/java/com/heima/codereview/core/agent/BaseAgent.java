package com.heima.codereview.core.agent;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public abstract class BaseAgent implements Agent {

    protected final Logger log = LoggerFactory.getLogger(getClass());

    @Override
    public String getId() {
        return getClass().getSimpleName();
    }
}
