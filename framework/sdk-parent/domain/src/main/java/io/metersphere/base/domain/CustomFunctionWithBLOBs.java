package io.metersphere.base.domain;

import lombok.Data;
import lombok.EqualsAndHashCode;
import lombok.ToString;

import java.io.Serializable;

@Data
@EqualsAndHashCode(callSuper = true)
@ToString(callSuper = true)
public class CustomFunctionWithBLOBs extends CustomFunction implements Serializable {
    private String params;

    private String script;

    private String result;

    private static final long serialVersionUID = 1L;
}