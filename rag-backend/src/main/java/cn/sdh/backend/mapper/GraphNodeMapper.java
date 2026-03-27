package cn.sdh.backend.mapper;

import cn.sdh.backend.entity.GraphNode;
import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;

import java.util.List;

@Mapper
public interface GraphNodeMapper extends BaseMapper<GraphNode> {

    @Select("SELECT * FROM graph_node WHERE document_id = #{documentId}")
    List<GraphNode> selectByDocumentId(@Param("documentId") Long documentId);

    @Select("SELECT * FROM graph_node WHERE node_type = #{nodeType} ORDER BY weight DESC LIMIT #{limit}")
    List<GraphNode> selectTopByType(@Param("nodeType") String nodeType, @Param("limit") int limit);
}