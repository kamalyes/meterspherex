package io.metersphere.api.exec.api;


import io.metersphere.api.dto.ApiCaseRunRequest;
import io.metersphere.api.dto.EnvironmentType;
import io.metersphere.api.dto.RunModeConfigWithEnvironmentDTO;
import io.metersphere.api.dto.definition.ApiTestCaseRequest;
import io.metersphere.api.dto.definition.BatchRunDefinitionRequest;
import io.metersphere.api.dto.scenario.DatabaseConfig;
import io.metersphere.api.dto.scenario.environment.EnvironmentConfig;
import io.metersphere.api.exec.queue.DBTestQueue;
import io.metersphere.service.definition.ApiCaseResultService;
import io.metersphere.service.ApiExecutionQueueService;
import io.metersphere.service.scenario.ApiScenarioReportStructureService;
import io.metersphere.commons.enums.ApiReportStatus;
import io.metersphere.commons.utils.ApiDefinitionExecResultUtil;
import io.metersphere.commons.utils.GenerateHashTreeUtil;
import io.metersphere.base.domain.*;
import io.metersphere.base.mapper.ApiDefinitionExecResultMapper;
import io.metersphere.base.mapper.ApiTestCaseMapper;
import io.metersphere.base.mapper.plan.TestPlanApiCaseMapper;
import io.metersphere.base.mapper.ext.ExtApiTestCaseMapper;
import io.metersphere.commons.constants.*;
import io.metersphere.commons.exception.MSException;
import io.metersphere.commons.utils.BeanUtils;
import io.metersphere.commons.utils.CommonBeanFactory;
import io.metersphere.commons.utils.JSON;
import io.metersphere.constants.RunModeConstants;
import io.metersphere.dto.MsExecResponseDTO;
import io.metersphere.dto.RunModeConfigDTO;
import io.metersphere.environment.service.BaseEnvGroupProjectService;
import io.metersphere.environment.service.BaseEnvironmentService;
import io.metersphere.service.ServiceUtils;
import io.metersphere.utils.LoggerUtil;
import org.apache.commons.beanutils.BeanComparator;
import org.apache.commons.collections.CollectionUtils;
import org.apache.commons.collections.MapUtils;
import org.apache.commons.collections4.comparators.FixedOrderComparator;
import org.apache.commons.lang3.StringUtils;
import org.json.JSONObject;
import org.springframework.stereotype.Service;

import javax.annotation.Resource;
import java.util.*;
import java.util.stream.Collectors;

@Service
public class ApiCaseExecuteService {
    @Resource
    private ApiCaseSerialService apiCaseSerialService;
    @Resource
    private ApiExecutionQueueService apiExecutionQueueService;
    @Resource
    private ApiCaseParallelExecuteService apiCaseParallelExecuteService;
    @Resource
    private ExtApiTestCaseMapper extApiTestCaseMapper;
    @Resource
    private ApiTestCaseMapper apiTestCaseMapper;
    @Resource
    private BaseEnvGroupProjectService environmentGroupProjectService;
    @Resource
    private ApiCaseResultService apiCaseResultService;
    @Resource
    private ApiScenarioReportStructureService apiScenarioReportStructureService;
    @Resource
    private ApiDefinitionExecResultMapper apiDefinitionExecResultMapper;
    @Resource
    private TestPlanApiCaseMapper testPlanApiCaseMapper;


