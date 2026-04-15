package com.heima.codereview.common.persistence.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;

@TableName("similar_code_blocks")
public class SimilarCodeBlockDO {

    @TableId(type = IdType.AUTO)
    private Long id;

    @TableField("group_id")
    private String groupId;

    @TableField("block_content")
    private String blockContent;

    public Long getId() {
        return id;
    }

    public void setId(Long id) {
        this.id = id;
    }

    public String getGroupId() {
        return groupId;
    }

    public void setGroupId(String groupId) {
        this.groupId = groupId;
    }

    public String getBlockContent() {
        return blockContent;
    }

    public void setBlockContent(String blockContent) {
        this.blockContent = blockContent;
    }
}
