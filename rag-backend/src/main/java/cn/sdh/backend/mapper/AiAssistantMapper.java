package cn.sdh.backend.mapper;

import cn.sdh.backend.entity.AiAssistant;
import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;
import org.apache.ibatis.annotations.Update;

@Mapper
public interface AiAssistantMapper extends BaseMapper<AiAssistant> {

    @Update("UPDATE ai_assistant SET use_count = use_count + 1 WHERE id = #{id}")
    int incrementUseCount(@Param("id") Long id);

    @Select("SELECT AVG(rating) FROM assistant_rating WHERE assistant_id = #{assistantId}")
    Double selectAvgRating(@Param("assistantId") Long assistantId);
}