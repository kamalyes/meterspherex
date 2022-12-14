package io.metersphere.service;

import io.metersphere.api.dto.RunModeDataDTO;
import io.metersphere.api.exec.api.ApiCaseSerialService;
import io.metersphere.api.exec.queue.DBTestQueue;
import io.metersphere.api.exec.scenario.ApiScenarioSerialService;
import io.metersphere.api.jmeter.JMeterService;
import io.metersphere.api.jmeter.JMeterThreadUtils;
import io.metersphere.base.domain.*;
import io.metersphere.base.mapper.*;
import io.metersphere.base.mapper.ext.BaseApiExecutionQueueMapper;
import io.metersphere.base.mapper.ext.ExtApiDefinitionExecResultMapper;
import io.metersphere.base.mapper.ext.ExtApiScenarioReportMapper;
import io.metersphere.commons.constants.ApiRunMode;
import io.metersphere.commons.constants.KafkaTopicConstants;
import io.metersphere.commons.constants.TestPlanReportStatus;
import io.metersphere.commons.enums.ApiReportStatus;
import io.metersphere.commons.utils.BeanUtils;
import io.metersphere.commons.utils.JSON;
import io.metersphere.commons.utils.LogUtil;
import io.metersphere.constants.RunModeConstants;
import io.metersphere.dto.ResultDTO;
import io.metersphere.dto.RunModeConfigDTO;
import io.metersphere.service.scenario.ApiScenarioReportService;
import io.metersphere.utils.LoggerUtil;
import org.apache.commons.collections4.CollectionUtils;
import org.apache.commons.collections4.MapUtils;
import org.apache.commons.lang3.BooleanUtils;
import org.apache.commons.lang3.StringUtils;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import javax.annotation.Resource;
import java.util.*;
import java.util.stream.Collectors;

@Service
public class ApiExecutionQueueService {
    @Resource
    protected ApiExecutionQueueMapper queueMapper;
    @Resource
    private ApiExecutionQueueDetailMapper executionQueueDetailMapper;
    @Resource
    private RedisTemplateService redisTemplateService;
    @Resource
    private ApiScenarioSerialService apiScenarioSerialService;
    @Resource
    private ApiScenarioReportService apiScenarioReportService;
    @Resource
    private ApiScenarioReportMapper apiScenarioReportMapper;
    @Resource
    private ApiDefinitionExecResultMapper apiDefinitionExecResultMapper;
    @Resource
    private ExtApiDefinitionExecResultMapper extApiDefinitionExecResultMapper;
    @Resource
    private ExtApiScenarioReportMapper extApiScenarioReportMapper;
    @Resource
    private JMeterService jMeterService;
    @Resource
    protected BaseApiExecutionQueueMapper extApiExecutionQueueMapper;
    @Resource
    private ApiScenarioReportResultMapper apiScenarioReportResultMapper;
    @Resource
    private ApiCaseSerialService apiCaseSerialService;
    @Resource
    private KafkaTemplate<String, String> kafkaTemplate;

    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public DBTestQueue add(Object runObj, String poolId, String type, String reportId, String reportType, String runMode, RunModeConfigDTO config) {
        LoggerUtil.info("?????????" + reportId + "????????????????????????");
        if (config.getEnvMap() == null) {
            config.setEnvMap(new LinkedHashMap<>());
        }
        ApiExecutionQueue executionQueue = getApiExecutionQueue(poolId, reportId, reportType, runMode, config);
        queueMapper.insert(executionQueue);
        DBTestQueue resQueue = new DBTestQueue();
        BeanUtils.copyBean(resQueue, executionQueue);

        Map<String, String> detailMap = new HashMap<>();
        List<ApiExecutionQueueDetail> queueDetails = new LinkedList<>();
        // ?????????API/????????????
        if (StringUtils.equalsAnyIgnoreCase(type, ApiRunMode.DEFINITION.name(), ApiRunMode.API_PLAN.name())) {
            Map<String, ApiDefinitionExecResult> runMap = (Map<String, ApiDefinitionExecResult>) runObj;
            initApi(runMap, resQueue, config, detailMap, queueDetails);
        }
        // ??????????????????????????????
        else if (StringUtils.equalsIgnoreCase(type, ApiRunMode.TEST_PLAN_PERFORMANCE_TEST.name())) {
            Map<String, String> requests = (Map<String, String>) runObj;
            initPerf(requests, resQueue, config, detailMap, queueDetails);
        }
        // ???????????????/UI?????????
        else {
            Map<String, RunModeDataDTO> runMap = (Map<String, RunModeDataDTO>) runObj;
            initScenario(runMap, resQueue, config, type, detailMap, queueDetails);
        }
        if (CollectionUtils.isNotEmpty(queueDetails)) {
            extApiExecutionQueueMapper.sqlInsert(queueDetails);
        }
        resQueue.setDetailMap(detailMap);
        LoggerUtil.info("?????????" + reportId + "????????????????????????");
        return resQueue;
    }

