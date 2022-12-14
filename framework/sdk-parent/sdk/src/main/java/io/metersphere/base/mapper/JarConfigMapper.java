package io.metersphere.base.mapper;

import io.metersphere.base.domain.JarConfig;
import io.metersphere.base.domain.JarConfigExample;
import org.apache.ibatis.annotations.Param;

import java.util.List;

public interface JarConfigMapper {
    long countByExample(JarConfigExample example);

    int deleteByExample(JarConfigExample example);

    int deleteByPrimaryKey(String id);

    int insert(JarConfig record);

    int insertSelective(JarConfig record);

    List<JarConfig> selectByExample(JarConfigExample example);

    JarConfig selectByPrimaryKey(String id);

    int updateByExampleSelective(@Param("record") JarConfig record, @Param("example") JarConfigExample example);

    int updateByExample(@Param("record") JarConfig record, @Param("example") JarConfigExample example);

    int updateByPrimaryKeySelective(JarConfig record);

    int updateByPrimaryKey(JarConfig record);
}