package com.gameexpert.bootstrap;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.List;
import javax.sql.DataSource;
import org.springframework.beans.factory.InitializingBean;
import org.springframework.boot.sql.init.dependency.DependsOnDatabaseInitialization;
import org.springframework.context.annotation.DependsOn;
import org.springframework.stereotype.Component;

@Component
@DependsOn("entityManagerFactory")
@DependsOnDatabaseInitialization
public final class ChatHistoryIndexRequirement implements InitializingBean {
    private final DataSource dataSource;

    public ChatHistoryIndexRequirement(DataSource dataSource) {
        this.dataSource = dataSource;
    }

    @Override
    public void afterPropertiesSet() throws SQLException {
        String sql = """
                SELECT COLUMN_NAME, IS_VISIBLE, SUB_PART
                FROM information_schema.STATISTICS
                WHERE TABLE_SCHEMA = DATABASE()
                  AND TABLE_NAME = 'chat_messages'
                  AND INDEX_NAME = 'idx_chat_world_created_at'
                ORDER BY SEQ_IN_INDEX
                """;
        try (Connection connection = dataSource.getConnection();
                PreparedStatement statement = connection.prepareStatement(sql);
                ResultSet result = statement.executeQuery()) {
            List<String> columns = new ArrayList<>();
            boolean usable = true;
            while (result.next()) {
                columns.add(result.getString("COLUMN_NAME"));
                usable &= "YES".equals(result.getString("IS_VISIBLE"))
                        && result.getObject("SUB_PART") == null;
            }
            if (!usable || !columns.equals(List.of("world_id", "created_at"))) {
                throw new IllegalStateException("CHAT_HISTORY_INDEX_MISSING: "
                        + "chat_messages 테이블에 idx_chat_world_created_at(world_id, created_at) "
                        + "인덱스가 필요합니다. Lv 2의 @Table 인덱스 설정을 확인하세요.");
            }
        }
    }
}
