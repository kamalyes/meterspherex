package io.metersphere.api.dto.definition.request.processors.pre;


import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.DeserializationFeature;
import com.fasterxml.jackson.databind.ObjectMapper;
import io.metersphere.api.dto.definition.request.ElementUtil;
import io.metersphere.api.dto.definition.request.ParameterConfig;
import io.metersphere.api.dto.scenario.DatabaseConfig;
import io.metersphere.api.dto.scenario.KeyValue;
import io.metersphere.api.dto.scenario.environment.EnvironmentConfig;
import io.metersphere.service.definition.ApiDefinitionService;
import io.metersphere.service.definition.ApiTestCaseService;
import io.metersphere.base.domain.ApiDefinitionWithBLOBs;
import io.metersphere.base.domain.ApiTestCaseWithBLOBs;
import io.metersphere.base.domain.ApiTestEnvironmentWithBLOBs;
import io.metersphere.commons.constants.ElementConstants;
import io.metersphere.commons.constants.MsTestElementConstants;
import io.metersphere.commons.constants.RequestTypeConstants;
import io.metersphere.commons.exception.MSException;
import io.metersphere.commons.utils.CommonBeanFactory;
import io.metersphere.commons.utils.LogUtil;
import io.metersphere.constants.RunModeConstants;
import io.metersphere.environment.service.BaseEnvironmentService;
import io.metersphere.plugin.core.MsParameter;
import io.metersphere.plugin.core.MsTestElement;
import io.metersphere.commons.utils.JSONUtil;
import lombok.Data;
import lombok.EqualsAndHashCode;
import org.apache.commons.collections.CollectionUtils;
import org.apache.commons.lang3.StringUtils;
import org.apache.jmeter.config.Arguments;
import org.apache.jmeter.protocol.jdbc.config.DataSourceElement;
import org.apache.jmeter.protocol.jdbc.processor.JDBCPreProcessor;
import org.apache.jmeter.save.SaveService;
import org.apache.jmeter.testelement.TestElement;
import org.apache.jorphan.collections.HashTree;
import org.json.JSONObject;

import java.util.Iterator;
import java.util.List;
import java.util.stream.Collectors;

/**
 * @author song.tianyang
 * @Date 2021/7/13 11:08 ??????
 */
@Data
@EqualsAndHashCode(callSuper = true)

public class MsJDBCPreProcessor extends MsTestElement {
    // type ???????????????????????????????????????????????????
    private String type = ElementConstants.JDBC_PRE;
    private String clazzName = MsJDBCPreProcessor.class.getCanonicalName();
    private DatabaseConfig dataSource;
    private String query;
    private long queryTimeout;
    private String resultVariable;
    private String variableNames;
    private List<KeyValue> variables;
    private String environmentId;
    private String dataSourceId;
    private String protocol = RequestTypeConstants.SQL;
    private String useEnvironment;

