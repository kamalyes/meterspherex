package io.metersphere.api.exec.scenario;

import io.metersphere.api.dto.EnvironmentType;
import io.metersphere.api.dto.RunModeConfigWithEnvironmentDTO;
import io.metersphere.api.dto.ScenarioEnv;
import io.metersphere.api.dto.automation.ApiScenarioDTO;
import io.metersphere.api.dto.automation.RunScenarioRequest;
import io.metersphere.api.dto.definition.request.ElementUtil;
import io.metersphere.api.dto.definition.request.MsScenario;
import io.metersphere.api.dto.definition.request.ParameterConfig;
import io.metersphere.api.dto.definition.request.sampler.MsHTTPSamplerProxy;
import io.metersphere.api.dto.scenario.environment.EnvironmentConfig;
import io.metersphere.base.domain.*;
import io.metersphere.base.mapper.ApiScenarioMapper;
import io.metersphere.base.mapper.ApiTestCaseMapper;
import io.metersphere.base.mapper.ApiTestEnvironmentMapper;
import io.metersphere.base.mapper.ProjectMapper;
import io.metersphere.base.mapper.plan.TestPlanApiScenarioMapper;
import io.metersphere.commons.constants.ApiRunMode;
import io.metersphere.commons.constants.ElementConstants;
import io.metersphere.commons.constants.MsTestElementConstants;
import io.metersphere.commons.exception.MSException;
import io.metersphere.commons.utils.*;
import io.metersphere.dto.RunModeConfigDTO;
import io.metersphere.environment.service.BaseEnvGroupProjectService;
import io.metersphere.environment.service.BaseEnvironmentService;
import io.metersphere.i18n.Translator;
import io.metersphere.plugin.core.MsTestElement;
import io.metersphere.service.definition.ApiDefinitionService;
import org.apache.commons.collections.CollectionUtils;
import org.apache.commons.collections.MapUtils;
import org.apache.commons.lang3.StringUtils;
import org.json.JSONObject;
import org.springframework.context.annotation.Lazy;
import org.springframework.stereotype.Service;

import javax.annotation.Resource;
import java.util.*;
import java.util.stream.Collectors;

@Service
public class ApiScenarioEnvService {
    @Resource
    private ApiDefinitionService apiDefinitionService;
    @Resource
    private ApiScenarioMapper apiScenarioMapper;
    @Resource
    private BaseEnvGroupProjectService environmentGroupProjectService;
    @Resource
    private ProjectMapper projectMapper;
    @Resource
    private ApiTestEnvironmentMapper apiTestEnvironmentMapper;
    @Resource
    private ApiTestCaseMapper apiTestCaseMapper;
    @Lazy
    @Resource
    private TestPlanApiScenarioMapper testPlanApiScenarioMapper;
    @Resource
    private BaseEnvironmentService apiTestEnvironmentService;

    public ScenarioEnv getApiScenarioEnv(String definition) {
        ScenarioEnv env = new ScenarioEnv();
        if (StringUtils.isEmpty(definition)) {
            return env;
        }
        List<MsTestElement> hashTree = GenerateHashTreeUtil.getScenarioHashTree(definition);
        if (CollectionUtils.isNotEmpty(hashTree)) {
            // ????????????????????????
            hashTree = hashTree.stream().filter(item -> item.isEnable()).collect(Collectors.toList());
        }
        for (MsTestElement testElement : hashTree) {
            this.formatElement(testElement, env);
            if (CollectionUtils.isNotEmpty(testElement.getHashTree())) {
                getHashTree(testElement.getHashTree(), env);
            }
        }
        return env;
    }


    private void getHashTree(List<MsTestElement> tree, ScenarioEnv env) {
        try {
            // ????????????????????????
            tree = tree.stream().filter(item -> item.isEnable()).collect(Collectors.toList());
            for (MsTestElement element : tree) {
                this.formatElement(element, env);
                if (CollectionUtils.isNotEmpty(element.getHashTree())) {
                    getHashTree(element.getHashTree(), env);
                }
            }
        } catch (Exception e) {
            LogUtil.error(e);
        }
    }