    private void initScenario(Map<String, RunModeDataDTO> runMap, DBTestQueue resQueue, RunModeConfigDTO config, String type, Map<String, String> detailMap, List<ApiExecutionQueueDetail> queueDetails) {
        final int[] sort = {0};
        runMap.forEach((k, v) -> {
            String envMap = JSON.toJSONString(v.getPlanEnvMap());
            ApiExecutionQueueDetail queue = detail(k, v.getTestId(), config.getMode(), sort[0], resQueue.getId(), envMap);
            queue.setSort(sort[0]);
            if (sort[0] == 0) {
                resQueue.setDetail(queue);
            }
            sort[0]++;
            queue.setRetryEnable(config.isRetryEnable());
            queue.setRetryNumber(config.getRetryNum());
            List<String> projectIds = new ArrayList<>();
            // ????????????????????????ID??????????????????????????????
            if (MapUtils.isNotEmpty(v.getPlanEnvMap())) {
                List<String> kyList = v.getPlanEnvMap().keySet().stream().collect(Collectors.toList());
                projectIds.addAll(kyList);
            } else {
                projectIds.add(v.getReport().getProjectId());
            }
            queue.setProjectIds(JSON.toJSONString(projectIds));
            queueDetails.add(queue);
            detailMap.put(k, queue.getId());
        });
    }

    private void initApi(Map<String, ApiDefinitionExecResult> runMap, DBTestQueue resQueue, RunModeConfigDTO config, Map<String, String> detailMap, List<ApiExecutionQueueDetail> queueDetails) {
        int sort = 0;
        String envStr = JSON.toJSONString(config.getEnvMap());
        for (String k : runMap.keySet()) {
            ApiExecutionQueueDetail queue = detail(runMap.get(k).getId(), k, config.getMode(), sort++, resQueue.getId(), envStr);
            if (sort == 1) {
                resQueue.setDetail(queue);
            }
            queue.setRetryEnable(config.isRetryEnable());
            queue.setRetryNumber(config.getRetryNum());
            queue.setProjectIds(JSON.toJSONString(new ArrayList<>() {{
                this.add(runMap.get(k).getProjectId());
            }}));
            queueDetails.add(queue);
            detailMap.put(k, queue.getId());
        }
        resQueue.setDetailMap(detailMap);
    }

    private void initPerf(Map<String, String> requests, DBTestQueue resQueue, RunModeConfigDTO config, Map<String, String> detailMap, List<ApiExecutionQueueDetail> queueDetails) {
        String envStr = JSON.toJSONString(config.getEnvMap());
        int i = 0;
        for (String testId : requests.keySet()) {
            ApiExecutionQueueDetail queue = detail(requests.get(testId), testId, config.getMode(), i++, resQueue.getId(), envStr);
            if (i == 1) {
                resQueue.setDetail(queue);
            }
            queue.setRetryEnable(config.isRetryEnable());
            queue.setRetryNumber(config.getRetryNum());
            queueDetails.add(queue);
            detailMap.put(testId, queue.getId());
        }
    }

    protected ApiExecutionQueue getApiExecutionQueue(String poolId, String reportId, String reportType, String runMode, RunModeConfigDTO config) {
        ApiExecutionQueue executionQueue = new ApiExecutionQueue();
        executionQueue.setId(UUID.randomUUID().toString());
        executionQueue.setCreateTime(System.currentTimeMillis());
        executionQueue.setPoolId(poolId);
        executionQueue.setFailure(config.isOnSampleError());
        executionQueue.setReportId(reportId);
        executionQueue.setReportType(StringUtils.isNotEmpty(reportType) ? reportType : RunModeConstants.INDEPENDENCE.toString());
        executionQueue.setRunMode(runMode);
        return executionQueue;
    }

