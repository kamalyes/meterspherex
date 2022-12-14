package io.metersphere.dto;

import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
public class LoadCaseCountChartResult {
    private String groupName;
    private long countNum;

    public String getCountNumStr() {
        return String.valueOf(countNum);
    }
}