    @Override
    public void toHashTree(HashTree tree, List<MsTestElement> hashTree, MsParameter msParameter) {
        ParameterConfig config = (ParameterConfig) msParameter;
        // ??????????????????
        this.dataSource = null;
        // ??????????????????????????????????????????????????????
        if (!config.isOperating() && !this.isEnable()) {
            return;
        }
        if (this.getReferenced() != null && MsTestElementConstants.REF.name().equals(this.getReferenced())) {
            this.setRefElement();
        }
        if (config.getConfig() == null) {
            // ??????????????????
            this.setProjectId(config.getProjectId());
            config.setConfig(ElementUtil.getEnvironmentConfig(StringUtils.isNotEmpty(useEnvironment) ? useEnvironment : environmentId, this.getProjectId()));
        }

        // ??????????????????
        if (config.getConfig() != null && StringUtils.isNotEmpty(this.getProjectId()) && config.getConfig().containsKey(this.getProjectId())) {
            // 1.8 ?????? ??????????????????
        } else if (config.getConfig() != null && config.getConfig().containsKey(getParentProjectId())) {
            // 1.8 ?????? ????????????
            this.setProjectId(getParentProjectId());
        } else {
            // 1.8 ?????? ??????
            if (config.getConfig() != null) {
                if (config.getConfig().containsKey(RunModeConstants.HIS_PRO_ID.toString())) {
                    this.setProjectId(RunModeConstants.HIS_PRO_ID.toString());
                } else {
                    // ??????????????????
                    Iterator<String> it = config.getConfig().keySet().iterator();
                    if (it.hasNext()) {
                        this.setProjectId(it.next());
                    }
                }
            }
        }
        //????????????????????????null????????????????????????????????????
        if (this.dataSource == null) {
            // ??????????????????
            if (config.isEffective(this.getProjectId()) && CollectionUtils.isNotEmpty(config.getConfig().get(this.getProjectId()).getDatabaseConfigs())
                    && isDataSource(config.getConfig().get(this.getProjectId()).getDatabaseConfigs())) {
                EnvironmentConfig environmentConfig = config.getConfig().get(this.getProjectId());
                if (environmentConfig.getDatabaseConfigs() != null && StringUtils.isNotEmpty(environmentConfig.getEnvironmentId())) {
                    this.environmentId = environmentConfig.getEnvironmentId();
                }
                this.initDataSource();
            } else {
                // ??????????????????????????????????????????
                if (config.isEffective(this.getProjectId()) && CollectionUtils.isNotEmpty(config.getConfig().get(this.getProjectId()).getDatabaseConfigs())) {
                    DatabaseConfig dataSourceOrg = ElementUtil.dataSource(getProjectId(), dataSourceId, config.getConfig().get(this.getProjectId()));
                    if (dataSourceOrg != null) {
                        this.dataSource = dataSourceOrg;
                    } else {
                        this.dataSource = config.getConfig().get(this.getProjectId()).getDatabaseConfigs().get(0);
                    }
                }
            }
        }
        if (this.dataSource == null) {
            // ??????????????????
            if (StringUtils.isNotEmpty(dataSourceId)) {
                this.dataSource = null;
                this.initDataSource();
            }
            if (this.dataSource == null) {
                String message = "?????????????????????????????????";
                MSException.throwException(StringUtils.isNotEmpty(this.getName()) ? this.getName() + "???" + message : message);
            }
        }
        final HashTree samplerHashTree = tree.add(jdbcPreProcessor(config));
        tree.add(jdbcDataSource());
        Arguments arguments = arguments(StringUtils.isNotEmpty(this.getName()) ? this.getName() : "Arguments", this.getVariables());
        if (arguments != null) {
            tree.add(arguments);
        }
        if (CollectionUtils.isNotEmpty(hashTree)) {
            hashTree.forEach(el -> {
                el.toHashTree(samplerHashTree, el.getHashTree(), config);
            });
        }
    }

    private boolean isDataSource(List<DatabaseConfig> databaseConfigs) {
        List<String> ids = databaseConfigs.stream().map(DatabaseConfig::getId).collect(Collectors.toList());
        if (StringUtils.isNotEmpty(this.dataSourceId) && ids.contains(this.dataSourceId)) {
            return true;
        }
        return false;
    }

    private String getParentProjectId() {
        MsTestElement parent = this.getParent();
        while (parent != null) {
            if (StringUtils.isNotBlank(parent.getProjectId())) {
                return parent.getProjectId();
            }
            parent = parent.getParent();
        }
        return "";
    }

    private void setRefElement() {
        try {
            ApiDefinitionService apiDefinitionService = CommonBeanFactory.getBean(ApiDefinitionService.class);
            ObjectMapper mapper = new ObjectMapper();
            mapper.configure(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES, false);
            MsJDBCPreProcessor proxy = null;
            if (StringUtils.equals(this.getRefType(), "CASE")) {
                ApiTestCaseService apiTestCaseService = CommonBeanFactory.getBean(ApiTestCaseService.class);
                ApiTestCaseWithBLOBs bloBs = apiTestCaseService.get(this.getId());
                if (bloBs != null) {
                    this.setName(bloBs.getName());
                    this.setProjectId(bloBs.getProjectId());
                    JSONObject element = JSONUtil.parseObject(bloBs.getRequest());
                    ElementUtil.dataFormatting(element);
                    proxy = mapper.readValue(element.toString(), new TypeReference<MsJDBCPreProcessor>() {
                    });
                }
            } else {
                ApiDefinitionWithBLOBs apiDefinition = apiDefinitionService.getBLOBs(this.getId());
                if (apiDefinition != null) {
                    this.setProjectId(apiDefinition.getProjectId());
                    proxy = mapper.readValue(apiDefinition.getRequest(), new TypeReference<MsJDBCPreProcessor>() {
                    });
                    this.setName(apiDefinition.getName());
                }
            }
            if (proxy != null) {
                this.setHashTree(proxy.getHashTree());
                this.setDataSource(proxy.getDataSource());
                this.setDataSourceId(proxy.getDataSourceId());
                this.setQuery(proxy.getQuery());
                this.setVariables(proxy.getVariables());
                this.setVariableNames(proxy.getVariableNames());
                this.setResultVariable(proxy.getResultVariable());
                this.setQueryTimeout(proxy.getQueryTimeout());
            }
        } catch (Exception ex) {
            ex.printStackTrace();
            LogUtil.error(ex);
        }
    }

