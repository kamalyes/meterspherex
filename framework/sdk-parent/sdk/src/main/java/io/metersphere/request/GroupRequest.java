package io.metersphere.request;

import lombok.Data;

@Data
public class GroupRequest {
    private String resourceId;
    private String projectId;
    private String type;
}
