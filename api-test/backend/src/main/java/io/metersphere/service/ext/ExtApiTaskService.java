package io.metersphere.service.ext;

import io.metersphere.api.exec.queue.ExecThreadPoolExecutor;
import io.metersphere.api.exec.queue.PoolExecBlockingQueueUtil;
import io.metersphere.api.jmeter.JMeterService;
import io.metersphere.api.jmeter.JMeterThreadUtils;
import io.metersphere.service.ApiExecutionQueueService;
import io.metersphere.base.domain.*;
import io.metersphere.base.mapper.ApiDefinitionExecResultMapper;
import io.metersphere.base.mapper.ApiScenarioReportMapper;
import io.metersphere.base.mapper.TestResourceMapper;
import io.metersphere.base.mapper.TestResourcePoolMapper;
import io.metersphere.base.mapper.ext.ExtApiDefinitionExecResultMapper;
import io.metersphere.base.mapper.ext.ExtApiScenarioReportMapper;
import io.metersphere.base.mapper.ext.BaseTaskMapper;
import io.metersphere.commons.constants.ElementConstants;
import io.metersphere.commons.enums.ApiReportStatus;
import io.metersphere.commons.utils.JSON;
import io.metersphere.commons.utils.LogUtil;
import io.metersphere.dto.NodeDTO;
import io.metersphere.jmeter.LocalRunner;
import io.metersphere.task.dto.TaskCenterDTO;
import io.metersphere.task.dto.TaskCenterRequest;
import io.metersphere.task.dto.TaskRequestDTO;
import io.metersphere.task.service.TaskService;
import io.metersphere.utils.LoggerUtil;
import org.apache.commons.collections.CollectionUtils;
import org.apache.commons.lang3.StringUtils;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.client.RestTemplate;

import javax.annotation.Resource;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

@Service
@Transactional(rollbackFor = Exception.class)
public class ExtApiTaskService extends TaskService {
    @Resource
    private BaseTaskMapper baseTaskMapper;
    @Resource
    private ApiDefinitionExecResultMapper apiDefinitionExecResultMapper;
    @Resource
    private ApiScenarioReportMapper apiScenarioReportMapper;
    @Resource
    private RestTemplate restTemplate;
    @Resource
    private TestResourcePoolMapper testResourcePoolMapper;
    @Resource
    private TestResourceMapper testResourceMapper;
    @Resource
    private ExtApiDefinitionExecResultMapper extApiDefinitionExecResultMapper;
    @Resource
    private ExtApiScenarioReportMapper extApiScenarioReportMapper;
    @Resource
    private ExecThreadPoolExecutor execThreadPoolExecutor;
    @Resource
    private ApiExecutionQueueService apiExecutionQueueService;

    public List<TaskCenterDTO> getCases(String id) {
        return baseTaskMapper.getCases(id);
    }

    public List<TaskCenterDTO> getScenario(String id) {
        return baseTaskMapper.getScenario(id);
    }


    public void send(Map<String, List<String>> poolMap) {
        try {
            LoggerUtil.info("????????????NODE??????????????????");
            Thread thread = new Thread(new Runnable() {
                @Override
                public void run() {
                    Thread.currentThread().setName("STOP-NODE");
                    for (String poolId : poolMap.keySet()) {
                        TestResourcePoolExample example = new TestResourcePoolExample();
                        example.createCriteria().andStatusEqualTo("VALID").andTypeEqualTo("NODE").andIdEqualTo(poolId);
                        List<TestResourcePool> pools = testResourcePoolMapper.selectByExample(example);
                        if (CollectionUtils.isNotEmpty(pools)) {
                            List<String> poolIds = pools.stream().map(pool -> pool.getId()).collect(Collectors.toList());
                            TestResourceExample resourceExample = new TestResourceExample();
                            resourceExample.createCriteria().andTestResourcePoolIdIn(poolIds);
                            List<TestResource> testResources = testResourceMapper.selectByExampleWithBLOBs(resourceExample);
                            for (TestResource testResource : testResources) {
                                String configuration = testResource.getConfiguration();
                                NodeDTO node = JSON.parseObject(configuration, NodeDTO.class);
                                String nodeIp = node.getIp();
                                Integer port = node.getPort();
                                String uri = String.format(JMeterService.BASE_URL + "/jmeter/stop", nodeIp, port);
                                restTemplate.postForEntity(uri, poolMap.get(poolId), void.class);
                            }
                        }
                    }
                }
            });
            thread.start();
        } catch (Exception e) {
            LogUtil.error(e.getMessage());
        }
    }