    /**
     * ????????????case??????
     *
     * @param request
     * @return
     */
    public List<MsExecResponseDTO> run(BatchRunDefinitionRequest request) {
        List<MsExecResponseDTO> responseDTOS = new LinkedList<>();
        if (CollectionUtils.isEmpty(request.getPlanIds())) {
            return responseDTOS;
        }
        if (request.getConfig() == null) {
            request.setConfig(new RunModeConfigDTO());
        }
        if (StringUtils.equals(EnvironmentType.GROUP.toString(), request.getConfig().getEnvironmentType()) && StringUtils.isNotEmpty(request.getConfig().getEnvironmentGroupId())) {
            request.getConfig().setEnvMap(environmentGroupProjectService.getEnvMap(request.getConfig().getEnvironmentGroupId()));
        }
        LoggerUtil.debug("??????????????????????????????");

        TestPlanApiCaseExample example = new TestPlanApiCaseExample();
        example.createCriteria().andIdIn(request.getPlanIds());
        example.setOrderByClause("`order` DESC");
        List<TestPlanApiCase> planApiCases = testPlanApiCaseMapper.selectByExample(example);
        if (CollectionUtils.isEmpty(planApiCases)) {
            return responseDTOS;
        }
        if (StringUtils.isEmpty(request.getTriggerMode())) {
            request.setTriggerMode(ApiRunMode.API_PLAN.name());
        }
        LoggerUtil.debug("??????????????????????????? " + planApiCases.size());

        Map<String, ApiDefinitionExecResultWithBLOBs> executeQueue = request.isRerun() ? request.getExecuteQueue() : new LinkedHashMap<>();
        String status = request.getConfig().getMode().equals(RunModeConstants.SERIAL.toString()) ? ApiReportStatus.PENDING.name()
                : ApiReportStatus.RUNNING.name();

        // ????????????
        List<String> apiCaseIds = planApiCases.stream().map(TestPlanApiCase::getApiCaseId).collect(Collectors.toList());
        ApiTestCaseExample caseExample = new ApiTestCaseExample();
        caseExample.createCriteria().andIdIn(apiCaseIds);
        List<ApiTestCase> apiTestCases = apiTestCaseMapper.selectByExample(caseExample);
        Map<String, ApiTestCase> caseMap = apiTestCases.stream().collect(Collectors.toMap(ApiTestCase::getId, a -> a, (k1, k2) -> k1));
        // ?????????
        String resourcePoolId = null;
        if (request.getConfig() != null && GenerateHashTreeUtil.isResourcePool(request.getConfig().getResourcePoolId()).isPool()) {
            resourcePoolId = request.getConfig().getResourcePoolId();
        }
        if (!request.isRerun()) {
            for (TestPlanApiCase testPlanApiCase : planApiCases) {
                //????????????????????????????????????
                RunModeConfigDTO runModeConfigDTO = new RunModeConfigDTO();
                BeanUtils.copyBean(runModeConfigDTO, request.getConfig());
                if (MapUtils.isEmpty(runModeConfigDTO.getEnvMap())) {
                    ApiTestCase testCase = caseMap.get(testPlanApiCase.getApiCaseId());
                    if (testCase != null) {
                        runModeConfigDTO.setEnvMap(new HashMap<>() {{
                            this.put(testCase.getProjectId(), testPlanApiCase.getEnvironmentId());
                        }});
                    }
                }
                ApiDefinitionExecResultWithBLOBs report = ApiDefinitionExecResultUtil.addResult(request, runModeConfigDTO, testPlanApiCase, status, caseMap, resourcePoolId);
                executeQueue.put(testPlanApiCase.getId(), report);
                responseDTOS.add(new MsExecResponseDTO(testPlanApiCase.getId(), report.getId(), request.getTriggerMode()));
                LoggerUtil.debug("????????????????????????????????????" + report.getName() + ", ID " + report.getId());
            }
            apiCaseResultService.batchSave(executeQueue);
        }

        LoggerUtil.debug("??????????????????????????????");
        String reportType = request.getConfig().getReportType();
        String poolId = request.getConfig().getResourcePoolId();
        String runMode = StringUtils.equals(request.getTriggerMode(), TriggerMode.MANUAL.name()) ? ApiRunMode.API_PLAN.name() : ApiRunMode.SCHEDULE_API_PLAN.name();
        DBTestQueue deQueue = apiExecutionQueueService.add(executeQueue, poolId, ApiRunMode.API_PLAN.name(), request.getPlanReportId(), reportType, runMode, request.getConfig());

        // ????????????????????????
        if (deQueue != null && deQueue.getDetail() != null) {
            Thread thread = new Thread(new Runnable() {
                @Override
                public void run() {
                    Thread.currentThread().setName("PLAN-CASE???" + request.getPlanReportId());
                    if (request.getConfig() != null && request.getConfig().getMode().equals(RunModeConstants.SERIAL.toString())) {
                        apiCaseSerialService.serial(deQueue);
                    } else {
                        apiCaseParallelExecuteService.parallel(executeQueue, request.getConfig(), deQueue, runMode);
                    }
                }
            });
            thread.start();
        }
        return responseDTOS;
    }

