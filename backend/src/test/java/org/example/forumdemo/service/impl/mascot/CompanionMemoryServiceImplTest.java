package org.example.forumdemo.service.impl.mascot;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.conditions.update.LambdaUpdateWrapper;
import com.baomidou.mybatisplus.core.MybatisConfiguration;
import com.baomidou.mybatisplus.core.metadata.TableInfoHelper;
import org.example.forumdemo.common.exception.ApplicationException;
import org.example.forumdemo.entity.db.ForumCompanionMessage;
import org.example.forumdemo.entity.db.ForumCompanionSession;
import org.example.forumdemo.mapper.ForumCompanionMessageMapper;
import org.example.forumdemo.mapper.ForumCompanionSessionMapper;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.apache.ibatis.builder.MapperBuilderAssistant;

import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.isNull;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class CompanionMemoryServiceImplTest {

    @BeforeAll
    static void initializeLambdaMetadata() {
        MybatisConfiguration configuration = new MybatisConfiguration();
        TableInfoHelper.initTableInfo(
                new MapperBuilderAssistant(configuration, "companion-session-test"),
                ForumCompanionSession.class);
        TableInfoHelper.initTableInfo(
                new MapperBuilderAssistant(configuration, "companion-message-test"),
                ForumCompanionMessage.class);
    }

    @Mock
    private ForumCompanionSessionMapper companionSessionMapper;

    @Mock
    private ForumCompanionMessageMapper companionMessageMapper;

    @InjectMocks
    private CompanionMemoryServiceImpl service;

    @Test
    void deleteSessionShouldSoftDeleteOwnedSessionAndMessages() {
        ForumCompanionSession session = new ForumCompanionSession();
        session.setId(9L);
        session.setUserId(3L);
        when(companionSessionMapper.selectOne(any(LambdaQueryWrapper.class))).thenReturn(session);

        service.deleteSession(3L, 9L);

        verify(companionMessageMapper).update(isNull(), any(LambdaUpdateWrapper.class));
        verify(companionSessionMapper).update(isNull(), any(LambdaUpdateWrapper.class));
    }

    @Test
    void deleteSessionShouldRejectMissingOrForeignSession() {
        when(companionSessionMapper.selectOne(any(LambdaQueryWrapper.class))).thenReturn(null);

        assertThrows(ApplicationException.class, () -> service.deleteSession(3L, 9L));

        verify(companionMessageMapper, never()).update(isNull(), any(LambdaUpdateWrapper.class));
        verify(companionSessionMapper, never()).update(isNull(), any(LambdaUpdateWrapper.class));
    }
}
