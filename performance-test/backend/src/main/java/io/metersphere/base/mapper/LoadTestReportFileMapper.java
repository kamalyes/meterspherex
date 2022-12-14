package io.metersphere.base.mapper;

import io.metersphere.base.domain.LoadTestReportFile;
import io.metersphere.base.domain.LoadTestReportFileExample;
import org.apache.ibatis.annotations.Param;

import java.util.List;

public interface LoadTestReportFileMapper {
    long countByExample(LoadTestReportFileExample example);

    int deleteByExample(LoadTestReportFileExample example);

    int insert(LoadTestReportFile record);

    int insertSelective(LoadTestReportFile record);

    List<LoadTestReportFile> selectByExample(LoadTestReportFileExample example);

    int updateByExampleSelective(@Param("record") LoadTestReportFile record, @Param("example") LoadTestReportFileExample example);

    int updateByExample(@Param("record") LoadTestReportFile record, @Param("example") LoadTestReportFileExample example);
}