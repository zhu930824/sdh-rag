package cn.sdh.backend.dto;

import lombok.Data;

/**
 * 内置数据集信息
 */
@Data
public class DatasetInfo {

    private String name;

    private String description;

    private Integer itemCount;

    private Integer negativeCount;

    private String fileName;
}
