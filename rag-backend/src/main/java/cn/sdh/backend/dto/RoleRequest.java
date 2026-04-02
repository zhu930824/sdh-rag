package cn.sdh.backend.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import lombok.Data;

import java.util.List;

/**
 * 角色管理请求
 */
@Data
public class RoleRequest {

    @NotBlank(message = "角色名称不能为空")
    @Size(min = 2, max = 20, message = "角色名称长度在2到20个字符")
    private String name;

    @NotBlank(message = "角色编码不能为空")
    @Size(min = 2, max = 20, message = "角色编码长度在2到20个字符")
    private String code;

    @Size(max = 200, message = "描述长度不能超过200个字符")
    private String description;

    /**
     * 菜单权限列表
     */
    private List<String> permissions;

    private Integer status = 1;
}
