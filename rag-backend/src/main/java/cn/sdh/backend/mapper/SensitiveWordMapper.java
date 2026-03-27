package cn.sdh.backend.mapper;

import cn.sdh.backend.entity.SensitiveWord;
import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.baomidou.mybatisplus.core.metadata.IPage;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;

import java.util.List;

@Mapper
public interface SensitiveWordMapper extends BaseMapper<SensitiveWord> {

    @Select("SELECT * FROM sensitive_word WHERE status = 1")
    List<SensitiveWord> selectAllEnabled();

    @Select("SELECT * FROM sensitive_word WHERE category = #{category} AND status = 1")
    List<SensitiveWord> selectByCategory(@Param("category") String category);

    IPage<SensitiveWord> selectPageByCondition(Page<SensitiveWord> page, 
                                                @Param("keyword") String keyword,
                                                @Param("category") String category);
}