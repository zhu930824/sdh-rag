package cn.sdh.backend.mapper;

import cn.sdh.backend.entity.SessionShare;
import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;
import org.apache.ibatis.annotations.Update;

@Mapper
public interface SessionShareMapper extends BaseMapper<SessionShare> {

    @Select("SELECT * FROM session_share WHERE share_code = #{shareCode} AND status = 1")
    SessionShare selectByShareCode(@Param("shareCode") String shareCode);

    @Select("SELECT * FROM session_share WHERE session_id = #{sessionId} AND user_id = #{userId} AND status = 1")
    SessionShare selectActiveBySession(@Param("sessionId") String sessionId, @Param("userId") Long userId);

    @Update("UPDATE session_share SET view_count = view_count + 1 WHERE share_code = #{shareCode}")
    void incrementViewCount(@Param("shareCode") String shareCode);

    @Update("UPDATE session_share SET status = 0 WHERE expire_time < NOW()")
    void expireOldShares();
}
