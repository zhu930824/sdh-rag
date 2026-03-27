package cn.sdh.backend.mapper;

import cn.sdh.backend.entity.ChatSession;
import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;
import org.apache.ibatis.annotations.Update;

import java.util.List;

@Mapper
public interface ChatSessionMapper extends BaseMapper<ChatSession> {

    @Select("SELECT * FROM chat_session WHERE session_id = #{sessionId}")
    ChatSession selectBySessionId(@Param("sessionId") String sessionId);

    @Select("SELECT * FROM chat_session WHERE user_id = #{userId} ORDER BY last_message_time DESC")
    List<ChatSession> selectByUserId(@Param("userId") Long userId);

    @Select("SELECT * FROM chat_session WHERE user_id = #{userId} AND is_starred = 1 ORDER BY last_message_time DESC")
    List<ChatSession> selectStarredByUserId(@Param("userId") Long userId);

    @Select("SELECT * FROM chat_session WHERE user_id = #{userId} AND is_archived = 1 ORDER BY last_message_time DESC")
    List<ChatSession> selectArchivedByUserId(@Param("userId") Long userId);

    @Update("UPDATE chat_session SET message_count = message_count + 1, total_tokens = total_tokens + #{tokens}, last_message_time = NOW() WHERE session_id = #{sessionId}")
    void updateMessageStats(@Param("sessionId") String sessionId, @Param("tokens") Integer tokens);

    @Update("UPDATE chat_session SET is_starred = #{starred} WHERE session_id = #{sessionId}")
    void updateStarred(@Param("sessionId") String sessionId, @Param("starred") Byte starred);

    @Update("UPDATE chat_session SET title = #{title} WHERE session_id = #{sessionId}")
    void updateTitle(@Param("sessionId") String sessionId, @Param("title") String title);
}