    private void formatElement(MsTestElement testElement, ScenarioEnv env) {
        if (StringUtils.equals(MsTestElementConstants.REF.name(), testElement.getReferenced())) {
            if (StringUtils.equals(testElement.getType(), ElementConstants.HTTP_SAMPLER)) {
                MsHTTPSamplerProxy http = (MsHTTPSamplerProxy) testElement;
                // ????????????URL??????
                http.setUrl(StringUtils.equals(testElement.getRefType(), "CASE") ? null : http.getUrl());

                // ??????????????????
                if (!StringUtils.equalsIgnoreCase(http.getReferenced(), "Created") || (http.getIsRefEnvironment() != null && http.getIsRefEnvironment())) {
                    env.getProjectIds().add(http.getProjectId());
                    env.setFullUrl(false);
                }
            } else if (StringUtils.equals(testElement.getType(), ElementConstants.JDBC_SAMPLER) || StringUtils.equals(testElement.getType(), ElementConstants.TCP_SAMPLER)) {
                if (StringUtils.isEmpty(testElement.getProjectId())) {
                    if (StringUtils.equals(testElement.getRefType(), "CASE")) {
                        ApiTestCase testCase = apiTestCaseMapper.selectByPrimaryKey(testElement.getId());
                        if (testCase != null) {
                            env.getProjectIds().add(testCase.getProjectId());
                            env.setFullUrl(false);
                        }
                    } else {
                        ApiDefinition apiDefinition = apiDefinitionService.get(testElement.getId());
                        if (apiDefinition != null) {
                            env.getProjectIds().add(apiDefinition.getProjectId());
                            env.setFullUrl(false);
                        }
                    }
                } else {
                    env.getProjectIds().add(testElement.getProjectId());
                    env.setFullUrl(false);
                }
            } else if (StringUtils.equals(testElement.getType(), ElementConstants.SCENARIO) && StringUtils.isEmpty(testElement.getProjectId())) {
                ApiScenarioWithBLOBs apiScenario = apiScenarioMapper.selectByPrimaryKey(testElement.getId());
                if (apiScenario != null) {
                    env.getProjectIds().add(apiScenario.getProjectId());
                    String scenarioDefinition = apiScenario.getScenarioDefinition();
                    JSONObject element = JSONUtil.parseObject(scenarioDefinition);
                    ElementUtil.dataFormatting(element);
                    testElement.setHashTree(GenerateHashTreeUtil.getScenarioHashTree(scenarioDefinition));
                }
            }
        } else {
            if (StringUtils.equals(testElement.getType(), ElementConstants.HTTP_SAMPLER)) {
                // ????????????????????????
                MsHTTPSamplerProxy httpSamplerProxy = (MsHTTPSamplerProxy) testElement;
                if (httpSamplerProxy.isCustomizeReq()) {
                    env.getProjectIds().add(httpSamplerProxy.getProjectId());
                    env.setFullUrl(httpSamplerProxy.getIsRefEnvironment() == null ? true : !httpSamplerProxy.getIsRefEnvironment());
                } else if (!StringUtils.equalsIgnoreCase(httpSamplerProxy.getReferenced(), "Created") || (httpSamplerProxy.getIsRefEnvironment() != null && httpSamplerProxy.getIsRefEnvironment())) {
                    env.getProjectIds().add(httpSamplerProxy.getProjectId());
                    env.setFullUrl(false);
                }

            } else if (StringUtils.equals(testElement.getType(), ElementConstants.JDBC_SAMPLER) || StringUtils.equals(testElement.getType(), ElementConstants.TCP_SAMPLER)) {
                env.getProjectIds().add(testElement.getProjectId());
                env.setFullUrl(false);
            }
        }
        if (StringUtils.equals(testElement.getType(), ElementConstants.SCENARIO) && !((MsScenario) testElement).isEnvironmentEnable()) {
            env.getProjectIds().add(testElement.getProjectId());
        }
    }

