package cn.sdh.backend.workflow.dto;

import lombok.Data;
import java.util.List;
import java.util.Map;

/**
 * 节点类型定义
 */
@Data
public class NodeTypeDefinition {

    /**
     * 节点类型标识
     */
    private String type;

    /**
     * 节点类型名称
     */
    private String name;

    /**
     * 图标
     */
    private String icon;

    /**
     * 分类: basic, llm, tool, logic
     */
    private String category;

    /**
     * 描述
     */
    private String description;

    /**
     * 默认配置
     */
    private Map<String, Object> defaultConfig;

    /**
     * 输入参数定义
     */
    private List<ParamDefinition> inputs;

    /**
     * 输出参数定义
     */
    private List<ParamDefinition> outputs;

    @Data
    public static class ParamDefinition {
        /**
         * 参数名
         */
        private String name;

        /**
         * 参数类型: string, number, boolean, object, array
         */
        private String type;

        /**
         * 是否必填
         */
        private boolean required;

        /**
         * 描述
         */
        private String description;

        /**
         * 默认值
         */
        private Object defaultValue;
    }
}
