package io.metersphere.base.mapper.ext;

import io.metersphere.dto.TestCaseNodeDTO;
import org.apache.ibatis.annotations.Param;

import java.util.List;

public interface ExtTestCaseNodeMapper {

    List<TestCaseNodeDTO> getNodeTreeByProjectId(@Param("projectId") String projectId);

    List<TestCaseNodeDTO> getNodeTreeByProjectIds(@Param("projectIds") List<String> projectIds);

    TestCaseNodeDTO get(String id);

    void updatePos(String id, Double pos);

    List<String> getNodes(@Param("parentId") String parentId);

    List<TestCaseNodeDTO> getNodeTreeByIds(@Param("nodeIds") List<String> nodeIds);
}