    protected ApiExecutionQueueDetail detail(String reportId, String testId, String type, int sort, String queueId, String envMap) {
        ApiExecutionQueueDetail queue = new ApiExecutionQueueDetail();
        queue.setCreateTime(System.currentTimeMillis());
        queue.setId(UUID.randomUUID().toString());
        queue.setEvnMap(envMap);
        queue.setReportId(reportId);
        queue.setTestId(testId);
        queue.setType(type);
        queue.setSort(sort);
        queue.setQueueId(queueId);
        return queue;
    }

    private boolean failure(DBTestQueue executionQueue, ResultDTO dto) {
        LoggerUtil.info("???????????????????????????" + executionQueue.getId());
        boolean isError = false;
        if (StringUtils.contains(dto.getRunMode(), ApiRunMode.SCENARIO.name())) {
            if (StringUtils.equals(dto.getReportType(), RunModeConstants.SET_REPORT.toString())) {
                ApiScenarioReportResultExample example = new ApiScenarioReportResultExample();
                example.createCriteria().andReportIdEqualTo(dto.getReportId()).andStatusEqualTo(ApiReportStatus.ERROR.name());
                long error = apiScenarioReportResultMapper.countByExample(example);
                isError = error > 0;
            } else {
                ApiScenarioReport report = apiScenarioReportMapper.selectByPrimaryKey(executionQueue.getCompletedReportId());
                if (report != null && StringUtils.equalsIgnoreCase(report.getStatus(), ApiReportStatus.ERROR.name())) {
                    isError = true;
                }
            }
        } else {
            ApiDefinitionExecResult result = apiDefinitionExecResultMapper.selectByPrimaryKey(executionQueue.getCompletedReportId());
            if (result != null && StringUtils.equalsIgnoreCase(result.getStatus(), ApiReportStatus.ERROR.name())) {
                isError = true;
            }
        }
        if (isError) {
            ApiExecutionQueueDetailExample example = new ApiExecutionQueueDetailExample();
            example.createCriteria().andQueueIdEqualTo(dto.getQueueId());

            if (StringUtils.isNotEmpty(dto.getTestPlanReportId())) {
                testPlanReportTestEnded(dto.getTestPlanReportId());
            }
            // ??????????????????????????????
            List<ApiExecutionQueueDetail> details = executionQueueDetailMapper.selectByExample(example);
            List<String> reportIds = details.stream().map(ApiExecutionQueueDetail::getReportId).collect(Collectors.toList());
            if (CollectionUtils.isNotEmpty(reportIds)) {
                extApiDefinitionExecResultMapper.update(reportIds);
                extApiScenarioReportMapper.update(reportIds);
            }
            // ????????????
            executionQueueDetailMapper.deleteByExample(example);
            queueMapper.deleteByPrimaryKey(executionQueue.getId());
            // ??????????????????
            this.margeReport(dto);
            return false;
        }
        return true;
    }

    public DBTestQueue handleQueue(String id, String testId) {
        ApiExecutionQueue executionQueue = queueMapper.selectByPrimaryKey(id);
        DBTestQueue queue = new DBTestQueue();
        if (executionQueue != null) {
            BeanUtils.copyBean(queue, executionQueue);
            LoggerUtil.info("Get the next execution point??????" + id + "???");

            ApiExecutionQueueDetailExample example = new ApiExecutionQueueDetailExample();
            example.setOrderByClause("sort asc");
            example.createCriteria().andQueueIdEqualTo(id);
            List<ApiExecutionQueueDetail> queues = executionQueueDetailMapper.selectByExampleWithBLOBs(example);

            if (CollectionUtils.isNotEmpty(queues)) {
                // ??????????????????????????????????????????
                List<ApiExecutionQueueDetail> completedQueues = queues.stream().filter(item -> StringUtils.equals(item.getTestId(), testId)).collect(Collectors.toList());
                if (CollectionUtils.isNotEmpty(completedQueues)) {
                    ApiExecutionQueueDetail completed = completedQueues.get(0);
                    queue.setCompletedReportId(completed.getReportId());
                    executionQueueDetailMapper.deleteByPrimaryKey(completed.getId());
                    queues.remove(completed);
                }
                // ?????????????????????????????????
                if (CollectionUtils.isNotEmpty(queues)) {
                    queue.setDetail(queues.get(0));
                } else {
                    LoggerUtil.info("execution complete,clear queue??????" + id + "???");
                    queueMapper.deleteByPrimaryKey(id);
                }
            } else {
                LoggerUtil.info("execution complete,clear queue??????" + id + "???");
                queueMapper.deleteByPrimaryKey(id);
            }
        } else {
            LoggerUtil.info("The queue was accidentally deleted??????" + id + "???");
        }
        return queue;
    }