    /**
     * ??????????????????????????? ?????????/??????JSON
     *
     * @param apiScenarioWithBLOBs
     */
    public void setScenarioEnv(ApiScenarioWithBLOBs apiScenarioWithBLOBs, RunScenarioRequest request) {
        if (apiScenarioWithBLOBs == null) {
            return;
        }
        String environmentType = apiScenarioWithBLOBs.getEnvironmentType();
        String environmentJson = apiScenarioWithBLOBs.getEnvironmentJson();
        String environmentGroupId = apiScenarioWithBLOBs.getEnvironmentGroupId();
        if (StringUtils.isBlank(environmentType)) {
            environmentType = EnvironmentType.JSON.toString();
        }
        String definition = apiScenarioWithBLOBs.getScenarioDefinition();
        MsScenario scenario = JSON.parseObject(definition, MsScenario.class);
        GenerateHashTreeUtil.parse(definition, scenario);
        if (StringUtils.equals(environmentType, EnvironmentType.JSON.toString()) && StringUtils.isNotEmpty(environmentJson)) {
            scenario.setEnvironmentMap(JSON.parseObject(environmentJson, Map.class));
        } else if (StringUtils.equals(environmentType, EnvironmentType.GROUP.toString())) {
            Map<String, String> map = environmentGroupProjectService.getEnvMap(environmentGroupId);
            scenario.setEnvironmentMap(map);
        }
        if (request != null && request.getConfig() != null && request.getConfig().getEnvMap() != null && !request.getConfig().getEnvMap().isEmpty()) {
            scenario.setEnvironmentMap(request.getConfig().getEnvMap());
        }
        apiScenarioWithBLOBs.setScenarioDefinition(JSON.toJSONString(scenario));
    }

    public boolean verifyScenarioEnv(ApiScenarioWithBLOBs apiScenarioWithBLOBs) {
        if (apiScenarioWithBLOBs != null) {
            String definition = apiScenarioWithBLOBs.getScenarioDefinition();
            MsScenario scenario = JSON.parseObject(definition, MsScenario.class);
            Map<String, String> envMap = scenario.getEnvironmentMap();
            return this.check(definition, envMap, scenario.getEnvironmentId(), apiScenarioWithBLOBs.getProjectId());
        }
        return true;
    }

    private boolean check(String definition, Map<String, String> envMap, String envId, String projectId) {
        boolean isEnv = true;
        ScenarioEnv apiScenarioEnv = getApiScenarioEnv(definition);
        // ????????????????????????????????????
        if (!apiScenarioEnv.getFullUrl()) {
            try {
                if (envMap == null || envMap.isEmpty()) {
                    isEnv = false;
                } else {
                    Set<String> projectIds = apiScenarioEnv.getProjectIds();
                    projectIds.remove(null);
                    if (CollectionUtils.isNotEmpty(envMap.keySet())) {
                        for (String id : projectIds) {
                            Project project = projectMapper.selectByPrimaryKey(id);
                            String s = envMap.get(id);
                            if (project == null) {
                                s = envMap.get(projectId);
                            }
                            if (StringUtils.isBlank(s)) {
                                isEnv = false;
                                break;
                            } else {
                                ApiTestEnvironmentWithBLOBs env = apiTestEnvironmentMapper.selectByPrimaryKey(s);
                                if (env == null) {
                                    isEnv = false;
                                    break;
                                }
                            }
                        }
                    } else {
                        isEnv = false;
                    }
                }
            } catch (Exception e) {
                isEnv = false;
                LogUtil.error(e.getMessage(), e);
            }
        }

        // 1.8 ??????????????? environmentId
        if (!isEnv) {
            if (StringUtils.isNotBlank(envId)) {
                ApiTestEnvironmentWithBLOBs env = apiTestEnvironmentMapper.selectByPrimaryKey(envId);
                if (env != null) {
                    isEnv = true;
                }
            }
        }
        return isEnv;
    }

