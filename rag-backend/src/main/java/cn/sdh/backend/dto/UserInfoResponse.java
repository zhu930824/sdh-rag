package cn.sdh.backend.dto;

import lombok.Data;

import java.time.LocalDateTime;
import java.util.List;

/**
 * 用户信息响应
 */
@Data
public class UserInfoResponse {

    private Long id;
    private String username;
    private String nickname;
    private String avatar;
    private String role;
    private Integer status;
    private LocalDateTime createTime;

    /**
     * 用户权限列表（菜单路径）
     */
    private List<String> permissions;
}
