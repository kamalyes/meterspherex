package io.metersphere.service;

import io.metersphere.api.dto.automation.ApiTestReportVariable;
import io.metersphere.api.exec.scenario.ApiEnvironmentRunningParamService;
import io.metersphere.base.domain.ApiDefinitionExecResultExample;
import io.metersphere.base.domain.ApiDefinitionExecResultWithBLOBs;
import io.metersphere.base.domain.ApiScenarioReport;
import io.metersphere.base.domain.ApiScenarioWithBLOBs;
import io.metersphere.base.mapper.ApiDefinitionExecResultMapper;
import io.metersphere.base.mapper.ApiScenarioMapper;
import io.metersphere.commons.constants.ApiRunMode;
import io.metersphere.commons.constants.NoticeConstants;
import io.metersphere.commons.constants.PropertyConstant;
import io.metersphere.commons.constants.ReportTriggerMode;
import io.metersphere.commons.enums.ApiReportStatus;
import io.metersphere.commons.utils.CommonBeanFactory;
import io.metersphere.commons.utils.DateUtils;
import io.metersphere.constants.RunModeConstants;
import io.metersphere.dto.BaseSystemConfigDTO;
import io.metersphere.dto.ResultDTO;
import io.metersphere.notice.sender.NoticeModel;
import io.metersphere.notice.service.NoticeSendService;
import io.metersphere.service.definition.ApiDefinitionExecResultService;
import io.metersphere.service.scenario.ApiScenarioExecutionInfoService;
import io.metersphere.service.scenario.ApiScenarioReportService;
import io.metersphere.service.scenario.ApiScenarioReportStructureService;
import io.metersphere.service.scenario.ApiScenarioService;
import org.apache.commons.beanutils.BeanMap;
import org.apache.commons.collections4.CollectionUtils;
import org.apache.commons.lang3.StringUtils;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import javax.annotation.Resource;
import java.util.*;

@Service
@Transactional(rollbackFor = Exception.class)
public class TestResultService {
    @Resource
    private ApiDefinitionExecResultService apiDefinitionExecResultService;
    @Resource
    private ApiScenarioReportService apiScenarioReportService;
    @Resource
    private ApiScenarioService apiAutomationService;
    @Resource
    private ApiScenarioMapper apiScenarioMapper;
    @Resource
    private ApiDefinitionExecResultMapper apiDefinitionExecResultMapper;
    @Resource
    private ApiEnvironmentRunningParamService apiEnvironmentRunningParamService;
    @Resource
    private RedisTemplateService redisTemplateService;
    @Resource
    private ApiScenarioExecutionInfoService scenarioExecutionInfoService;
    @Resource
    private ApiScenarioReportStructureService apiScenarioReportStructureService;

    // ??????
    private static final List<String> scenarioRunModes = new ArrayList<>() {{
        this.add(ApiRunMode.SCENARIO.name());
        this.add(ApiRunMode.SCENARIO_PLAN.name());
        this.add(ApiRunMode.SCHEDULE_SCENARIO_PLAN.name());
        this.add(ApiRunMode.SCHEDULE_SCENARIO.name());
        this.add(ApiRunMode.JENKINS_SCENARIO_PLAN.name());
    }};

    // ???????????? ??????/??????
    private static final List<String> caseRunModes = new ArrayList<>() {{
        this.add(ApiRunMode.DEFINITION.name());
        this.add(ApiRunMode.JENKINS.name());
        this.add(ApiRunMode.API_PLAN.name());
    }};

    // ???????????? ??????/??????
    private static final List<String> planCaseRunModes = new ArrayList<>() {{
        this.add(ApiRunMode.SCHEDULE_API_PLAN.name());
        this.add(ApiRunMode.JENKINS_API_PLAN.name());
        this.add(ApiRunMode.MANUAL_PLAN.name());
    }};

