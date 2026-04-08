package cn.sdh.backend.dto;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import lombok.Data;

/**
 * 更新个人信息请求
 */
@Data
public class UpdateProfileRequest {

    @NotBlank(message = "昵称不能为空")
    @Size(max = 50, message = "昵称最多50个字符")
    private String nickname;

    @Email(message = "邮箱格式不正确")
    private String email;

    private String signature;

    private String phone;
}
