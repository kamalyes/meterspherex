package io.metersphere.base.domain;

import lombok.Data;

import java.io.Serializable;

@Data
public class TestCaseComment implements Serializable {
    private String id;

    private String caseId;

    private String author;

    private Long createTime;

    private Long updateTime;

    private String status;

    private String type;

    private String description;

    private static final long serialVersionUID = 1L;
}