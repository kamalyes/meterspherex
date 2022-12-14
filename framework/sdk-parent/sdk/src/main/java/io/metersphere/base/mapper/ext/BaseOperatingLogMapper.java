package io.metersphere.base.mapper.ext;

import io.metersphere.log.vo.OperatingLogDTO;
import io.metersphere.log.vo.OperatingLogRequest;
import org.apache.ibatis.annotations.Param;

import java.util.List;

public interface BaseOperatingLogMapper {

    List<OperatingLogDTO> list(@Param("request") OperatingLogRequest request);

    List<OperatingLogDTO> findBySourceId(@Param("request") OperatingLogRequest request);

    List<OperatingLogDTO> findBySourceIdEnv(@Param("request") OperatingLogRequest request);

    List<OperatingLogDTO> findSourceIdByLogIds(@Param("ids") List<String> ids);

}