    public Map<String, List<String>> checkEnv(List<ApiTestCaseWithBLOBs> caseList) {
        Map<String, List<String>> projectEnvMap = new HashMap<>();
        if (CollectionUtils.isNotEmpty(caseList)) {
            StringBuilder builderHttp = new StringBuilder();
            StringBuilder builderTcp = new StringBuilder();
            for (int i = caseList.size() - 1; i >= 0; i--) {
                ApiTestCaseWithBLOBs apiCase = caseList.get(i);
                JSONObject apiCaseNew = new JSONObject(apiCase.getRequest());
                if (apiCaseNew.has(PropertyConstant.TYPE) && ElementConstants.HTTP_SAMPLER.equals(apiCaseNew.optString(PropertyConstant.TYPE))) {
                    if (!apiCaseNew.has("useEnvironment") || StringUtils.isEmpty(apiCaseNew.optString("useEnvironment"))) {
                        builderHttp.append(apiCase.getName()).append("; ");
                    } else {
                        //??????????????????ID
                        String envId = apiCaseNew.optString("useEnvironment");
                        if (projectEnvMap.containsKey(apiCase.getProjectId())) {
                            if (!projectEnvMap.get(apiCase.getProjectId()).contains(envId)) {
                                projectEnvMap.get(apiCase.getProjectId()).add(envId);
                            }
                        } else {
                            projectEnvMap.put(apiCase.getProjectId(), new ArrayList<>() {{
                                this.add(envId);
                            }});
                        }
                    }
                }
                if (apiCaseNew.has(PropertyConstant.TYPE) && ElementConstants.JDBC_SAMPLER.equals(apiCaseNew.optString(PropertyConstant.TYPE))) {
                    DatabaseConfig dataSource = null;
                    if (apiCaseNew.has("useEnvironment") && apiCaseNew.has("dataSourceId")) {
                        String environmentId = apiCaseNew.optString("useEnvironment");
                        String dataSourceId = apiCaseNew.optString("dataSourceId");
                        BaseEnvironmentService apiTestEnvironmentService = CommonBeanFactory.getBean(BaseEnvironmentService.class);
                        ApiTestEnvironmentWithBLOBs environment = apiTestEnvironmentService.get(environmentId);
                        EnvironmentConfig envConfig = null;
                        if (environment != null && environment.getConfig() != null) {
                            envConfig = JSON.parseObject(environment.getConfig(), EnvironmentConfig.class);
                            if (CollectionUtils.isNotEmpty(envConfig.getDatabaseConfigs())) {
                                for (DatabaseConfig item : envConfig.getDatabaseConfigs()) {
                                    if (item.getId().equals(dataSourceId)) {
                                        dataSource = item;
                                        //??????????????????ID
                                        if (projectEnvMap.containsKey(apiCase.getProjectId())) {
                                            if (!projectEnvMap.get(apiCase.getProjectId()).contains(environmentId)) {
                                                projectEnvMap.get(apiCase.getProjectId()).add(environmentId);
                                            }
                                        } else {
                                            projectEnvMap.put(apiCase.getProjectId(), new ArrayList<>() {{
                                                this.add(environmentId);
                                            }});
                                        }
                                    }
                                }
                            }
                        }
                    }
                    if (dataSource == null) {
                        builderTcp.append(apiCase.getName()).append("; ");
                    }
                }
            }
            if (StringUtils.isNotEmpty(builderHttp)) {
                MSException.throwException("?????????" + builderHttp + "??????????????????????????????");
            }
            if (StringUtils.isNotEmpty(builderTcp)) {
                MSException.throwException("?????????" + builderTcp + "???????????????????????????");
            }
        }
        return projectEnvMap;
    }

    public Map<String, String> getEnvMap(ApiTestCaseWithBLOBs apiCase) {
        Map<String, String> projectEnvMap = new HashMap<>();

        JSONObject apiCaseNew = new JSONObject(apiCase.getRequest());
        if (apiCaseNew.has(PropertyConstant.TYPE) && ElementConstants.HTTP_SAMPLER.equals(apiCaseNew.optString(PropertyConstant.TYPE))) {
            if (apiCaseNew.has("useEnvironment") && StringUtils.isNotEmpty(apiCaseNew.optString("useEnvironment"))) {
                //??????????????????ID
                String envId = apiCaseNew.optString("useEnvironment");
                projectEnvMap.put(apiCase.getProjectId(), envId);
            }
        }
        if (apiCaseNew.has(PropertyConstant.TYPE) && ElementConstants.JDBC_SAMPLER.equals(apiCaseNew.optString(PropertyConstant.TYPE))) {
            if (apiCaseNew.has("useEnvironment") && apiCaseNew.has("dataSourceId")) {
                String environmentId = apiCaseNew.optString("useEnvironment");
                String dataSourceId = apiCaseNew.optString("dataSourceId");
                BaseEnvironmentService apiTestEnvironmentService = CommonBeanFactory.getBean(BaseEnvironmentService.class);
                ApiTestEnvironmentWithBLOBs environment = apiTestEnvironmentService.get(environmentId);
                EnvironmentConfig envConfig = null;
                if (environment != null && environment.getConfig() != null) {
                    envConfig = JSON.parseObject(environment.getConfig(), EnvironmentConfig.class);
                    if (CollectionUtils.isNotEmpty(envConfig.getDatabaseConfigs())) {
                        for (DatabaseConfig item : envConfig.getDatabaseConfigs()) {
                            if (item.getId().equals(dataSourceId)) {
                                //??????????????????ID
                                projectEnvMap.put(apiCase.getProjectId(), environmentId);
                            }
                        }
                    }
                }
            }
        }
        return projectEnvMap;
    }

