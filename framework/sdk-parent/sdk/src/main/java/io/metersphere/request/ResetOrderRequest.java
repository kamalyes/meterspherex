package io.metersphere.request;

import lombok.Data;

@Data
public class ResetOrderRequest {
    private String moveId;
    private String targetId;
    private String moveMode;

    // 项目id或者测试计划id
    private String groupId;

    public enum MoveMode {
        BEFORE,
        AFTER,
        APPEND // 脑图添加多个用例时，后面的用例类型
    }
}
