package com.heima.codereview.core.coordination;

import com.heima.codereview.core.agent.Agent;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

@Component
public class AgentRegistry {

    private final Map<String, Agent> agentMap = new ConcurrentHashMap<>();

    public AgentRegistry(List<Agent> agents) {
        for (Agent agent : agents) {
            agentMap.put(agent.getId(), agent);
        }
    }

    public Agent getById(String id) {
        return agentMap.get(id);
    }

    public Map<String, Agent> all() {
        return agentMap;
    }
}
