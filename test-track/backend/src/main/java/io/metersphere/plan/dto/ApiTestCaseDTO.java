package io.metersphere.plan.dto;

import io.metersphere.base.domain.ApiTestCase;
import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
public class ApiTestCaseDTO extends ApiTestCase {
    private String moduleId;
    private String path;
    private String protocol;
    private String updateUser;
    private String createUser;
    private String deleteUser;
    private String apiName;
    private String passRate;
    private String projectName;
    private String environment;
    private String execResult;
    private String versionName;
    private boolean updated;
}
