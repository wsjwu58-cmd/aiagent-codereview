package com.heima.codereview.common.persistence.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;

@TableName("review_suggestions")
public class ReviewSuggestionDO {

    @TableId(type = IdType.AUTO)
    private Long id;

    @TableField("review_id")
    private String reviewId;

    @TableField("priority_no")
    private Integer priorityNo;

    private String title;

    private String description;

    public Long getId() {
        return id;
    }

    public void setId(Long id) {
        this.id = id;
    }

    public String getReviewId() {
        return reviewId;
    }

    public void setReviewId(String reviewId) {
        this.reviewId = reviewId;
    }

    public Integer getPriorityNo() {
        return priorityNo;
    }

    public void setPriorityNo(Integer priorityNo) {
        this.priorityNo = priorityNo;
    }

    public String getTitle() {
        return title;
    }

    public void setTitle(String title) {
        this.title = title;
    }

    public String getDescription() {
        return description;
    }

    public void setDescription(String description) {
        this.description = description;
    }
}