    public boolean verifyPlanScenarioEnv(ApiScenarioWithBLOBs apiScenarioWithBLOBs, TestPlanApiScenario testPlanApiScenarios) {
        if (apiScenarioWithBLOBs != null) {
            String definition = apiScenarioWithBLOBs.getScenarioDefinition();
            MsScenario scenario = JSON.parseObject(definition, MsScenario.class);
            Map<String, String> envMap = scenario.getEnvironmentMap();
            if (testPlanApiScenarios != null) {
                String envType = testPlanApiScenarios.getEnvironmentType();
                String envJson = testPlanApiScenarios.getEnvironment();
                String envGroupId = testPlanApiScenarios.getEnvironmentGroupId();
                if (StringUtils.equals(envType, EnvironmentType.JSON.toString()) && StringUtils.isNotBlank(envJson)) {
                    envMap = JSON.parseObject(testPlanApiScenarios.getEnvironment(), Map.class);
                } else if (StringUtils.equals(envType, EnvironmentType.GROUP.name()) && StringUtils.isNotBlank(envGroupId)) {
                    envMap = environmentGroupProjectService.getEnvMap(envGroupId);
                } else {
                    envMap = new HashMap<>();
                }
            }
            return this.check(definition, envMap, scenario.getEnvironmentId(), apiScenarioWithBLOBs.getProjectId());
        }
        return true;
    }

    /**
     * ???????????????????????????????????????????????????????????????????????????????????????????????????
     *
     * @param request
     * @param apiScenarios
     * @return <projectId,envIds>
     */
    public Map<String, List<String>> checkEnv(RunScenarioRequest request, List<ApiScenarioWithBLOBs> apiScenarios) {
        Map<String, List<String>> projectEnvMap = new HashMap<>();
        if (StringUtils.equals(request.getRequestOriginator(), "TEST_PLAN")) {
            this.checkPlanScenarioEnv(request);
        } else if (StringUtils.isNotBlank(request.getRunMode()) && StringUtils.equalsAny(request.getRunMode(), ApiRunMode.SCENARIO.name(), ApiRunMode.SCENARIO_PLAN.name(), ApiRunMode.JENKINS_SCENARIO_PLAN.name())) {
            StringBuilder builder = new StringBuilder();
            for (ApiScenarioWithBLOBs apiScenarioWithBLOBs : apiScenarios) {
                try {
                    this.setScenarioEnv(apiScenarioWithBLOBs, request);
                    boolean haveEnv = this.verifyScenarioEnv(apiScenarioWithBLOBs);
                    if (!haveEnv) {
                        builder.append(apiScenarioWithBLOBs.getName()).append("; ");
                    }
                } catch (Exception e) {
                    MSException.throwException("?????????" + apiScenarioWithBLOBs.getName() + "??????????????????????????????????????????????????????!");
                }
            }
            if (builder.length() > 0) {
                MSException.throwException("?????????" + builder.toString() + "?????????????????????????????????!");
            }
        } else if (StringUtils.equals(request.getRunMode(), ApiRunMode.SCHEDULE_SCENARIO.name())) {
            for (ApiScenarioWithBLOBs apiScenarioWithBLOBs : apiScenarios) {
                try {
                    this.setScenarioEnv(apiScenarioWithBLOBs, request);
                } catch (Exception e) {
                    MSException.throwException("?????????????????????????????????????????????ID??? " + apiScenarioWithBLOBs.getId());
                }
            }
        }
        return projectEnvMap;
    }