    public void testPlanReportTestEnded(String testPlanReportId) {
        // ?????????????????????????????????????????????
        kafkaTemplate.send(KafkaTopicConstants.TEST_PLAN_REPORT_TOPIC, testPlanReportId);
    }

    public void queueNext(ResultDTO dto) {
        LoggerUtil.info("?????????????????????" + dto.getReportId() + "QID???" + dto.getQueueId());
        if (StringUtils.equals(dto.getRunType(), RunModeConstants.PARALLEL.toString())) {
            ApiExecutionQueueDetailExample example = new ApiExecutionQueueDetailExample();
            example.createCriteria().andQueueIdEqualTo(dto.getQueueId()).andTestIdEqualTo(dto.getTestId());
            executionQueueDetailMapper.deleteByExample(example);
            // ????????????????????????
            ApiExecutionQueueDetailExample queueDetailExample = new ApiExecutionQueueDetailExample();
            queueDetailExample.createCriteria().andQueueIdEqualTo(dto.getQueueId());
            long count = executionQueueDetailMapper.countByExample(queueDetailExample);
            if (count == 0) {
                queueMapper.deleteByPrimaryKey(dto.getQueueId());
                // ??????????????????
                this.margeReport(dto);
            }
            return;
        }
        // ?????????????????????????????????
        DBTestQueue executionQueue = this.handleQueue(dto.getQueueId(), dto.getTestId());
        if (executionQueue != null) {
            // ??????????????????
            if (BooleanUtils.isTrue(executionQueue.getFailure()) && StringUtils.isNotEmpty(executionQueue.getCompletedReportId())) {
                boolean isNext = failure(executionQueue, dto);
                if (!isNext) {
                    return;
                }
            }
            LoggerUtil.info("???????????????????????????" + executionQueue.getId() + " ??????????????????" + dto.getTestId() + "??????ID???" + dto.getReportId());
            if (executionQueue.getDetail() != null && StringUtils.isNotEmpty(executionQueue.getDetail().getTestId())) {
                if (StringUtils.equals(dto.getRunType(), RunModeConstants.SERIAL.toString())) {
                    LoggerUtil.info("????????????????????????" + JSON.toJSONString(executionQueue.getDetail()));
                    // ??????????????????
                    String key = StringUtils.join(RunModeConstants.SERIAL.name(), "_", executionQueue.getDetail().getReportId());
                    boolean isNext = redisTemplateService.setIfAbsent(key, executionQueue.getDetail().getQueueId());
                    if (!isNext) {
                        return;
                    }
                    redisTemplateService.expire(key);
                    if (StringUtils.equalsAny(executionQueue.getRunMode(),
                            ApiRunMode.SCENARIO.name(),
                            ApiRunMode.SCENARIO_PLAN.name(),
                            ApiRunMode.SCHEDULE_SCENARIO_PLAN.name(),
                            ApiRunMode.SCHEDULE_SCENARIO.name(),
                            ApiRunMode.JENKINS_SCENARIO_PLAN.name())) {
                        apiScenarioSerialService.serial(executionQueue);
                    } else {
                        apiCaseSerialService.serial(executionQueue);
                    }
                }
            } else {
                // ??????????????????
                this.margeReport(dto);
                queueMapper.deleteByPrimaryKey(dto.getQueueId());
                LoggerUtil.info("Queue execution ends???" + dto.getQueueId());
            }

            ApiExecutionQueueDetailExample example = new ApiExecutionQueueDetailExample();
            example.createCriteria().andQueueIdEqualTo(dto.getQueueId()).andTestIdEqualTo(dto.getTestId());
            executionQueueDetailMapper.deleteByExample(example);
        }
        LoggerUtil.info("?????????????????????" + dto.getReportId() + "QID???" + dto.getQueueId());
    }

    private void margeReport(ResultDTO dto) {
        if (StringUtils.equals(dto.getReportType(), RunModeConstants.SET_REPORT.toString())) {
            String reportId = dto.getReportId();
            if (StringUtils.equalsIgnoreCase(dto.getRunMode(), ApiRunMode.DEFINITION.name())) {
                reportId = dto.getTestPlanReportId();
            }
            apiScenarioReportService.margeReport(reportId, dto.getRunMode(), dto.getConsole());
        }
    }