    /**
     * ????????????case??????
     *
     * @param request
     * @return
     */
    public List<MsExecResponseDTO> run(ApiCaseRunRequest request) {
        if (LoggerUtil.getLogger().isDebugEnabled()) {
            LoggerUtil.debug("???????????????????????????????????????" + JSON.toJSONString(request));
        }
        if (request.getConfig() == null) {
            request.setConfig(new RunModeConfigDTO());
        }

        if (StringUtils.equals(EnvironmentType.GROUP.toString(), request.getConfig().getEnvironmentType()) && StringUtils.isNotEmpty(request.getConfig().getEnvironmentGroupId())) {
            request.getConfig().setEnvMap(environmentGroupProjectService.getEnvMap(request.getConfig().getEnvironmentGroupId()));
        }

        ServiceUtils.getSelectAllIds(request, request.getCondition(),
                (query) -> extApiTestCaseMapper.selectIdsByQuery((ApiTestCaseRequest) query));

        List<MsExecResponseDTO> responseDTOS = new LinkedList<>();
        List<ApiTestCaseWithBLOBs> caseList = extApiTestCaseMapper.unTrashCaseListByIds(request.getIds());

        LoggerUtil.debug("????????????????????????" + caseList.size());
        Map<String, List<String>> testCaseEnvMap = new HashMap<>();
        // ????????????
        if (MapUtils.isEmpty(request.getConfig().getEnvMap())) {
            testCaseEnvMap = this.checkEnv(caseList);
        }
        // ??????????????????
        String serialReportId = request.isRerun() ? request.getReportId() : null;
        if (!request.isRerun() && StringUtils.equals(request.getConfig().getReportType(), RunModeConstants.SET_REPORT.toString())
                && StringUtils.isNotEmpty(request.getConfig().getReportName())) {
            serialReportId = UUID.randomUUID().toString();

            RunModeConfigDTO config = request.getConfig();
            if (MapUtils.isEmpty(config.getEnvMap())) {
                RunModeConfigWithEnvironmentDTO runModeConfig = new RunModeConfigWithEnvironmentDTO();
                BeanUtils.copyBean(runModeConfig, request.getConfig());
                this.setExecutionEnvironment(runModeConfig, testCaseEnvMap);
                config = runModeConfig;
            }
            ApiDefinitionExecResultWithBLOBs report = ApiDefinitionExecResultUtil.initBase(null, ApiReportStatus.RUNNING.name(), serialReportId, config);
            report.setName(request.getConfig().getReportName());
            report.setProjectId(request.getProjectId());
            report.setReportType(ReportTypeConstants.API_INTEGRATED.name());
            report.setVersionId(caseList.get(0).getVersionId());

            Map<String, ApiDefinitionExecResultWithBLOBs> executeQueue = new LinkedHashMap<>();
            executeQueue.put(serialReportId, report);

            apiScenarioReportStructureService.save(serialReportId, new ArrayList<>());
            apiCaseResultService.batchSave(executeQueue);
            responseDTOS.add(new MsExecResponseDTO(JSON.toJSONString(request.getIds()), report.getId(), request.getTriggerMode()));
        }
        // ??????????????????????????????
        if (request.isRerun()) {
            ApiDefinitionExecResultWithBLOBs result = new ApiDefinitionExecResultWithBLOBs();
            result.setId(serialReportId);
            result.setStatus(ApiReportStatus.RERUNNING.name());
            apiDefinitionExecResultMapper.updateByPrimaryKeySelective(result);
        }

        if (request.getConfig() != null && request.getConfig().getMode().equals(RunModeConstants.SERIAL.toString())) {
            if (request.getCondition() == null || !request.getCondition().isSelectAll()) {
                // ??????id??????????????????
                FixedOrderComparator<String> fixedOrderComparator = new FixedOrderComparator<String>(request.getIds());
                fixedOrderComparator.setUnknownObjectBehavior(FixedOrderComparator.UnknownObjectBehavior.BEFORE);
                BeanComparator beanComparator = new BeanComparator("id", fixedOrderComparator);
                Collections.sort(caseList, beanComparator);
            }
        }

        if (StringUtils.isEmpty(request.getTriggerMode())) {
            request.setTriggerMode(ApiRunMode.DEFINITION.name());
        }
        // ???????????????????????????????????????
        Map<String, ApiDefinitionExecResultWithBLOBs> executeQueue = request.isRerun() ? request.getExecuteQueue() : new LinkedHashMap<>();

        String status = request.getConfig().getMode().equals(RunModeConstants.SERIAL.toString()) ?
                ApiReportStatus.PENDING.name() : ApiReportStatus.RUNNING.name();
        // ???????????????????????????????????????
        if (!request.isRerun()) {
            for (int i = 0; i < caseList.size(); i++) {
                ApiTestCaseWithBLOBs caseWithBLOBs = caseList.get(i);

                RunModeConfigDTO config = new RunModeConfigDTO();
                BeanUtils.copyBean(config, request.getConfig());

                if (StringUtils.equals(config.getEnvironmentType(), EnvironmentType.JSON.name()) && MapUtils.isEmpty(config.getEnvMap())) {
                    config.setEnvMap(this.getEnvMap(caseWithBLOBs));
                }
                ApiDefinitionExecResultWithBLOBs report = ApiDefinitionExecResultUtil.initBase(caseWithBLOBs.getId(), ApiReportStatus.RUNNING.name(), null, config);
                report.setStatus(status);
                report.setName(caseWithBLOBs.getName());
                report.setProjectId(caseWithBLOBs.getProjectId());
                report.setVersionId(caseWithBLOBs.getVersionId());
                report.setCreateTime(System.currentTimeMillis() + i);
                if (StringUtils.isNotEmpty(serialReportId)) {
                    report.setIntegratedReportId(serialReportId);
                }
                executeQueue.put(caseWithBLOBs.getId(), report);
                if (!StringUtils.equals(request.getConfig().getReportType(), RunModeConstants.SET_REPORT.toString())) {
                    responseDTOS.add(new MsExecResponseDTO(caseWithBLOBs.getId(), report.getId(), request.getTriggerMode()));
                }
            }
            apiCaseResultService.batchSave(executeQueue);
        }

        String reportType = request.getConfig().getReportType();
        String poolId = request.getConfig().getResourcePoolId();
        DBTestQueue queue = apiExecutionQueueService.add(executeQueue, poolId, ApiRunMode.DEFINITION.name(), serialReportId, reportType, ApiRunMode.DEFINITION.name(), request.getConfig());
        // ????????????????????????
        if (queue != null && queue.getDetail() != null) {
            Thread thread = new Thread(new Runnable() {
                @Override
                public void run() {
                    Thread.currentThread().setName("API-CASE-RUN");
                    if (request.getConfig().getMode().equals(RunModeConstants.SERIAL.toString())) {
                        apiCaseSerialService.serial(queue);
                    } else {
                        apiCaseParallelExecuteService.parallel(executeQueue, request.getConfig(), queue, ApiRunMode.DEFINITION.name());
                    }
                }
            });
            thread.start();
        }
        return responseDTOS;
    }

    public void setExecutionEnvironment(RunModeConfigWithEnvironmentDTO config, Map<String, List<String>> projectEnvMap) {
        if (MapUtils.isNotEmpty(projectEnvMap) && config != null) {
            config.setExecutionEnvironmentMap(projectEnvMap);
        }
    }

    public void setRunModeConfigEnvironment(RunModeConfigWithEnvironmentDTO config, Map<String, String> projectEnvMap) {
        if (MapUtils.isNotEmpty(projectEnvMap) && config != null) {
            Map<String, List<String>> executionEnvMap = new HashMap<>();
            for (Map.Entry<String, String> entry : projectEnvMap.entrySet()) {
                String projectId = entry.getKey();
                String envId = entry.getValue();
                if (StringUtils.isNoneEmpty(projectId, envId)) {
                    executionEnvMap.put(projectId, new ArrayList<>() {{
                        this.add(envId);
                    }});
                }
            }
            if (MapUtils.isNotEmpty(executionEnvMap)) {
                config.setExecutionEnvironmentMap(executionEnvMap);
            }
        }
    }
}
