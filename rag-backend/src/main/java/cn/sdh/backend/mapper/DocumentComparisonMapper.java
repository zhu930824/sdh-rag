package cn.sdh.backend.mapper;

import cn.sdh.backend.entity.DocumentComparison;
import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;

import java.util.List;

@Mapper
public interface DocumentComparisonMapper extends BaseMapper<DocumentComparison> {

    @Select("SELECT * FROM document_comparison WHERE document_id_1 = #{docId} OR document_id_2 = #{docId} ORDER BY create_time DESC")
    List<DocumentComparison> selectByDocumentId(@Param("docId") Long docId);

    @Select("SELECT * FROM document_comparison WHERE document_id_1 = #{docId1} AND document_id_2 = #{docId2} OR document_id_1 = #{docId2} AND document_id_2 = #{docId1} ORDER BY create_time DESC LIMIT 1")
    DocumentComparison selectByDocumentPair(@Param("docId1") Long docId1, @Param("docId2") Long docId2);
}