    public void defendQueue() {
        final int SECOND_MILLIS = 1000;
        final int MINUTE_MILLIS = 60 * SECOND_MILLIS;
        // ?????????????????????????????????
        final long timeout = System.currentTimeMillis() - (60 * MINUTE_MILLIS);
        ApiExecutionQueueDetailExample example = new ApiExecutionQueueDetailExample();
        example.createCriteria().andCreateTimeLessThan(timeout).andTypeNotEqualTo("loadTest");
        List<ApiExecutionQueueDetail> queueDetails = executionQueueDetailMapper.selectByExample(example);

        for (ApiExecutionQueueDetail item : queueDetails) {
            ApiExecutionQueue queue = queueMapper.selectByPrimaryKey(item.getQueueId());
            if (queue == null) {
                executionQueueDetailMapper.deleteByPrimaryKey(item.getId());
                continue;
            }
            // ?????????????????????
            if (StringUtils.isNotEmpty(queue.getPoolId()) && jMeterService.getRunningQueue(queue.getPoolId(), item.getReportId())) {
                continue;
            }
            // ???????????????????????????????????????????????????????????????
            if (JMeterThreadUtils.isRunning(item.getReportId(), item.getTestId())) {
                continue;
            }
            // ????????????????????????
            ResultDTO dto = new ResultDTO();
            dto.setQueueId(item.getQueueId());
            dto.setTestId(item.getTestId());
            if (StringUtils.equalsAnyIgnoreCase(queue.getRunMode(), ApiRunMode.SCENARIO.name(), ApiRunMode.SCENARIO_PLAN.name(), ApiRunMode.SCHEDULE_SCENARIO_PLAN.name(), ApiRunMode.SCHEDULE_SCENARIO.name(), ApiRunMode.JENKINS_SCENARIO_PLAN.name())) {
                ApiScenarioReportWithBLOBs report = apiScenarioReportMapper.selectByPrimaryKey(item.getReportId());
                // ???????????????????????????????????????
                if (report == null) {
                    executionQueueDetailMapper.deleteByPrimaryKey(item.getId());
                }
                // ???????????????????????????????????????????????? ??????????????? Waiting ??????????????????????????????
                if (report != null && StringUtils.equalsAnyIgnoreCase(report.getStatus(), TestPlanReportStatus.RUNNING.name()) && report.getUpdateTime() < timeout) {
                    report.setStatus(ApiReportStatus.ERROR.name());
                    apiScenarioReportMapper.updateByPrimaryKeySelective(report);

                    LoggerUtil.info("?????????????????????" + report.getId());
                    if (queue != null && StringUtils.equalsIgnoreCase(item.getType(), RunModeConstants.SERIAL.toString())) {
                        // ?????????????????????
                        String key = StringUtils.join(RunModeConstants.SERIAL.name(), "_", dto.getReportId());
                        redisTemplateService.delete(key);

                        LoggerUtil.info("????????????????????????" + report.getId() + "????????????????????????");
                        dto.setTestPlanReportId(queue.getReportId());
                        dto.setReportId(queue.getReportId());
                        dto.setRunMode(queue.getRunMode());
                        dto.setRunType(item.getType());
                        dto.setReportType(queue.getReportType());
                        queueNext(dto);
                    } else {
                        executionQueueDetailMapper.deleteByPrimaryKey(item.getId());
                    }
                }
            } else {
                // ??????/????????????????????????
                ApiDefinitionExecResultWithBLOBs result = apiDefinitionExecResultMapper.selectByPrimaryKey(item.getReportId());
                if (result != null && StringUtils.equalsAnyIgnoreCase(result.getStatus(), TestPlanReportStatus.RUNNING.name())) {
                    result.setStatus(ApiReportStatus.ERROR.name());
                    apiDefinitionExecResultMapper.updateByPrimaryKeySelective(result);
                    executionQueueDetailMapper.deleteByPrimaryKey(item.getId());
                    dto.setTestPlanReportId(queue.getReportId());
                    dto.setReportId(queue.getReportId());
                    dto.setRunMode(queue.getRunMode());
                    dto.setRunType(item.getType());
                    dto.setReportType(queue.getReportType());
                    queueNext(dto);
                }
            }
        }
        // ????????????????????????
        ApiExecutionQueueExample queueDetailExample = new ApiExecutionQueueExample();
        queueDetailExample.createCriteria().andReportTypeEqualTo(RunModeConstants.SET_REPORT.toString()).andCreateTimeLessThan(timeout);
        List<ApiExecutionQueue> executionQueues = queueMapper.selectByExample(queueDetailExample);
        if (CollectionUtils.isNotEmpty(executionQueues)) {
            executionQueues.forEach(item -> {
                ApiScenarioReportWithBLOBs report = apiScenarioReportMapper.selectByPrimaryKey(item.getReportId());
                if (report != null && StringUtils.equalsAnyIgnoreCase(report.getStatus(),
                        TestPlanReportStatus.RUNNING.name(), ApiReportStatus.PENDING.name()) && (report.getUpdateTime() < timeout)) {
                    report.setStatus(ApiReportStatus.ERROR.name());
                    apiScenarioReportMapper.updateByPrimaryKeySelective(report);
                }
            });
        }
        // ????????????????????????
        List<ApiExecutionQueue> queues = extApiExecutionQueueMapper.findTestPlanReportQueue();
        if (CollectionUtils.isNotEmpty(queues)) {
            queues.forEach(item -> {
                // ????????????????????????
                if (StringUtils.isNotEmpty(item.getReportId())) {
                    LoggerUtil.info("Handling test plan reports that are not in the execution queue??????" + item.getReportId() + "???");
                    testPlanReportTestEnded(item.getReportId());
                }
            });
        }

        List<String> testPlanReports = extApiExecutionQueueMapper.findTestPlanRunningReport();
        if (CollectionUtils.isNotEmpty(testPlanReports)) {
            testPlanReports.forEach(reportId -> {
                LoggerUtil.info("Compensation Test Plan Report??????" + reportId + "???");
                testPlanReportTestEnded(reportId);
            });
        }
        // ??????????????????/?????????????????????????????????
        extApiExecutionQueueMapper.delete();
    }

