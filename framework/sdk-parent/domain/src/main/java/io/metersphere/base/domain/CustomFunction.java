package io.metersphere.base.domain;

import lombok.Data;

import java.io.Serializable;

@Data
public class CustomFunction implements Serializable {
    private String id;

    private String name;

    private String tags;

    private String description;

    private String type;

    private String createUser;

    private Long createTime;

    private Long updateTime;

    private String projectId;

    private static final long serialVersionUID = 1L;
}