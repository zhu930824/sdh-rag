package cn.sdh.backend.mapper;

import cn.sdh.backend.entity.PointsRecord;
import cn.sdh.backend.entity.UserProfile;
import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;
import org.apache.ibatis.annotations.Update;

@Mapper
public interface UserProfileMapper extends BaseMapper<UserProfile> {

    @Select("SELECT * FROM user_profile WHERE user_id = #{userId}")
    UserProfile selectByUserId(@Param("userId") Long userId);

    @Update("UPDATE user_profile SET points_balance = points_balance + #{points}, total_points = total_points + #{points} WHERE user_id = #{userId}")
    int addPoints(@Param("userId") Long userId, @Param("points") int points);
}