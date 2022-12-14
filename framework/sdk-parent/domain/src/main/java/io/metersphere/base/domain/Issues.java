package io.metersphere.base.domain;

import lombok.Data;

import java.io.Serializable;

@Data
public class Issues implements Serializable {
    private String id;

    private String title;

    private String status;

    private Long createTime;

    private Long updateTime;

    private String reporter;

    private String lastmodify;

    private String platform;

    private String projectId;

    private String creator;

    private String resourceId;

    private Integer num;

    private String platformStatus;

    private String platformId;

    private static final long serialVersionUID = 1L;
}