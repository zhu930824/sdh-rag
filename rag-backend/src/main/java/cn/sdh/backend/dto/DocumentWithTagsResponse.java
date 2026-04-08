package cn.sdh.backend.dto;

import lombok.Data;

import java.time.LocalDateTime;
import java.util.List;

/**
 * 文档响应（包含标签）
 */
@Data
public class DocumentWithTagsResponse {

    private Long id;

    private String title;

    private String content;

    private String fileType;

    private String filePath;

    private Long fileSize;

    private Long categoryId;

    private Long userId;

    private LocalDateTime createTime;

    private LocalDateTime updateTime;

    /**
     * 标签列表
     */
    private List<TagInfo> tags;

    @Data
    public static class TagInfo {
        private Long id;
        private String name;
        private String color;
    }
}
