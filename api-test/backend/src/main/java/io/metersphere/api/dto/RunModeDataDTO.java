package io.metersphere.api.dto;

import io.metersphere.api.dto.automation.ApiScenarioReportResult;
import io.metersphere.base.domain.ApiScenarioWithBLOBs;
import io.metersphere.base.domain.UiScenarioWithBLOBs;
import lombok.Getter;
import lombok.Setter;

import java.util.Map;

@Getter
@Setter
public class RunModeDataDTO {
    // 执行HashTree
    private ApiScenarioWithBLOBs scenario;
    private UiScenarioWithBLOBs uiScenario;
    // 测试场景/测试用例
    private String testId;

    private String reportId;
    // 初始化报告
    private ApiScenarioReportResult report;
    //
    private String apiCaseId;

    private Map<String, String> planEnvMap;

    public RunModeDataDTO() {

    }
}
