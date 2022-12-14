package io.metersphere.api.dto.definition.request.sampler;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.DeserializationFeature;
import com.fasterxml.jackson.databind.ObjectMapper;
import io.metersphere.api.parse.api.JMeterScriptUtil;
import io.metersphere.api.dto.definition.request.ElementUtil;
import io.metersphere.api.dto.definition.request.ParameterConfig;
import io.metersphere.api.dto.scenario.DatabaseConfig;
import io.metersphere.api.dto.scenario.KeyValue;
import io.metersphere.api.dto.scenario.environment.EnvironmentConfig;
import io.metersphere.api.dto.scenario.environment.GlobalScriptFilterRequest;
import io.metersphere.service.definition.ApiDefinitionService;
import io.metersphere.service.definition.ApiTestCaseService;
import io.metersphere.base.domain.ApiDefinitionWithBLOBs;
import io.metersphere.base.domain.ApiTestCaseWithBLOBs;
import io.metersphere.base.domain.ApiTestEnvironmentWithBLOBs;
import io.metersphere.commons.constants.ElementConstants;
import io.metersphere.commons.constants.MsTestElementConstants;
import io.metersphere.commons.exception.MSException;
import io.metersphere.commons.utils.CommonBeanFactory;
import io.metersphere.commons.utils.LogUtil;
import io.metersphere.constants.RunModeConstants;
import io.metersphere.environment.service.BaseEnvironmentService;
import io.metersphere.plugin.core.MsParameter;
import io.metersphere.plugin.core.MsTestElement;
import io.metersphere.commons.utils.HashTreeUtil;
import io.metersphere.commons.utils.JSONUtil;
import io.metersphere.utils.LoggerUtil;
import lombok.Data;
import lombok.EqualsAndHashCode;
import org.apache.commons.collections.CollectionUtils;
import org.apache.commons.lang3.StringUtils;
import org.apache.jmeter.config.Arguments;
import org.apache.jmeter.protocol.jdbc.config.DataSourceElement;
import org.apache.jmeter.protocol.jdbc.sampler.JDBCSampler;
import org.apache.jmeter.save.SaveService;
import org.apache.jmeter.testelement.TestElement;
import org.apache.jorphan.collections.HashTree;
import org.json.JSONObject;

import java.util.Iterator;
import java.util.List;
import java.util.stream.Collectors;

@Data
@EqualsAndHashCode(callSuper = true)
public class MsJDBCSampler extends MsTestElement {
    // type ???????????????????????????????????????????????????
    private String type = ElementConstants.JDBC_SAMPLER;
    private String clazzName = MsJDBCSampler.class.getCanonicalName();
    private DatabaseConfig dataSource;
    private String query;
    private long queryTimeout;
    private String resultVariable;
    private String variableNames;
    private List<KeyValue> variables;
    private String environmentId;
    private String dataSourceId;
    private String protocol = "SQL";
    private String useEnvironment;
    private boolean customizeReq;
    private Boolean isRefEnvironment;

