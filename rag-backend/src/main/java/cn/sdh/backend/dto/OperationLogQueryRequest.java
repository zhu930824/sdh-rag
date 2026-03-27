package cn.sdh.backend.dto;

import lombok.Data;

@Data
public class OperationLogQueryRequest {

    private Integer page = 1;

    private Integer pageSize = 10;

    private String type;

    private String username;

    private Integer status;

    private String startTime;

    private String endTime;
}