    public void checkPlanScenarioEnv(RunScenarioRequest request) {
        StringBuilder builder = new StringBuilder();
        List<String> planCaseIds = request.getPlanCaseIds();
        if (CollectionUtils.isNotEmpty(planCaseIds)) {
            TestPlanApiScenarioExample example = new TestPlanApiScenarioExample();
            example.createCriteria().andIdIn(planCaseIds);
            List<TestPlanApiScenario> testPlanApiScenarios = testPlanApiScenarioMapper.selectByExampleWithBLOBs(example);
            for (TestPlanApiScenario testPlanApiScenario : testPlanApiScenarios) {
                try {
                    ApiScenarioWithBLOBs apiScenarioWithBLOBs = apiScenarioMapper.selectByPrimaryKey(testPlanApiScenario.getApiScenarioId());
                    boolean haveEnv = this.verifyPlanScenarioEnv(apiScenarioWithBLOBs, testPlanApiScenario);
                    if (!haveEnv) {
                        builder.append(apiScenarioWithBLOBs.getName()).append("; ");
                    }
                } catch (Exception e) {
                    MSException.throwException("?????????" + builder.toString() + "?????????????????????????????????!");
                }
            }
            if (builder.length() > 0) {
                MSException.throwException("?????????" + builder.toString() + "?????????????????????????????????!");
            }
        }
    }

    public Map<String, List<String>> selectApiScenarioEnv(List<? extends ApiScenarioWithBLOBs> list) {
        Map<String, List<String>> projectEnvMap = new LinkedHashMap<>();
        for (int i = 0; i < list.size(); i++) {
            try {
                Map<String, String> map = new HashMap<>();
                String environmentType = list.get(i).getEnvironmentType();
                String environmentGroupId = list.get(i).getEnvironmentGroupId();
                String env = list.get(i).getEnvironmentJson();
                if (StringUtils.equals(environmentType, EnvironmentType.JSON.name())) {
                    // ?????????????????? ??????
                    if (StringUtils.isBlank(env)) {
                        continue;
                    }
                    map = JSON.parseObject(env, Map.class);
                } else if (StringUtils.equals(environmentType, EnvironmentType.GROUP.name())) {
                    map = environmentGroupProjectService.getEnvMap(environmentGroupId);
                }

                Set<String> set = map.keySet();
                HashMap<String, String> envMap = new HashMap<>(16);
                // ???????????? ??????
                if (set.isEmpty()) {
                    continue;
                }
                for (String projectId : set) {
                    String envId = map.get(projectId);
                    envMap.put(projectId, envId);
                }
                for (Map.Entry<String, String> entry : envMap.entrySet()) {
                    String projectId = entry.getKey();
                    String envId = entry.getValue();
                    if (projectEnvMap.containsKey(projectId)) {
                        if (!projectEnvMap.get(projectId).contains(envId)) {
                            projectEnvMap.get(projectId).add(envId);
                        }
                    } else {
                        projectEnvMap.put(projectId, new ArrayList<>() {{
                            this.add(envId);
                        }});
                    }
                }
            } catch (Exception e) {
                LogUtil.error("api scenario environment map incorrect parsing. api scenario id:" + list.get(i).getId());
            }
        }
        return projectEnvMap;
    }

    public void setApiScenarioEnv(List<ApiScenarioDTO> list) {
        List<Project> projectList = projectMapper.selectByExample(new ProjectExample());
        List<ApiTestEnvironmentWithBLOBs> apiTestEnvironments = apiTestEnvironmentMapper.selectByExampleWithBLOBs(new ApiTestEnvironmentExample());
        for (ApiScenarioDTO scenarioDTO : list) {
            Map<String, String> map = new HashMap<>();
            String env = scenarioDTO.getEnv();
            if (StringUtils.equals(scenarioDTO.getEnvironmentType(), EnvironmentType.JSON.name())) {
                // ?????????????????? ??????
                if (StringUtils.isBlank(env)) {
                    continue;
                }
                map = JSON.parseObject(env, Map.class);
            } else if (StringUtils.equals(scenarioDTO.getEnvironmentType(), EnvironmentType.GROUP.name())) {
                map = environmentGroupProjectService.getEnvMap(scenarioDTO.getEnvironmentGroupId());
            }

            Set<String> set = map.keySet();
            HashMap<String, String> envMap = new HashMap<>(16);
            // ???????????? ??????
            if (set.isEmpty()) {
                continue;
            }
            for (String projectId : set) {
                String envId = map.get(projectId);
                if (StringUtils.isBlank(envId)) {
                    continue;
                }
                List<Project> projects = projectList.stream().filter(p -> StringUtils.equals(p.getId(), projectId)).collect(Collectors.toList());
                if (CollectionUtils.isEmpty(projects)) {
                    continue;
                }
                Project project = projects.get(0);
                List<ApiTestEnvironmentWithBLOBs> envs = apiTestEnvironments.stream().filter(e -> StringUtils.equals(e.getId(), envId)).collect(Collectors.toList());
                if (CollectionUtils.isEmpty(envs)) {
                    continue;
                }
                ApiTestEnvironmentWithBLOBs environment = envs.get(0);
                String projectName = project.getName();
                String envName = environment.getName();
                if (StringUtils.isBlank(projectName) || StringUtils.isBlank(envName)) {
                    continue;
                }
                envMap.put(projectName, envName);
            }
            scenarioDTO.setEnvironmentMap(envMap);
        }
    }

