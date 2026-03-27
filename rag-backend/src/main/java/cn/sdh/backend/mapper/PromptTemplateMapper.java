package cn.sdh.backend.mapper;

import cn.sdh.backend.entity.PromptTemplate;
import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;
import org.apache.ibatis.annotations.Update;

import java.util.List;

@Mapper
public interface PromptTemplateMapper extends BaseMapper<PromptTemplate> {

    @Select("SELECT * FROM prompt_template WHERE status = 1 ORDER BY use_count DESC, create_time DESC")
    List<PromptTemplate> selectActiveTemplates();

    @Select("SELECT * FROM prompt_template WHERE code = #{code} AND status = 1")
    PromptTemplate selectByCode(@Param("code") String code);

    @Select("SELECT * FROM prompt_template WHERE category = #{category} AND status = 1 ORDER BY use_count DESC")
    List<PromptTemplate> selectByCategory(@Param("category") String category);

    @Update("UPDATE prompt_template SET use_count = use_count + 1 WHERE id = #{id}")
    void incrementUseCount(@Param("id") Long id);
}