    /**
     * ??????????????????
     *
     * @param dto ????????????
     */
    public void saveResults(ResultDTO dto) {
        // ????????????
        List<String> environmentList = new LinkedList<>();
        if (dto.getArbitraryData() != null && dto.getArbitraryData().containsKey("ENV")) {
            environmentList = (List<String>) dto.getArbitraryData().get("ENV");
        }
        //??????????????????
        if (CollectionUtils.isNotEmpty(environmentList)) {
            apiEnvironmentRunningParamService.parseEnvironment(environmentList);
        }

        // ????????????????????????????????????
        if (planCaseRunModes.contains(dto.getRunMode())) {
            apiDefinitionExecResultService.saveApiResultByScheduleTask(dto);
        } else if (caseRunModes.contains(dto.getRunMode())) {
            // ????????????/???????????? ??????????????????
            apiDefinitionExecResultService.saveApiResult(dto);
        } else if (scenarioRunModes.contains(dto.getRunMode())) {
            // ????????????????????????
            apiScenarioReportService.saveResult(dto);
        }
    }

    /**
     * ??????????????????NODE/K8s???????????????
     */
    public void batchSaveResults(Map<String, List<ResultDTO>> resultDtoMap) {
        // ????????????
        List<String> environmentList = new LinkedList<>();
        for (String key : resultDtoMap.keySet()) {
            List<ResultDTO> resultDTOS = resultDtoMap.get(key);
            for (ResultDTO dto : resultDTOS) {
                if (dto.getArbitraryData() != null && dto.getArbitraryData().containsKey("ENV")) {
                    environmentList = (List<String>) dto.getArbitraryData().get("ENV");
                }
                //??????????????????
                if (CollectionUtils.isNotEmpty(environmentList)) {
                    apiEnvironmentRunningParamService.parseEnvironment(environmentList);
                }
                // ?????????????????????console??????
                if (StringUtils.isNotEmpty(dto.getConsole()) && StringUtils.equals(dto.getReportType(), RunModeConstants.SET_REPORT.toString())) {
                    String reportId = dto.getReportId();
                    if (StringUtils.equalsIgnoreCase(dto.getRunMode(), ApiRunMode.DEFINITION.name())) {
                        reportId = dto.getTestPlanReportId();
                    }
                    apiScenarioReportStructureService.update(reportId, dto.getConsole(), true);
                }
            }
            //????????????????????????-??????????????????????????????????????????????????????????????????
            if (StringUtils.equals(key, "schedule-task")) {
                apiDefinitionExecResultService.batchSaveApiResult(resultDTOS, true);
            } else if (StringUtils.equals(key, "api-test-case-task")) {
                apiDefinitionExecResultService.batchSaveApiResult(resultDTOS, false);
            } else if (StringUtils.equalsAny(key, "api-scenario-task")) {
                apiScenarioReportService.batchSaveResult(resultDTOS);
            }

        }
    }

    public void testEnded(ResultDTO dto) {
        // ?????????????????????
        if (StringUtils.equals(dto.getRunType(), RunModeConstants.SERIAL.toString())) {
            String key = StringUtils.join(RunModeConstants.SERIAL.name(), "_", dto.getReportId());
            redisTemplateService.delete(key);
        }
        if (dto.getRequestResults() == null) {
            dto.setRequestResults(new LinkedList<>());
        }
        if (scenarioRunModes.contains(dto.getRunMode())) {
            ApiScenarioReport scenarioReport = apiScenarioReportService.testEnded(dto);
            if (scenarioReport != null) {
                String environment = StringUtils.EMPTY;
                //?????????
                String userName = StringUtils.EMPTY;
                //?????????
                String principal = StringUtils.EMPTY;

                ApiScenarioWithBLOBs apiScenario = apiScenarioMapper.selectByPrimaryKey(scenarioReport.getScenarioId());
                if (apiScenario != null) {
                    if (StringUtils.equalsAnyIgnoreCase(dto.getRunMode(), ApiRunMode.SCENARIO_PLAN.name(), ApiRunMode.SCHEDULE_SCENARIO_PLAN.name(), ApiRunMode.JENKINS_SCENARIO_PLAN.name())) {
                        scenarioExecutionInfoService.insertExecutionInfo(dto.getTestId(), scenarioReport.getStatus(), scenarioReport.getTriggerMode());
                    } else {
                        scenarioExecutionInfoService.insertExecutionInfo(scenarioReport.getScenarioId(), scenarioReport.getStatus(), scenarioReport.getTriggerMode());
                    }
                    environment = apiScenarioReportService.getEnvironment(apiScenario);
                    userName = apiAutomationService.getUser(apiScenario.getUserId());
                    principal = apiAutomationService.getUser(apiScenario.getPrincipal());
                }

                //????????????
                ApiTestReportVariable reportTask = new ApiTestReportVariable();
                reportTask.setStatus(scenarioReport.getStatus());
                reportTask.setId(scenarioReport.getId());
                reportTask.setTriggerMode(scenarioReport.getTriggerMode());
                reportTask.setName(scenarioReport.getName());
                reportTask.setExecutor(userName);
                reportTask.setUserId(scenarioReport.getUserId());
                reportTask.setPrincipal(principal);
                reportTask.setExecutionTime(DateUtils.getTimeString(scenarioReport.getUpdateTime()));
                reportTask.setEnvironment(environment);
                reportTask.setProjectId(scenarioReport.getProjectId());

                if (reportTask != null) {
                    if (StringUtils.equals(ReportTriggerMode.API.name(), reportTask.getTriggerMode()) || StringUtils.equals(ReportTriggerMode.SCHEDULE.name(), reportTask.getTriggerMode())) {
                        sendTask(reportTask, dto.getTestId());
                    }
                }
            }
        } else if (StringUtils.equalsAnyIgnoreCase(dto.getRunMode(), ApiRunMode.DEFINITION.name(), ApiRunMode.API_PLAN.name(), ApiRunMode.SCHEDULE_API_PLAN.name())) {
            ApiDefinitionExecResultWithBLOBs record = new ApiDefinitionExecResultWithBLOBs();
            record.setId(dto.getReportId());
            record.setStatus(ApiReportStatus.STOPPED.name());

            ApiDefinitionExecResultExample example = new ApiDefinitionExecResultExample();
            example.createCriteria().andIdEqualTo(dto.getReportId()).andStatusEqualTo(ApiReportStatus.RUNNING.name());
            apiDefinitionExecResultMapper.updateByExampleSelective(record, example);
        }
    }

