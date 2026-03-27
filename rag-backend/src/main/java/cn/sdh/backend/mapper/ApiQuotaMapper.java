package cn.sdh.backend.mapper;

import cn.sdh.backend.entity.ApiQuota;
import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;
import org.apache.ibatis.annotations.Update;

import java.util.List;

@Mapper
public interface ApiQuotaMapper extends BaseMapper<ApiQuota> {

    @Select("SELECT * FROM api_quota WHERE user_id = #{userId} AND quota_type = #{quotaType}")
    ApiQuota selectByUserAndType(@Param("userId") Long userId, @Param("quotaType") String quotaType);

    @Select("SELECT * FROM api_quota WHERE user_id = #{userId}")
    List<ApiQuota> selectByUserId(@Param("userId") Long userId);

    @Update("UPDATE api_quota SET daily_used = daily_used + 1, monthly_used = monthly_used + 1 WHERE user_id = #{userId} AND quota_type = #{quotaType}")
    void incrementUsage(@Param("userId") Long userId, @Param("quotaType") String quotaType);

    @Update("UPDATE api_quota SET daily_used = 0, reset_date = CURDATE() WHERE reset_date < CURDATE()")
    void resetDailyQuota();

    @Update("UPDATE api_quota SET monthly_used = 0 WHERE MONTH(reset_date) != MONTH(CURDATE()) OR YEAR(reset_date) != YEAR(CURDATE())")
    void resetMonthlyQuota();
}
