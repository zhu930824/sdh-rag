package cn.sdh.backend.mapper;

import cn.sdh.backend.entity.MemoryAbstract;
import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;

import java.time.LocalDateTime;
import java.util.List;

/**
 * 记忆摘要Mapper
 */
@Mapper
public interface MemoryAbstractMapper extends BaseMapper<MemoryAbstract> {

    @Select("SELECT * FROM memory_abstract WHERE user_id = #{userId} ORDER BY created_at DESC LIMIT #{limit}")
    List<MemoryAbstract> findByUserId(@Param("userId") Long userId, @Param("limit") int limit);

    @Select("SELECT * FROM memory_abstract WHERE needs_summary = 1 LIMIT #{limit}")
    List<MemoryAbstract> findNeedsSummary(@Param("limit") int limit);

    @Select("SELECT * FROM memory_abstract WHERE created_at < #{before} AND importance < #{minImportance}")
    List<MemoryAbstract> findExpired(@Param("before") LocalDateTime before, @Param("minImportance") int minImportance);
}