    private void sendTask(ApiTestReportVariable report, String testId) {
        if (report == null) {
            return;
        }
        SystemParameterService systemParameterService = CommonBeanFactory.getBean(SystemParameterService.class);
        NoticeSendService noticeSendService = CommonBeanFactory.getBean(NoticeSendService.class);
        assert systemParameterService != null;
        assert noticeSendService != null;
        BaseSystemConfigDTO baseSystemConfigDTO = systemParameterService.getBaseInfo();
        String reportUrl = baseSystemConfigDTO.getUrl() + "/#/api/automation/report/view/" + report.getId();

        String subject = StringUtils.EMPTY;
        String event = StringUtils.EMPTY;
        String successContext = "${operator}???????????????????????????: ${name}" + ", ??????: ${reportUrl}";
        String failedContext = "${operator}???????????????????????????: ${name}" + ", ??????: ${reportUrl}";

        if (StringUtils.equals(ReportTriggerMode.API.name(), report.getTriggerMode())) {
            subject = "Jenkins????????????";
        }
        if (StringUtils.equals(ReportTriggerMode.SCHEDULE.name(), report.getTriggerMode())) {
            subject = "????????????";
        }
        if (StringUtils.equalsIgnoreCase(ApiReportStatus.SUCCESS.name(), report.getStatus())) {
            event = NoticeConstants.Event.EXECUTE_SUCCESSFUL;
        }
        if (StringUtils.equalsIgnoreCase(ApiReportStatus.ERROR.name(), report.getStatus())) {
            event = NoticeConstants.Event.EXECUTE_FAILED;
        }
        ApiScenarioWithBLOBs scenario = apiScenarioMapper.selectByPrimaryKey(testId);
        Map paramMap = new HashMap<>();
        paramMap.put(PropertyConstant.TYPE, "api");
        paramMap.put("url", baseSystemConfigDTO.getUrl());
        paramMap.put("reportUrl", reportUrl);
        paramMap.put("operator", report.getExecutor());
        paramMap.putAll(new BeanMap(report));
        paramMap.putAll(new BeanMap(scenario));
        NoticeModel noticeModel = NoticeModel.builder().operator(report.getUserId()).successContext(successContext).failedContext(failedContext).testId(testId).status(report.getStatus()).event(event).subject(subject).paramMap(paramMap).build();
        noticeSendService.send(report.getTriggerMode(), NoticeConstants.TaskType.API_DEFINITION_TASK, noticeModel);
    }
}
