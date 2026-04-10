package cn.sdh.backend.mapper;

import cn.sdh.backend.entity.TokenUsage;
import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Select;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;

/**
 * Token 使用记录 Mapper
 */
@Mapper
public interface TokenUsageMapper extends BaseMapper<TokenUsage> {

    /**
     * 按日期统计 token 使用量
     */
    @Select("SELECT DATE(create_time) as date, " +
            "SUM(prompt_tokens) as promptTokens, " +
            "SUM(completion_tokens) as completionTokens, " +
            "SUM(total_tokens) as totalTokens, " +
            "COUNT(*) as requestCount " +
            "FROM token_usage " +
            "WHERE user_id = #{userId} " +
            "AND create_time BETWEEN #{startTime} AND #{endTime} " +
            "GROUP BY DATE(create_time) " +
            "ORDER BY date")
    List<Map<String, Object>> statsByDate(Long userId, LocalDateTime startTime, LocalDateTime endTime);

    /**
     * 按模型统计 token 使用量
     */
    @Select("SELECT model_name as modelName, provider, " +
            "SUM(prompt_tokens) as promptTokens, " +
            "SUM(completion_tokens) as completionTokens, " +
            "SUM(total_tokens) as totalTokens, " +
            "COUNT(*) as requestCount " +
            "FROM token_usage " +
            "WHERE user_id = #{userId} " +
            "AND create_time BETWEEN #{startTime} AND #{endTime} " +
            "GROUP BY model_name, provider " +
            "ORDER BY totalTokens DESC")
    List<Map<String, Object>> statsByModel(Long userId, LocalDateTime startTime, LocalDateTime endTime);

    /**
     * 获取用户总统计
     */
    @Select("SELECT " +
            "SUM(prompt_tokens) as totalPromptTokens, " +
            "SUM(completion_tokens) as totalCompletionTokens, " +
            "SUM(total_tokens) as totalTokens, " +
            "COUNT(*) as totalRequests " +
            "FROM token_usage " +
            "WHERE user_id = #{userId}")
    Map<String, Object> getUserTotalStats(Long userId);

    /**
     * 获取全局统计
     */
    @Select("SELECT " +
            "SUM(prompt_tokens) as totalPromptTokens, " +
            "SUM(completion_tokens) as totalCompletionTokens, " +
            "SUM(total_tokens) as totalTokens, " +
            "COUNT(*) as totalRequests " +
            "FROM token_usage")
    Map<String, Object> getGlobalStats();
}
