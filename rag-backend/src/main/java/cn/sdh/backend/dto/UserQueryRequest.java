package cn.sdh.backend.dto;

import lombok.Data;

/**
 * 用户查询请求
 */
@Data
public class UserQueryRequest {

    private Integer page = 1;
    
    private Integer pageSize = 10;
    
    private String keyword;
    
    private Integer status;
    
    private String role;
}
