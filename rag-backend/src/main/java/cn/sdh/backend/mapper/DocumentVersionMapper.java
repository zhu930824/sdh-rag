package cn.sdh.backend.mapper;

import cn.sdh.backend.entity.DocumentVersion;
import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;

import java.util.List;

@Mapper
public interface DocumentVersionMapper extends BaseMapper<DocumentVersion> {

    @Select("SELECT * FROM document_version WHERE document_id = #{documentId} ORDER BY version_number DESC")
    List<DocumentVersion> selectByDocumentId(@Param("documentId") Long documentId);

    @Select("SELECT MAX(version_number) FROM document_version WHERE document_id = #{documentId}")
    Integer selectMaxVersion(@Param("documentId") Long documentId);
}