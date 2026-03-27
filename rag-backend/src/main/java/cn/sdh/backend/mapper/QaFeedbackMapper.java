package cn.sdh.backend.mapper;

import cn.sdh.backend.entity.QaFeedback;
import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;

@Mapper
public interface QaFeedbackMapper extends BaseMapper<QaFeedback> {

    @Select("SELECT COUNT(*) FROM qa_feedback WHERE chat_history_id = #{chatHistoryId} AND rating = 1")
    int countLikes(@Param("chatHistoryId") Long chatHistoryId);

    @Select("SELECT COUNT(*) FROM qa_feedback WHERE chat_history_id = #{chatHistoryId} AND rating = 0")
    int countDislikes(@Param("chatHistoryId") Long chatHistoryId);
}