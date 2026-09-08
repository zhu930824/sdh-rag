package cn.sdh.backend.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.time.LocalDateTime;

/**
 * 演示售后工单(内置演示业务闭环;真实系统替换为业务表)
 */
@Data
@TableName("demo_ticket")
public class DemoTicket {
    @TableId(type = IdType.AUTO)
    private Long id;

    @TableField("ticket_no")
    private String ticketNo;

    private String customer;

    @TableField("order_no")
    private String orderNo;

    private String title;

    private String detail;

    /** OPEN/PROCESSING/RESOLVED/CLOSED */
    private String status;

    @TableField("created_by")
    private Long createdBy;

    @TableField("create_time")
    private LocalDateTime createTime;

    @TableField("update_time")
    private LocalDateTime updateTime;

    public static final String STATUS_OPEN = "OPEN";
    public static final String STATUS_PROCESSING = "PROCESSING";
    public static final String STATUS_RESOLVED = "RESOLVED";
    public static final String STATUS_CLOSED = "CLOSED";
}
