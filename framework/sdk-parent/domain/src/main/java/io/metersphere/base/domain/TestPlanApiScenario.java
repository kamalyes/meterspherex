package io.metersphere.base.domain;

import lombok.Data;

import java.io.Serializable;

@Data
public class TestPlanApiScenario implements Serializable {
    private String id;

    private String testPlanId;

    private String apiScenarioId;

    private String status;

    private Long createTime;

    private Long updateTime;

    private String passRate;

    private String lastResult;

    private String reportId;

    private String createUser;

    private Long order;

    private String environmentType;

    private String environmentGroupId;

    private String environment;

    private static final long serialVersionUID = 1L;
}