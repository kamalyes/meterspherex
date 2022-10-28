package io.metersphere.base.domain;

import lombok.Data;

import java.io.Serializable;

@Data
public class OperatingLogResource implements Serializable {
    private String id;

    private String operatingLogId;

    private String sourceId;

    private static final long serialVersionUID = 1L;
}