package io.metersphere.environment.dto;

import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
public class MockConfigRequestParams {
    private String key;
    private String condition;
    private String value;
}
