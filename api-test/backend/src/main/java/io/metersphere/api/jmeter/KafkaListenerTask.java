package io.metersphere.api.jmeter;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import io.metersphere.api.exec.queue.PoolExecBlockingQueueUtil;
import io.metersphere.service.ApiExecutionQueueService;
import io.metersphere.service.TestResultService;
import io.metersphere.commons.constants.ApiRunMode;
import io.metersphere.dto.ResultDTO;
import io.metersphere.utils.LoggerUtil;
import lombok.Data;
import org.apache.commons.collections.CollectionUtils;
import org.apache.commons.collections.MapUtils;
import org.apache.commons.lang3.StringUtils;
import org.apache.kafka.clients.consumer.ConsumerRecord;

import java.util.*;

@Data
public class KafkaListenerTask implements Runnable {
    private ConsumerRecord<?, String> record;
    private ApiExecutionQueueService apiExecutionQueueService;
    private TestResultService testResultService;
    private ObjectMapper mapper;

    private static final Map<String, String> RUN_MODE_MAP = new HashMap<String, String>() {{
        this.put(ApiRunMode.SCHEDULE_API_PLAN.name(), "schedule-task");
        this.put(ApiRunMode.JENKINS_API_PLAN.name(), "schedule-task");
        this.put(ApiRunMode.MANUAL_PLAN.name(), "schedule-task");

        this.put(ApiRunMode.DEFINITION.name(), "api-test-case-task");
        this.put(ApiRunMode.JENKINS.name(), "api-test-case-task");
        this.put(ApiRunMode.API_PLAN.name(), "api-test-case-task");
        this.put(ApiRunMode.JENKINS_API_PLAN.name(), "api-test-case-task");
        this.put(ApiRunMode.MANUAL_PLAN.name(), "api-test-case-task");


        this.put(ApiRunMode.SCENARIO.name(), "api-scenario-task");
        this.put(ApiRunMode.SCENARIO_PLAN.name(), "api-scenario-task");
        this.put(ApiRunMode.SCHEDULE_SCENARIO_PLAN.name(), "api-scenario-task");
        this.put(ApiRunMode.SCHEDULE_SCENARIO.name(), "api-scenario-task");
        this.put(ApiRunMode.JENKINS_SCENARIO_PLAN.name(), "api-scenario-task");

    }};

    @Override
    public void run() {
        try {
            // ???????????????
            Map<String, List<ResultDTO>> assortMap = new LinkedHashMap<>();
            List<ResultDTO> resultDTOS = new LinkedList<>();
            LoggerUtil.info("KAFKA????????????????????????????????????", String.valueOf(record.key()));
            ResultDTO dto = this.formatResult();
            if (dto == null) {
                return;
            }
            if (dto.getArbitraryData() != null && dto.getArbitraryData().containsKey("TEST_END")
                    && (Boolean) dto.getArbitraryData().get("TEST_END")) {
                resultDTOS.add(dto);
                LoggerUtil.info("KAFKA???????????????????????????" + dto.getArbitraryData().get("TEST_END"), String.valueOf(record.key()));
            }
            // ????????????
            if (CollectionUtils.isNotEmpty(dto.getRequestResults())) {
                String key = RUN_MODE_MAP.get(dto.getRunMode());
                if (assortMap.containsKey(key)) {
                    assortMap.get(key).add(dto);
                } else {
                    assortMap.put(key, new LinkedList<ResultDTO>() {{
                        this.add(dto);
                    }});
                }
            }
            if (MapUtils.isNotEmpty(assortMap)) {
                LoggerUtil.info("KAFKA??????????????????????????????", String.valueOf(record.key()));
                testResultService.batchSaveResults(assortMap);
                LoggerUtil.info("KAFKA??????????????????????????????", String.valueOf(record.key()));
            }
            // ??????????????????
            if (CollectionUtils.isNotEmpty(resultDTOS)) {
                resultDTOS.forEach(testResult -> {
                    LoggerUtil.info("?????? " + testResult.getTestId() + " ??????????????????", testResult.getReportId());
                    testResultService.testEnded(testResult);
                    LoggerUtil.info("?????????????????????" + testResult.getQueueId(), testResult.getReportId());
                    apiExecutionQueueService.queueNext(testResult);
                    // ??????????????????
                    PoolExecBlockingQueueUtil.offer(testResult.getReportId());
                    // ????????????????????????
                    if (StringUtils.isNotEmpty(testResult.getTestPlanReportId())) {
                        LoggerUtil.info("Check Processing Test Plan report status???" + testResult.getQueueId() + "???" + testResult.getTestId(), testResult.getReportId());
                        apiExecutionQueueService.testPlanReportTestEnded(testResult.getTestPlanReportId());
                    }
                });
            }
        } catch (Exception e) {
            LoggerUtil.error("KAFKA???????????????", String.valueOf(record.key()), e);
        }
    }

    private ResultDTO formatResult() {
        try {
            // ??????JSON?????????????????????????????????????????? ObjectMapper ??????
            if (StringUtils.isNotEmpty(record.value())) {
                return mapper.readValue(record.value(), new TypeReference<ResultDTO>() {
                });
            }
        } catch (Exception e) {
            LoggerUtil.error("??????????????????????????????", String.valueOf(record.key()), e);
        }
        return null;
    }
}
