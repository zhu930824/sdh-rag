package cn.sdh.backend.service.impl;

import cn.hutool.core.util.RandomUtil;
import cn.hutool.crypto.digest.BCrypt;
import cn.sdh.backend.common.exception.BusinessException;
import cn.sdh.backend.dto.ChangePasswordRequest;
import cn.sdh.backend.dto.UpdateProfileRequest;
import cn.sdh.backend.dto.UserManageRequest;
import cn.sdh.backend.dto.UserPreferenceRequest;
import cn.sdh.backend.dto.UserStatsResponse;
import cn.sdh.backend.entity.ChatHistory;
import cn.sdh.backend.entity.KnowledgeBase;
import cn.sdh.backend.entity.KnowledgeDocument;
import cn.sdh.backend.entity.PromptTemplate;
import cn.sdh.backend.entity.User;
import cn.sdh.backend.entity.Workflow;
import cn.sdh.backend.mapper.ChatHistoryMapper;
import cn.sdh.backend.mapper.KnowledgeBaseMapper;
import cn.sdh.backend.mapper.KnowledgeDocumentMapper;
import cn.sdh.backend.mapper.PromptTemplateMapper;
import cn.sdh.backend.mapper.UserMapper;
import cn.sdh.backend.mapper.WorkflowMapper;
import cn.sdh.backend.service.UserService;
import cn.sdh.backend.utils.JwtUtil;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.metadata.IPage;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.util.Arrays;

/**
 * 用户服务实现
 */
@Slf4j
@Service
public class UserServiceImpl implements UserService {

    @Autowired
    private UserMapper userMapper;

    @Autowired
    private JwtUtil jwtUtil;

    @Autowired
    private KnowledgeBaseMapper knowledgeBaseMapper;

    @Autowired
    private KnowledgeDocumentMapper knowledgeDocumentMapper;

    @Autowired
    private ChatHistoryMapper chatHistoryMapper;

    @Autowired
    private WorkflowMapper workflowMapper;

    @Autowired
    private PromptTemplateMapper promptTemplateMapper;

