package cn.sdh.backend.mapper;

import cn.sdh.backend.entity.HotwordRecord;
import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;

import java.time.LocalDate;
import java.util.List;
import java.util.Map;

@Mapper
public interface HotwordRecordMapper extends BaseMapper<HotwordRecord> {

    @Select("SELECT word, SUM(count) as total_count FROM hotword_record " +
            "WHERE query_date >= #{startDate} AND query_date <= #{endDate} " +
            "GROUP BY word ORDER BY total_count DESC LIMIT #{limit}")
    List<Map<String, Object>> selectTopWords(@Param("startDate") LocalDate startDate,
                                              @Param("endDate") LocalDate endDate,
                                              @Param("limit") int limit);

    @Select("SELECT query_date, SUM(count) as total_count FROM hotword_record " +
            "WHERE word = #{word} AND query_date >= #{startDate} AND query_date <= #{endDate} " +
            "GROUP BY query_date ORDER BY query_date")
    List<Map<String, Object>> selectWordTrend(@Param("word") String word,
                                               @Param("startDate") LocalDate startDate,
                                               @Param("endDate") LocalDate endDate);

    @Select("SELECT COUNT(DISTINCT word) as word_count, SUM(count) as total_count " +
            "FROM hotword_record WHERE query_date >= #{startDate} AND query_date <= #{endDate}")
    Map<String, Object> selectStats(@Param("startDate") LocalDate startDate,
                                     @Param("endDate") LocalDate endDate);
}