package io.metersphere.base.domain;

import lombok.Data;
import lombok.EqualsAndHashCode;
import lombok.ToString;

import java.io.Serializable;

@Data
@EqualsAndHashCode(callSuper = true)
@ToString(callSuper = true)
public class LoadTestReportWithBLOBs extends LoadTestReport implements Serializable {
    private String description;

    private String loadConfiguration;

    private String jmxContent;

    private String advancedConfiguration;

    private String envInfo;

    private static final long serialVersionUID = 1L;
}