    @Override
    public String login(String username, String password) {
        User user = getByUsername(username);
        if (user == null) {
            throw new BusinessException("用户名或密码错误");
        }

        if (!BCrypt.checkpw(password, user.getPassword())) {
            throw new BusinessException("用户名或密码错误");
        }

        if (user.getStatus() != 1) {
            throw new BusinessException("账号已被禁用");
        }

        return jwtUtil.generateToken(user.getId(), user.getUsername());
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public boolean register(String username, String password, String nickname) {
        User existUser = getByUsername(username);
        if (existUser != null) {
            throw new BusinessException("用户名已存在");
        }

        User user = new User();
        user.setUsername(username);
        user.setPassword(BCrypt.hashpw(password));
        user.setNickname(nickname != null ? nickname : username);
        user.setRole("普通用户");
        user.setStatus(1);
        user.setUserLevel(1);
        user.setExperience(0);
        user.setTheme("light");
        user.setLanguage("zh-CN");
        user.setEmailNotification(false);
        user.setSoundNotification(true);
        user.setCreateTime(LocalDateTime.now());
        user.setUpdateTime(LocalDateTime.now());

        return userMapper.insert(user) > 0;
    }

    @Override
    public User getById(Long userId) {
        return userMapper.selectById(userId);
    }

    @Override
    public User getByUsername(String username) {
        LambdaQueryWrapper<User> wrapper = new LambdaQueryWrapper<>();
        wrapper.eq(User::getUsername, username);
        return userMapper.selectOne(wrapper);
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public boolean update(User user) {
        user.setUpdateTime(LocalDateTime.now());
        return userMapper.updateById(user) > 0;
    }

    @Override
    public IPage<User> getPage(Integer page, Integer pageSize, String keyword, Integer status, String role) {
        Page<User> pageParam = new Page<>(page, pageSize);
        return userMapper.selectPageByCondition(pageParam, keyword, status, role);
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public boolean createUser(UserManageRequest request) {
        User existUser = getByUsername(request.getUsername());
        if (existUser != null) {
            throw new BusinessException("用户名已存在");
        }

        User user = new User();
        user.setUsername(request.getUsername());
        user.setPassword(BCrypt.hashpw(request.getPassword()));
        user.setNickname(request.getNickname());
        user.setEmail(request.getEmail());
        user.setRole(request.getRole());
        user.setStatus(request.getStatus());
        user.setUserLevel(1);
        user.setExperience(0);
        user.setCreateTime(LocalDateTime.now());
        user.setUpdateTime(LocalDateTime.now());

        return userMapper.insert(user) > 0;
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public boolean updateUser(Long id, UserManageRequest request) {
        User user = getById(id);
        if (user == null) {
            throw new BusinessException("用户不存在");
        }

        user.setNickname(request.getNickname());
        user.setEmail(request.getEmail());
        user.setRole(request.getRole());
        user.setStatus(request.getStatus());
        user.setUpdateTime(LocalDateTime.now());

        return userMapper.updateById(user) > 0;
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public boolean deleteUser(Long id) {
        return userMapper.deleteById(id) > 0;
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public boolean batchDeleteUsers(Long[] ids) {
        return userMapper.deleteBatchIds(Arrays.asList(ids)) > 0;
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public String resetPassword(Long id) {
        User user = getById(id);
        if (user == null) {
            throw new BusinessException("用户不存在");
        }

        String newPassword = RandomUtil.randomString(8);
        user.setPassword(BCrypt.hashpw(newPassword));
        user.setUpdateTime(LocalDateTime.now());
        userMapper.updateById(user);

        return newPassword;
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public boolean toggleStatus(Long id) {
        User user = getById(id);
        if (user == null) {
            throw new BusinessException("用户不存在");
        }

        user.setStatus(user.getStatus() == 1 ? 0 : 1);
        user.setUpdateTime(LocalDateTime.now());
        return userMapper.updateById(user) > 0;
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public boolean updateProfile(Long userId, UpdateProfileRequest request) {
        User user = getById(userId);
        if (user == null) {
            throw new BusinessException("用户不存在");
        }

        user.setNickname(request.getNickname());
        user.setEmail(request.getEmail());
        user.setSignature(request.getSignature());
        user.setPhone(request.getPhone());
        user.setUpdateTime(LocalDateTime.now());

        return userMapper.updateById(user) > 0;
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public boolean changePassword(Long userId, ChangePasswordRequest request) {
        User user = getById(userId);
        if (user == null) {
            throw new BusinessException("用户不存在");
        }

        if (!BCrypt.checkpw(request.getOldPassword(), user.getPassword())) {
            throw new BusinessException("原密码错误");
        }

        if (!request.getNewPassword().equals(request.getConfirmPassword())) {
            throw new BusinessException("两次输入的密码不一致");
        }

        user.setPassword(BCrypt.hashpw(request.getNewPassword()));
        user.setUpdateTime(LocalDateTime.now());

        return userMapper.updateById(user) > 0;
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public boolean updateAvatar(Long userId, String avatarUrl) {
        User user = getById(userId);
        if (user == null) {
            throw new BusinessException("用户不存在");
        }

        user.setAvatar(avatarUrl);
        user.setUpdateTime(LocalDateTime.now());

        return userMapper.updateById(user) > 0;
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public boolean updatePreference(Long userId, UserPreferenceRequest request) {
        User user = getById(userId);
        if (user == null) {
            throw new BusinessException("用户不存在");
        }

        if (request.getDefaultModelId() != null) {
            user.setDefaultModelId(request.getDefaultModelId());
        }
        if (request.getTheme() != null) {
            user.setTheme(request.getTheme());
        }
        if (request.getLanguage() != null) {
            user.setLanguage(request.getLanguage());
        }
        if (request.getEmailNotification() != null) {
            user.setEmailNotification(request.getEmailNotification());
        }
        if (request.getSoundNotification() != null) {
            user.setSoundNotification(request.getSoundNotification());
        }
        if (request.getReplyLanguage() != null) {
            user.setReplyLanguage(request.getReplyLanguage());
        }
        user.setUpdateTime(LocalDateTime.now());

        return userMapper.updateById(user) > 0;
    }

    @Override
    public UserStatsResponse getUserStats(Long userId) {
        UserStatsResponse stats = new UserStatsResponse();

        // 知识库数量
        LambdaQueryWrapper<KnowledgeBase> kbWrapper = new LambdaQueryWrapper<>();
        kbWrapper.eq(KnowledgeBase::getUserId, userId);
        stats.setKnowledgeCount(knowledgeBaseMapper.selectCount(kbWrapper));

        // 文档数量
        LambdaQueryWrapper<KnowledgeDocument> docWrapper = new LambdaQueryWrapper<>();
        docWrapper.eq(KnowledgeDocument::getUserId, userId);
        stats.setDocumentCount(knowledgeDocumentMapper.selectCount(docWrapper));

        // 对话次数
        LambdaQueryWrapper<ChatHistory> chatWrapper = new LambdaQueryWrapper<>();
        chatWrapper.eq(ChatHistory::getUserId, userId);
        stats.setChatCount(chatHistoryMapper.selectCount(chatWrapper));

        // 今日对话次数
        LocalDateTime todayStart = LocalDateTime.of(LocalDate.now(), LocalTime.MIN);
        LocalDateTime todayEnd = LocalDateTime.of(LocalDate.now(), LocalTime.MAX);
        LambdaQueryWrapper<ChatHistory> todayChatWrapper = new LambdaQueryWrapper<>();
        todayChatWrapper.eq(ChatHistory::getUserId, userId)
                .between(ChatHistory::getCreateTime, todayStart, todayEnd);
        stats.setTodayChatCount(chatHistoryMapper.selectCount(todayChatWrapper));

        // 工作流数量
        LambdaQueryWrapper<Workflow> workflowWrapper = new LambdaQueryWrapper<>();
        workflowWrapper.eq(Workflow::getUserId, userId);
        stats.setWorkflowCount(workflowMapper.selectCount(workflowWrapper));

        // 提示词数量
        LambdaQueryWrapper<PromptTemplate> promptWrapper = new LambdaQueryWrapper<>();
        promptWrapper.eq(PromptTemplate::getUserId, userId);
        stats.setPromptCount(promptTemplateMapper.selectCount(promptWrapper));

        // 获取用户等级和经验值
        User user = getById(userId);
        if (user != null) {
            stats.setUserLevel(user.getUserLevel() != null ? user.getUserLevel() : 1);
            stats.setExperience(user.getExperience() != null ? user.getExperience() : 0);
        }

        return stats;
    }
}