    public String apiStop(List<TaskRequestDTO> taskRequests) {
        if (CollectionUtils.isNotEmpty(taskRequests)) {
            List<TaskRequestDTO> stopTasks = taskRequests.stream().filter(s -> StringUtils.isNotEmpty(s.getReportId())).collect(Collectors.toList());
            // ??????????????????????????????????????????
            Map<String, List<String>> poolMap = new HashMap<>();
            // ????????????
            if (CollectionUtils.isNotEmpty(stopTasks) && stopTasks.size() == 1) {
                // ???????????????
                TaskRequestDTO request = stopTasks.get(0);
                execThreadPoolExecutor.removeQueue(request.getReportId());
                apiExecutionQueueService.stop(request.getReportId());
                PoolExecBlockingQueueUtil.offer(request.getReportId());
                if (StringUtils.equals(request.getType(), "API")) {
                    ApiDefinitionExecResultWithBLOBs result = apiDefinitionExecResultMapper.selectByPrimaryKey(request.getReportId());
                    if (result != null) {
                        result.setStatus(ApiReportStatus.STOPPED.name());
                        apiDefinitionExecResultMapper.updateByPrimaryKeySelective(result);
                        extracted(poolMap, request.getReportId(), result.getActuator());
                    }
                }
                if (StringUtils.equals(request.getType(), ElementConstants.SCENARIO_UPPER)) {
                    ApiScenarioReportWithBLOBs report = apiScenarioReportMapper.selectByPrimaryKey(request.getReportId());
                    if (report != null) {
                        report.setStatus(ApiReportStatus.STOPPED.name());
                        apiScenarioReportMapper.updateByPrimaryKeySelective(report);
                        extracted(poolMap, request.getReportId(), report.getActuator());
                    }
                }

            } else {
                try {
                    LoggerUtil.info("????????????????????????");
                    // ????????????
                    Map<String, TaskRequestDTO> taskRequestMap = taskRequests.stream().collect(Collectors.toMap(TaskRequestDTO::getType, taskRequest -> taskRequest));
                    // ????????????????????????
                    LoggerUtil.info("?????????????????????????????????");
                    TaskCenterRequest taskCenterRequest = new TaskCenterRequest();
                    taskCenterRequest.setProjects(this.getOwnerProjectIds(taskRequestMap.get(ElementConstants.SCENARIO_UPPER).getUserId()));

                    // ?????????????????????????????????
                    LoggerUtil.info("??????????????????????????????????????????");
                    JMeterThreadUtils.stop("PLAN-CASE");
                    JMeterThreadUtils.stop("API-CASE-RUN");
                    JMeterThreadUtils.stop("SCENARIO-PARALLEL-THREAD");

                    if (taskRequestMap.containsKey("API")) {
                        List<ApiDefinitionExecResult> results = extApiDefinitionExecResultMapper.findByProjectIds(taskCenterRequest);
                        LoggerUtil.info("??????API?????????????????????" + results.size());
                        if (CollectionUtils.isNotEmpty(results)) {
                            for (ApiDefinitionExecResult item : results) {
                                extracted(poolMap, item.getId(), item.getActuator());
                                // ???????????????
                                execThreadPoolExecutor.removeQueue(item.getId());
                                PoolExecBlockingQueueUtil.offer(item.getId());
                            }
                            LoggerUtil.info("??????API??????????????????");
                            baseTaskMapper.stopApi(taskCenterRequest);
                            // ???????????????????????????????????????
                            LoggerUtil.info("??????API?????????");
                            List<String> ids = results.stream().map(ApiDefinitionExecResult::getId).collect(Collectors.toList());
                            apiExecutionQueueService.stop(ids);
                        }
                    }
                    if (taskRequestMap.containsKey(ElementConstants.SCENARIO_UPPER)) {
                        List<ApiScenarioReport> reports = extApiScenarioReportMapper.findByProjectIds(taskCenterRequest);
                        LoggerUtil.info("????????????????????????????????????" + reports.size());
                        if (CollectionUtils.isNotEmpty(reports)) {
                            for (ApiScenarioReport report : reports) {

                                extracted(poolMap, report.getId(), report.getActuator());
                                // ???????????????
                                execThreadPoolExecutor.removeQueue(report.getId());
                                PoolExecBlockingQueueUtil.offer(report.getId());
                            }

                            // ???????????????????????????????????????
                            LoggerUtil.info("???????????????????????????????????? ");
                            List<String> ids = reports.stream().map(ApiScenarioReport::getId).collect(Collectors.toList());
                            baseTaskMapper.stopScenario(taskCenterRequest);
                            // ???????????????????????????????????????
                            LoggerUtil.info("??????????????????????????????????????? ");
                            apiExecutionQueueService.stop(ids);
                        }
                    }
                } catch (Exception e) {
                    LogUtil.error(e);
                }
            }
            if (!poolMap.isEmpty()) {
                this.send(poolMap);
            }
        }
        return "SUCCESS";
    }

    private void extracted(Map<String, List<String>> poolMap, String reportId, String actuator) {
        if (StringUtils.isEmpty(reportId)) {
            return;
        }
        if (StringUtils.isNotEmpty(actuator) && !StringUtils.equals(actuator, "LOCAL")) {
            if (poolMap.containsKey(actuator)) {
                poolMap.get(actuator).add(reportId);
            } else {
                poolMap.put(actuator, new ArrayList<String>() {{
                    this.add(reportId);
                }});
            }
        } else {
            new LocalRunner().stop(reportId);
            JMeterThreadUtils.stop(reportId);
        }
    }
}