    @Override
    public void toHashTree(HashTree tree, List<MsTestElement> hashTree, MsParameter msParameter) {
        ParameterConfig config = (ParameterConfig) msParameter;
        // ???????????????????????????
        this.dataSource = null;
        // ??????????????????????????????????????????????????????
        if (config != null && !config.isOperating() && !this.isEnable()) {
            return;
        } else if (config.isOperating() && StringUtils.isNotEmpty(config.getOperatingSampleTestName())) {
            this.setName(config.getOperatingSampleTestName());
        }
        if (this.getReferenced() != null && MsTestElementConstants.REF.name().equals(this.getReferenced())) {
            boolean ref = this.setRefElement();
            if (!ref) {
                LoggerUtil.error("??????????????????????????????" + this.getId());
                return;
            }
            hashTree = this.getHashTree();
        }
        if (config != null && config.getConfig() == null) {
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
        EnvironmentConfig envConfig = null;
        // ?????????????????????????????????????????????
        if (StringUtils.equalsIgnoreCase(this.getReferenced(), "Created") && (isRefEnvironment == null || !isRefEnvironment)) {
            this.dataSource = null;
            envConfig = this.initDataSource();
        } else {
            if (config.isEffective(this.getProjectId()) && CollectionUtils.isNotEmpty(config.getConfig().get(this.getProjectId()).getDatabaseConfigs())
                    && isDataSource(config.getConfig().get(this.getProjectId()).getDatabaseConfigs())) {
                EnvironmentConfig environmentConfig = config.getConfig().get(this.getProjectId());
                if (environmentConfig.getDatabaseConfigs() != null && StringUtils.isNotEmpty(environmentConfig.getEnvironmentId())) {
                    this.environmentId = environmentConfig.getEnvironmentId();
                }
                this.dataSource = null;
                envConfig = this.initDataSource();
            } else {
                // ??????????????????????????????????????????
                if (config.isEffective(this.getProjectId())) {
                    if (config.getConfig().get(this.getProjectId()) != null) {
                        envConfig = config.getConfig().get(this.getProjectId());
                        if (CollectionUtils.isNotEmpty(envConfig.getDatabaseConfigs())) {
                            DatabaseConfig dataSourceOrg = ElementUtil.dataSource(getProjectId(), dataSourceId, envConfig);
                            if (dataSourceOrg != null) {
                                this.dataSource = dataSourceOrg;
                            } else {
                                this.dataSource = envConfig.getDatabaseConfigs().get(0);
                            }
                        }
                    }
                }
            }
        }
        if (this.dataSource == null) {
            String message = "?????????????????????????????????";
            MSException.throwException(StringUtils.isNotEmpty(this.getName()) ? this.getName() + "???" + message : message);
        }
        final HashTree samplerHashTree = tree.add(jdbcSampler(config));
        tree.add(jdbcDataSource());
        Arguments arguments = arguments(StringUtils.isNotEmpty(this.getName()) ? this.getName() : "Arguments", this.getVariables());
        if (arguments != null) {
            tree.add(arguments);
        }
        // ?????????????????????
        Arguments envArguments = ElementUtil.getConfigArguments(config, this.getName(), this.getProjectId(), null);
        if (envArguments != null) {
            tree.add(envArguments);
        }
        //??????csv
        ElementUtil.addApiVariables(config, tree, this.getProjectId());
        //???????????????????????????
        HashTreeUtil.addPositive(envConfig, samplerHashTree, config, this.getProjectId());
        //???????????????????????????(?????????)
        String environmentId = this.getEnvironmentId();
        if (environmentId == null) {
            environmentId = this.useEnvironment;
        }
        //????????????????????????????????????????????????
        JMeterScriptUtil.setScriptByEnvironmentConfig(envConfig, samplerHashTree, GlobalScriptFilterRequest.JDBC.name(), environmentId, config, false);
        if (CollectionUtils.isNotEmpty(hashTree)) {
            hashTree = ElementUtil.order(hashTree);
            hashTree.forEach(el -> {
                el.toHashTree(samplerHashTree, el.getHashTree(), config);
            });
        }
        //????????????????????????????????????????????????
        JMeterScriptUtil.setScriptByEnvironmentConfig(envConfig, samplerHashTree, GlobalScriptFilterRequest.JDBC.name(), environmentId, config, true);
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

    private boolean setRefElement() {
        try {
            ApiDefinitionService apiDefinitionService = CommonBeanFactory.getBean(ApiDefinitionService.class);
            ObjectMapper mapper = new ObjectMapper();
            mapper.configure(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES, false);
            MsJDBCSampler proxy = null;
            if (StringUtils.equals(this.getRefType(), "CASE")) {
                ApiTestCaseService apiTestCaseService = CommonBeanFactory.getBean(ApiTestCaseService.class);
                ApiTestCaseWithBLOBs bloBs = apiTestCaseService.get(this.getId());
                if (bloBs != null) {
                    this.setName(bloBs.getName());
                    this.setProjectId(bloBs.getProjectId());
                    JSONObject element = JSONUtil.parseObject(bloBs.getRequest());
                    ElementUtil.dataFormatting(element);
                    proxy = mapper.readValue(element.toString(), new TypeReference<MsJDBCSampler>() {
                    });
                }
            } else {
                ApiDefinitionWithBLOBs apiDefinition = apiDefinitionService.getBLOBs(this.getId());
                if (apiDefinition != null) {
                    this.setProjectId(apiDefinition.getProjectId());
                    proxy = mapper.readValue(apiDefinition.getRequest(), new TypeReference<MsJDBCSampler>() {
                    });
                    this.setName(apiDefinition.getName());
                }
            }
            if (proxy != null) {
                if (StringUtils.equals(this.getRefType(), "CASE")) {
                    ElementUtil.mergeHashTree(this, proxy.getHashTree());
                } else {
                    this.setHashTree(proxy.getHashTree());
                }
                this.setDataSource(proxy.getDataSource());
                this.setDataSourceId(proxy.getDataSourceId());
                this.setQuery(proxy.getQuery());
                this.setVariables(proxy.getVariables());
                this.setVariableNames(proxy.getVariableNames());
                this.setResultVariable(proxy.getResultVariable());
                this.setQueryTimeout(proxy.getQueryTimeout());
                return true;
            }
        } catch (Exception ex) {
            LogUtil.error(ex);
        }
        return false;
    }

    private EnvironmentConfig initDataSource() {
        BaseEnvironmentService apiTestEnvironmentService = CommonBeanFactory.getBean(BaseEnvironmentService.class);
        ApiTestEnvironmentWithBLOBs environment = apiTestEnvironmentService.get(environmentId);
        EnvironmentConfig envConfig = null;
        if (environment != null && environment.getConfig() != null) {
            envConfig = JSONUtil.parseObject(environment.getConfig(), EnvironmentConfig.class);
            if (CollectionUtils.isNotEmpty(envConfig.getDatabaseConfigs())) {
                envConfig.getDatabaseConfigs().forEach(item -> {
                    if (item.getId().equals(this.dataSourceId)) {
                        this.dataSource = item;
                        return;
                    }
                });
            }
        }
        return envConfig;
    }

    private Arguments arguments(String name, List<KeyValue> variables) {
        if (CollectionUtils.isNotEmpty(variables)) {
            Arguments arguments = new Arguments();
            arguments.setEnabled(true);
            arguments.setName(name + "JDBC_Argument");
            arguments.setProperty(TestElement.TEST_CLASS, Arguments.class.getName());
            arguments.setProperty(TestElement.GUI_CLASS, SaveService.aliasToClass("ArgumentsPanel"));
            variables.stream().filter(KeyValue::isValid).filter(KeyValue::isEnable).forEach(keyValue ->
                    arguments.addArgument(keyValue.getName(), keyValue.getValue(), "=")
            );
            return arguments;
        }
        return null;
    }

    private JDBCSampler jdbcSampler(ParameterConfig config) {
        JDBCSampler sampler = new JDBCSampler();
        sampler.setEnabled(this.isEnable());
        sampler.setName(this.getName());
        if (config.isOperating()) {
            String[] testNameArr = sampler.getName().split("<->");
            if (testNameArr.length > 0) {
                String testName = testNameArr[0];
                sampler.setName(testName);
            }
        }
        sampler.setProperty(TestElement.TEST_CLASS, JDBCSampler.class.getName());
        sampler.setProperty(TestElement.GUI_CLASS, SaveService.aliasToClass("TestBeanGUI"));
        ElementUtil.setBaseParams(sampler, this.getParent(), config, this.getId(), this.getIndex());
        sampler.setProperty("dataSource", this.dataSource.getName());
        sampler.setProperty("query", this.getQuery());
        sampler.setProperty("queryTimeout", String.valueOf(this.getQueryTimeout()));
        sampler.setProperty("resultVariable", this.getResultVariable());
        sampler.setProperty("variableNames", this.getVariableNames());
        sampler.setProperty("resultSetHandler", "Store as String");
        sampler.setProperty("queryType", "Callable Statement");
        return sampler;
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
