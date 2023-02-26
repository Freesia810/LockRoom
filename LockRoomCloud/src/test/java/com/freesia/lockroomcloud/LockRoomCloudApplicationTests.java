package com.freesia.lockroomcloud;

import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.ApplicationContext;
import org.springframework.context.annotation.AnnotationConfigApplicationContext;
import org.springframework.context.support.ClassPathXmlApplicationContext;
import org.springframework.jdbc.core.JdbcTemplate;

import javax.sql.DataSource;
import java.sql.SQLException;

@SpringBootTest
class LockRoomCloudApplicationTests {

    //测试是否连接数据库
    @Test
    public void testIsConnect() {
        ApplicationContext ac = new AnnotationConfigApplicationContext(AppConfig.class);
        DataSource d = (DataSource) ac.getBean("dataSource");
        JdbcTemplate jdbcTemplate = new JdbcTemplate();
        jdbcTemplate.setDataSource(d);
        jdbcTemplate.update("INSERT INTO shareinfo VALUES(replace(uuid(),'-',''),'test',12,1,'a',1,'abc');");
    }

}
