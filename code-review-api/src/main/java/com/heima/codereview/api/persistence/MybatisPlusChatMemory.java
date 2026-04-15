package com.heima.codereview.api.persistence;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.heima.codereview.common.model.chat.ChatMessage;
import com.heima.codereview.common.persistence.entity.ChatMessageDO;
import com.heima.codereview.common.persistence.mapper.ChatMessageMapper;
import com.heima.codereview.common.utils.IdUtils;
import com.heima.codereview.core.memory.ChatMemory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Primary;
import org.springframework.stereotype.Component;

import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;

@Component
@Primary
public class MybatisPlusChatMemory implements ChatMemory {

    private final ChatMessageMapper chatMessageMapper;
    private final int windowSize;

    public MybatisPlusChatMemory(ChatMessageMapper chatMessageMapper,
                                 @Value("${code-review.memory.window-size:20}") int windowSize) {
        this.chatMessageMapper = chatMessageMapper;
        this.windowSize = windowSize;
    }

    @Override
    public void append(String sessionId, ChatMessage message) {
        ChatMessageDO entity = new ChatMessageDO();
        entity.setMessageId(IdUtils.withPrefix("msg"));
        entity.setSessionId(sessionId);
        entity.setRole(message.role());
        entity.setContent(message.content());
        entity.setPromptTokens(0);
        entity.setCompletionTokens(0);
        entity.setTotalTokens(0);
        entity.setCreatedAt(LocalDateTime.ofInstant(Instant.ofEpochMilli(message.timestamp()), ZoneId.systemDefault()));
        chatMessageMapper.insert(entity);
    }

    @Override
    public List<ChatMessage> recent(String sessionId) {
        List<ChatMessageDO> rows = chatMessageMapper.selectList(new LambdaQueryWrapper<ChatMessageDO>()
                .eq(ChatMessageDO::getSessionId, sessionId)
                .orderByDesc(ChatMessageDO::getCreatedAt)
                .last("limit " + windowSize));
        List<ChatMessage> messages = new ArrayList<>(rows.size());
        for (ChatMessageDO row : rows) {
            long timestamp = row.getCreatedAt() == null
                    ? System.currentTimeMillis()
                    : row.getCreatedAt().atZone(ZoneId.systemDefault()).toInstant().toEpochMilli();
            messages.add(new ChatMessage(row.getRole(), row.getContent(), timestamp));
        }
        messages.sort(Comparator.comparingLong(ChatMessage::timestamp));
        return messages;
    }
}
