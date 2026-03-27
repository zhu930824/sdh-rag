package cn.sdh.backend.mapper;

import cn.sdh.backend.entity.KnowledgeHealth;
import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;

import java.util.List;

@Mapper
public interface KnowledgeHealthMapper extends BaseMapper<KnowledgeHealth> {

    @Select("SELECT * FROM knowledge_health WHERE knowledge_id = #{knowledgeId} ORDER BY check_time DESC")
    List<KnowledgeHealth> selectByKnowledgeId(@Param("knowledgeId") Long knowledgeId);

    @Select("SELECT * FROM knowledge_health WHERE knowledge_id = #{knowledgeId} AND check_type = #{checkType} ORDER BY check_time DESC LIMIT 1")
    KnowledgeHealth selectLatestByType(@Param("knowledgeId") Long knowledgeId, @Param("checkType") String checkType);

    @Select("SELECT AVG(score) FROM knowledge_health WHERE knowledge_id = #{knowledgeId} AND check_time > DATE_SUB(NOW(), INTERVAL 30 DAY)")
    Double selectAverageScore(@Param("knowledgeId") Long knowledgeId);
}