    public void setEnvConfig(Map<String, String> environmentMap, ParameterConfig config) {
        final Map<String, EnvironmentConfig> envConfig = new HashMap<>(16);
        if (MapUtils.isNotEmpty(environmentMap)) {
            environmentMap.keySet().forEach(projectId -> {
                BaseEnvironmentService apiTestEnvironmentService = CommonBeanFactory.getBean(BaseEnvironmentService.class);
                ApiTestEnvironmentWithBLOBs environment = apiTestEnvironmentService.get(environmentMap.get(projectId));
                if (environment != null && environment.getConfig() != null) {
                    EnvironmentConfig env = JSONUtil.parseObject(environment.getConfig(), EnvironmentConfig.class);
                    env.setEnvironmentId(environment.getId());
                    envConfig.put(projectId, env);
                }
            });
            config.setConfig(envConfig);
        }
    }

    public Map<String, String> planEnvMap(String testPlanScenarioId) {
        Map<String, String> planEnvMap = new HashMap<>();
        TestPlanApiScenario planApiScenario = testPlanApiScenarioMapper.selectByPrimaryKey(testPlanScenarioId);
        String envJson = planApiScenario.getEnvironment();
        String envType = planApiScenario.getEnvironmentType();
        String envGroupId = planApiScenario.getEnvironmentGroupId();
        if (StringUtils.equals(envType, EnvironmentType.JSON.toString()) && StringUtils.isNotBlank(envJson)) {
            planEnvMap = JSON.parseObject(envJson, Map.class);
        } else if (StringUtils.equals(envType, EnvironmentType.GROUP.toString()) && StringUtils.isNotBlank(envGroupId)) {
            planEnvMap = environmentGroupProjectService.getEnvMap(envGroupId);
        }
        return planEnvMap;
    }

    public LinkedHashMap<String, List<String>> selectProjectNameAndEnvName(Map<String, List<String>> projectEnvIdMap) {
        LinkedHashMap<String, List<String>> returnMap = new LinkedHashMap<>();
        if (MapUtils.isNotEmpty(projectEnvIdMap)) {
            for (Map.Entry<String, List<String>> entry : projectEnvIdMap.entrySet()) {
                String projectId = entry.getKey();
                List<String> envIdList = entry.getValue();
                String projectName = this.selectNameById(projectId);
                List<String> envNameList = apiTestEnvironmentService.selectNameByIds(envIdList);
                if (CollectionUtils.isNotEmpty(envNameList) && StringUtils.isNotEmpty(projectName)) {
                    returnMap.put(projectName, new ArrayList<>() {{
                        this.addAll(envNameList);
                    }});
                }
            }
        }
        return returnMap;
    }

