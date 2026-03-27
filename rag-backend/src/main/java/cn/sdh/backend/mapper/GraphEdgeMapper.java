package cn.sdh.backend.mapper;

import cn.sdh.backend.entity.GraphEdge;
import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;

import java.util.List;

@Mapper
public interface GraphEdgeMapper extends BaseMapper<GraphEdge> {

    @Select("SELECT * FROM graph_edge WHERE source_id = #{nodeId} OR target_id = #{nodeId}")
    List<GraphEdge> selectByNodeId(@Param("nodeId") Long nodeId);

    @Select("SELECT * FROM graph_edge WHERE source_id IN (${nodeIds}) OR target_id IN (${nodeIds})")
    List<GraphEdge> selectByNodeIds(@Param("nodeIds") String nodeIds);
}