package cn.sdh.backend.mapper;

import cn.sdh.backend.entity.StatsDaily;
import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;

import java.time.LocalDate;
import java.util.List;

@Mapper
public interface StatsDailyMapper extends BaseMapper<StatsDaily> {

    @Select("SELECT * FROM stats_daily WHERE stat_date BETWEEN #{startDate} AND #{endDate} ORDER BY stat_date")
    List<StatsDaily> selectByDateRange(@Param("startDate") LocalDate startDate, @Param("endDate") LocalDate endDate);

    @Select("SELECT * FROM stats_daily WHERE stat_type = #{statType} AND stat_date BETWEEN #{startDate} AND #{endDate}")
    List<StatsDaily> selectByTypeAndDateRange(@Param("statType") String statType, @Param("startDate") LocalDate startDate, @Param("endDate") LocalDate endDate);
}