    public Map<String, List<String>> selectProjectEnvMapByTestPlanScenarioIds(List<String> resourceIds) {
        Map<String, List<String>> returnMap = new LinkedHashMap<>();
        if (CollectionUtils.isNotEmpty(resourceIds)) {
            List<String> reportEnvConfList = testPlanApiScenarioMapper.selectReportEnvConfByResourceIds(resourceIds);
            reportEnvConfList.forEach(envConf -> {
                LinkedHashMap<String, List<String>> projectEnvMap = this.getProjectEnvMapByEnvConfig(envConf);
                for (Map.Entry<String, List<String>> entry : projectEnvMap.entrySet()) {
                    String projectName = entry.getKey();
                    List<String> envNameList = entry.getValue();
                    if (StringUtils.isEmpty(projectName) || CollectionUtils.isEmpty(envNameList)) {
                        continue;
                    }
                    if (returnMap.containsKey(projectName)) {
                        envNameList.forEach(envName -> {
                            if (!returnMap.get(projectName).contains(envName)) {
                                returnMap.get(projectName).add(envName);
                            }
                        });
                    } else {
                        returnMap.put(projectName, envNameList);
                    }
                }
            });
        }
        return returnMap;
    }

    public LinkedHashMap<String, List<String>> getProjectEnvMapByEnvConfig(String envConfig) {
        LinkedHashMap<String, List<String>> returnMap = new LinkedHashMap<>();
        //???????????????????????????????????????????????????????????????????????????????????????
        Map<String, String> envMapByRunConfig = null;
        //?????????????????????????????? ???????????????????????????????????????
        Map<String, List<String>> envMapByExecution = null;

        String groupId = null;
        try {
            JSONObject jsonObject = JSONUtil.parseObject(envConfig);
            if (jsonObject.has("executionEnvironmentMap")) {
                RunModeConfigWithEnvironmentDTO configWithEnvironment = JSON.parseObject(envConfig, RunModeConfigWithEnvironmentDTO.class);
                if (StringUtils.equals("GROUP", configWithEnvironment.getEnvironmentType()) && StringUtils.isNotEmpty(configWithEnvironment.getEnvironmentGroupId())) {
                    groupId = configWithEnvironment.getEnvironmentGroupId();
                }
                if (MapUtils.isNotEmpty(configWithEnvironment.getExecutionEnvironmentMap())) {
                    envMapByExecution = configWithEnvironment.getExecutionEnvironmentMap();
                } else {
                    envMapByRunConfig = configWithEnvironment.getEnvMap();
                }
            } else {
                RunModeConfigDTO config = JSON.parseObject(envConfig, RunModeConfigDTO.class);
                if (StringUtils.equals("GROUP", config.getEnvironmentType()) && StringUtils.isNotEmpty(config.getEnvironmentGroupId())) {
                    groupId = config.getEnvironmentGroupId();
                }
                envMapByRunConfig = config.getEnvMap();
            }
        } catch (Exception e) {
            LogUtil.error("??????RunModeConfig??????!?????????" + envConfig, e);
        }

        if (StringUtils.isNotEmpty(groupId)) {
            EnvironmentGroup environmentGroup = apiTestEnvironmentService.selectById(groupId);
            if (StringUtils.isNotEmpty(environmentGroup.getName())) {
                returnMap.put(Translator.get("environment_group"), new ArrayList<>() {{
                    this.add(environmentGroup.getName());
                }});
            }
        } else {
            returnMap.putAll(this.selectProjectNameAndEnvName(envMapByExecution));
            if (MapUtils.isNotEmpty(envMapByRunConfig)) {
                for (Map.Entry<String, String> entry : envMapByRunConfig.entrySet()) {
                    String projectId = entry.getKey();
                    String envId = entry.getValue();
                    String projectName = this.selectNameById(projectId);
                    String envName = apiTestEnvironmentService.selectNameById(envId);
                    if (StringUtils.isNoneEmpty(projectName, envName)) {
                        returnMap.put(projectName, new ArrayList<>() {{
                            this.add(envName);
                        }});
                    }
                }

            }
        }
        return returnMap;
    }

    public String selectNameById(String projectId) {
        Project project = projectMapper.selectByPrimaryKey(projectId);
        if (project == null) {
            return null;
        } else {
            return project.getName();
        }
    }
}
