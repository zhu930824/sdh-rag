package cn.sdh.backend.service;

import cn.sdh.backend.common.context.UserContext;
import cn.sdh.backend.common.exception.BusinessException;
import cn.sdh.backend.entity.User;
import cn.sdh.backend.mapper.UserMapper;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class PlatformAdminServiceTest {

    @Mock
    private UserMapper users;

    @AfterEach
    void clearContext() {
        UserContext.clear();
    }

    @Test
    void allowsActiveAdminFromCurrentDatabaseState() {
        setActor(7L);
        User admin = user(7L, "admin", 1);
        when(users.selectById(7L)).thenReturn(admin);

        assertSame(admin, new PlatformAdminService(users).requireAdmin());
    }

    @Test
    void rejectsNonAdmin() {
        setActor(8L);
        when(users.selectById(8L)).thenReturn(user(8L, "user", 1));

        BusinessException error = assertThrows(BusinessException.class,
                () -> new PlatformAdminService(users).requireAdmin());

        assertEquals(403, error.getCode());
    }

    @Test
    void rejectsDisabledAdmin() {
        setActor(9L);
        when(users.selectById(9L)).thenReturn(user(9L, "admin", 0));

        BusinessException error = assertThrows(BusinessException.class,
                () -> new PlatformAdminService(users).requireAdmin());

        assertEquals(401, error.getCode());
    }

    private void setActor(Long id) {
        UserContext context = new UserContext();
        context.setUserId(id);
        context.setUsername("tester");
        UserContext.setUserContext(context);
    }

    private User user(Long id, String role, int status) {
        User user = new User();
        user.setId(id);
        user.setRole(role);
        user.setStatus(status);
        return user;
    }
}