    public void stop(String reportId) {
        ApiExecutionQueueDetailExample example = new ApiExecutionQueueDetailExample();
        example.createCriteria().andReportIdEqualTo(reportId);
        List<ApiExecutionQueueDetail> details = executionQueueDetailMapper.selectByExample(example);
        details.forEach(detail -> {
            executionQueueDetailMapper.deleteByPrimaryKey(detail.getId());

            ApiExecutionQueueDetailExample queueDetailExample = new ApiExecutionQueueDetailExample();
            queueDetailExample.createCriteria().andQueueIdEqualTo(detail.getQueueId());
            long queueDetailSize = executionQueueDetailMapper.countByExample(queueDetailExample);
            if (queueDetailSize <= 1) {
                ApiExecutionQueue queue = queueMapper.selectByPrimaryKey(detail.getQueueId());
                // ????????????????????????
                if (queue != null && StringUtils.isNotEmpty(queue.getReportId())) {
                    testPlanReportTestEnded(queue.getReportId());
                }
            }
        });
    }

    public void stop(List<String> reportIds) {
        if (CollectionUtils.isEmpty(reportIds)) {
            return;
        }
        ApiExecutionQueueDetailExample example = new ApiExecutionQueueDetailExample();
        example.createCriteria().andReportIdIn(reportIds);
        List<ApiExecutionQueueDetail> details = executionQueueDetailMapper.selectByExample(example);

        List<String> queueIds = new ArrayList<>();
        details.forEach(item -> {
            if (!queueIds.contains(item.getQueueId())) {
                queueIds.add(item.getQueueId());
            }
        });
        executionQueueDetailMapper.deleteByExample(example);

        for (String queueId : queueIds) {
            ApiExecutionQueue queue = queueMapper.selectByPrimaryKey(queueId);
            // ????????????????????????
            if (queue != null && StringUtils.isNotEmpty(queue.getReportId())) {
                testPlanReportTestEnded(queue.getReportId());
                queueMapper.deleteByPrimaryKey(queueId);
            }
        }
    }

    /**
     * ????????????????????????
     */
    public void exceptionHandling() {
        LogUtil.info("????????????????????????????????????????????????????????????");
        // ??????????????????
        extApiScenarioReportMapper.updateAllStatus();
        // ????????????????????????
        extApiDefinitionExecResultMapper.updateAllStatus();
        LogUtil.info("????????????????????????????????????????????????????????????");
    }
}