    private void initDataSource() {
        BaseEnvironmentService apiTestEnvironmentService = CommonBeanFactory.getBean(BaseEnvironmentService.class);
        ApiTestEnvironmentWithBLOBs environment = apiTestEnvironmentService.get(environmentId);
        if (environment != null && environment.getConfig() != null) {
            EnvironmentConfig envConfig = JSONUtil.parseObject(environment.getConfig(), EnvironmentConfig.class);
            if (CollectionUtils.isNotEmpty(envConfig.getDatabaseConfigs())) {
                envConfig.getDatabaseConfigs().forEach(item -> {
                    if (item.getId().equals(this.dataSourceId)) {
                        this.dataSource = item;
                        return;
                    }
                });
            }
        }
    }

    private Arguments arguments(String name, List<KeyValue> variables) {
        if (CollectionUtils.isNotEmpty(variables)) {
            Arguments arguments = new Arguments();
            arguments.setEnabled(true);
            arguments.setName(name + "JDBC_Argument");
            arguments.setProperty(TestElement.TEST_CLASS, Arguments.class.getName());
            arguments.setProperty(TestElement.GUI_CLASS, SaveService.aliasToClass("ArgumentsPanel"));
            variables.stream().filter(KeyValue::isValid).filter(KeyValue::isEnable).forEach(keyValue ->
                    arguments.addArgument(keyValue.getName(), ElementUtil.getEvlValue(keyValue.getValue()), "=")
            );
            return arguments;
        }
        return null;
    }

    private JDBCPreProcessor jdbcPreProcessor(ParameterConfig config) {
        JDBCPreProcessor jdbcPreProcessor = new JDBCPreProcessor();
        jdbcPreProcessor.setEnabled(this.isEnable());
        jdbcPreProcessor.setName(this.getName() == null ? ElementConstants.JDBC_PRE : this.getName());
        jdbcPreProcessor.setProperty(TestElement.TEST_CLASS, JDBCPreProcessor.class.getName());
        jdbcPreProcessor.setProperty(TestElement.GUI_CLASS, SaveService.aliasToClass("TestBeanGUI"));

        ElementUtil.setBaseParams(jdbcPreProcessor, this.getParent(), config, this.getId(), this.getIndex());

        jdbcPreProcessor.setProperty("dataSource", this.dataSource.getName());
        jdbcPreProcessor.setProperty("query", this.getQuery());
        jdbcPreProcessor.setProperty("queryTimeout", String.valueOf(this.getQueryTimeout()));
        jdbcPreProcessor.setProperty("resultVariable", this.getResultVariable());
        jdbcPreProcessor.setProperty("variableNames", this.getVariableNames());
        jdbcPreProcessor.setProperty("resultSetHandler", "Store as String");
        jdbcPreProcessor.setProperty("queryType", "Callable Statement");
        return jdbcPreProcessor;
    }

    private DataSourceElement jdbcDataSource() {
        DataSourceElement dataSourceElement = new DataSourceElement();
        dataSourceElement.setEnabled(true);
        dataSourceElement.setName(this.getName() + " JDBCDataSource");
        dataSourceElement.setProperty(TestElement.TEST_CLASS, DataSourceElement.class.getName());
        dataSourceElement.setProperty(TestElement.GUI_CLASS, SaveService.aliasToClass("TestBeanGUI"));
        dataSourceElement.setProperty("autocommit", true);
        dataSourceElement.setProperty("keepAlive", true);
        dataSourceElement.setProperty("preinit", false);
        dataSourceElement.setProperty("dataSource", dataSource.getName());
        dataSourceElement.setProperty("dbUrl", dataSource.getDbUrl());
        dataSourceElement.setProperty("driver", dataSource.getDriver());
        dataSourceElement.setProperty("username", dataSource.getUsername());
        dataSourceElement.setProperty("password", dataSource.getPassword());
        dataSourceElement.setProperty("poolMax", dataSource.getPoolMax());
        dataSourceElement.setProperty("timeout", String.valueOf(dataSource.getTimeout()));
        dataSourceElement.setProperty("connectionAge", 5000);
        dataSourceElement.setProperty("trimInterval", 6000);
        dataSourceElement.setProperty("transactionIsolation", "DEFAULT");
        return dataSourceElement;
    }
}
