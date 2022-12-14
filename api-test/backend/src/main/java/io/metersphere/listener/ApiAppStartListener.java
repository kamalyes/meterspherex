package io.metersphere.listener;

import com.mchange.lang.IntegerUtils;
import io.metersphere.api.exec.queue.ExecThreadPoolExecutor;
import io.metersphere.api.jmeter.JMeterService;
import io.metersphere.service.ApiExecutionQueueService;
import io.metersphere.service.MockConfigService;
import io.metersphere.service.PluginService;
import io.metersphere.commons.constants.ScheduleGroup;
import io.metersphere.commons.utils.CommonBeanFactory;
import io.metersphere.commons.utils.LogUtil;
import io.metersphere.dto.BaseSystemConfigDTO;
import io.metersphere.service.BaseScheduleService;
import io.metersphere.service.SystemParameterService;
import org.apache.commons.lang3.StringUtils;
import org.python.core.Options;
import org.python.util.PythonInterpreter;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.context.ApplicationListener;
import org.springframework.stereotype.Component;

import javax.annotation.Resource;

@Component
public class ApiAppStartListener implements ApplicationRunner {
    @Resource
    private BaseScheduleService scheduleService;
    @Resource
    private JMeterService jMeterService;
    @Resource
    private MockConfigService mockConfigService;
    @Resource
    private SystemParameterService systemParameterService;
    @Resource
    private PluginService pluginService;
    @Resource
    private ApiExecutionQueueService apiExecutionQueueService;

    @Value("${jmeter.home}")
    private String jmeterHome;
    @Value("${quartz.properties.org.quartz.jobStore.acquireTriggersWithinLock}")
    private String acquireTriggersWithinLock;
    @Value("${quartz.enabled}")
    private boolean quartzEnable;
    @Value("${quartz.scheduler-name}")
    private String quartzScheduleName;
    @Value("${quartz.thread-count}")
    private int quartzThreadCount;

    @Override
    public void run(ApplicationArguments args) throws Exception {
        LogUtil.info("================= API ???????????? =================");
        System.setProperty("jmeter.home", jmeterHome);

        LogUtil.info("?????????????????????");
        pluginService.loadPlugins();

        LogUtil.info("??????????????????????????????????????????");
        apiExecutionQueueService.exceptionHandling();

        BaseSystemConfigDTO dto = systemParameterService.getBaseInfo();
        LogUtil.info("???????????????????????????", dto.getConcurrency());
        if (StringUtils.isNotEmpty(dto.getConcurrency())) {
            CommonBeanFactory.getBean(ExecThreadPoolExecutor.class).setCorePoolSize(IntegerUtils.parseInt(dto.getConcurrency(), 10));
        }

        LogUtil.info("????????????python?????????");
        initPythonEnv();

        //????????????????????????TCP-Mock????????????
        LogUtil.info("starting check mock ");
        mockConfigService.initMockTcpService();

        LogUtil.info("starting quartz");
        StringBuffer buffer = new StringBuffer("???????????????????????????");
        buffer.append("quartz.acquireTriggersWithinLock :")
                .append(acquireTriggersWithinLock).append(StringUtils.LF)
                .append("quartz.enabled ")
                .append(quartzEnable).append(StringUtils.LF)
                .append("quartz.scheduler-name ")
                .append(quartzScheduleName).append(StringUtils.LF)
                .append("quartz.thread-count ")
                .append(quartzThreadCount).append(StringUtils.LF);

        LogUtil.info(buffer.toString());
        scheduleService.startEnableSchedules(ScheduleGroup.API_SCENARIO_TEST);
        scheduleService.startEnableSchedules(ScheduleGroup.SWAGGER_IMPORT);
        LogUtil.info("Startup complete");
    }


    /**
     * ??????????????????-??????????????????python???
     */
    private void initPythonEnv() {
        //?????????????????? PyScriptEngineFactory
        try {
            Options.importSite = false;
            PythonInterpreter interpreter = new PythonInterpreter();
            StringBuffer pathBuffer = new StringBuffer(jMeterService.getJmeterHome());
            pathBuffer.append("/lib/ext/jython-standalone.jar/Lib");
            interpreter.exec("import sys");
            interpreter.exec("sys.path.append(\"" + pathBuffer.toString() + "\")");
            LogUtil.info("sys.path: ", pathBuffer.toString());
        } catch (Exception e) {
            LogUtil.error("????????????python??????????????? ", e);
        }
    }
}
