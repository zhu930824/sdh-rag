package cn.sdh.backend.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.io.Serializable;
import java.time.LocalDateTime;

@Data
@TableName("test_dataset")
public class TestDataset implements Serializable {

    @TableId(type = IdType.AUTO)
    private Long id;

    private String name;

    private String description;

    @TableField("item_count")
    private Integer itemCount;

    @TableField("negative_count")
    private Integer negativeCount;

    /**
     * 数据集类型: builtin-内置/custom-自定义
     */
    @TableField("dataset_type")
    private String datasetType;

    /**
     * 内置数据集文件名
     */
    @TableField("file_name")
    private String fileName;

    @TableField("user_id")
    private Long userId;

    @TableField("create_time")
    private LocalDateTime createTime;

    @TableField("update_time")
    private LocalDateTime updateTime;
}
