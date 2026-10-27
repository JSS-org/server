package cn.iocoder.yudao.server.integration;

import org.springframework.context.ApplicationContextInitializer;
import org.springframework.context.ConfigurableApplicationContext;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;

import java.sql.Connection;
import java.sql.DriverManager;
import java.util.LinkedHashSet;
import java.util.Set;

/** Controlled identities only in the wrapper-owned schema, before any application cache starts. */
public class JssHttpFixtures
        implements ApplicationContextInitializer<ConfigurableApplicationContext> {
    @Override
    public void initialize(ConfigurableApplicationContext context) {
        String schema = System.getenv("JSS_TEST_HTTP_SCHEMA");
        String url = System.getenv("JSS_TEST_MYSQL_URL");
        if (schema == null
                || !schema.matches("jss_http_verify_[0-9a-f]{12}")
                || url == null
                || !url.startsWith("jdbc:mysql://127.0.0.1:13316/" + schema + "?")) {
            throw new IllegalStateException("HTTP fixtures require the owned MySQL URL and schema");
        }
        for (String domain : new String[] {"master", "slave"}) {
            if (!url.equals(
                    context.getEnvironment()
                            .getProperty(
                                    "spring.datasource.dynamic.datasource." + domain + ".url"))) {
                throw new IllegalStateException(
                        "Refusing a datasource outside the owned HTTP schema");
            }
        }
        String hash = new BCryptPasswordEncoder().encode(System.getenv("JSS_TEST_HTTP_PASSWORD"));
        try (var connection =
                DriverManager.getConnection(
                        url,
                        System.getenv("JSS_TEST_MYSQL_USER"),
                        System.getenv("JSS_TEST_MYSQL_PASSWORD"))) {
            try (var statement = connection.createStatement()) {
                // Retain only local database file storage and disable exported provider/job
                // settings.
                statement.executeUpdate("DELETE FROM infra_data_source_config");
                statement.executeUpdate("DELETE FROM infra_job");
                statement.executeUpdate("UPDATE system_sms_channel SET status=1");
                statement.executeUpdate("UPDATE system_sms_template SET status=1");
                statement.executeUpdate("UPDATE system_social_client SET status=1");
                statement.executeUpdate("DELETE FROM infra_file_config WHERE id<>4");
                statement.executeUpdate("UPDATE infra_file_config SET master=b'1' WHERE id=4");
                try (var rows =
                        statement.executeQuery(
                                "SELECT storage FROM infra_file_config WHERE id=4")) {
                    if (!rows.next() || rows.getInt(1) != 1)
                        throw new IllegalStateException(
                                "HTTP fixture requires database file storage");
                }
                statement.executeUpdate(
                        "UPDATE system_tenant SET status=0,package_id=0,expire_time='2035-01-01',account_count=10000 WHERE id=1");
                statement.executeUpdate(
                        "UPDATE system_role SET code='super_admin',status=0,data_scope=1 WHERE id=1");
                String seed =
                        """
                        INSERT INTO system_tenant_package (id,name,status,menu_ids) VALUES (91001,'JSS HTTP tenant',0,'[]');
                        INSERT INTO system_tenant (id,name,contact_name,status,websites,package_id,expire_time,account_count) VALUES (91001,'JSS HTTP foreign','fixture',0,'[]',91001,'2035-01-01',1000);
                        INSERT INTO member_group (id,tenant_id,name,status) VALUES (23099,1,'员工',0);
                        INSERT INTO system_role (id,name,code,sort,data_scope,status,type,tenant_id) VALUES (23001,'HTTP staff','jss_http_staff',1,1,0,2,1),(23002,'HTTP entry only','jss_http_entry',2,1,0,2,1),(23004,'HTTP mutable role','jss_http_mutable',4,1,0,2,1),(33001,'HTTP tenant reviewer','jss_http_foreign',1,1,0,2,91001);
                        INSERT INTO system_role_menu (role_id,menu_id,tenant_id) SELECT 23001,id,1 FROM system_menu WHERE permission IN ('jss:workspace:access','system:user:query') AND deleted=b'0';
                        INSERT INTO system_role_menu (role_id,menu_id,tenant_id) SELECT 23002,id,1 FROM system_menu WHERE permission='jss:workspace:access' AND deleted=b'0';
                        INSERT INTO system_role_menu (role_id,menu_id,tenant_id) SELECT 23004,id,1 FROM system_menu WHERE permission IN ('jss:workspace:access','system:user:query') AND deleted=b'0';
                        INSERT INTO system_user_role (user_id,role_id,tenant_id) VALUES (23001,23001,1),(23002,23002,1),(23004,23004,1),(23005,23001,1),(23007,23001,1),(23008,23001,1);
                        INSERT INTO system_user_role (user_id,role_id,tenant_id) VALUES (33001,33001,91001);
                        INSERT INTO system_role (id,name,code,sort,data_scope,status,type,tenant_id) VALUES (23011,'HTTP websocket','jss_http_websocket',11,1,0,2,1);
                        INSERT INTO system_user_role (user_id,role_id,tenant_id) VALUES (23011,23011,1);
                        INSERT INTO system_role_menu (role_id,menu_id,tenant_id) SELECT 23011,id,1 FROM system_menu WHERE permission='jss:workspace:access' AND deleted=b'0';
                        INSERT INTO system_role (id,name,code,sort,data_scope,status,type,tenant_id) VALUES (23009,'HTTP multi entry','jss_http_multi_entry',9,1,0,2,1),(23010,'HTTP multi full','jss_http_multi_full',10,1,1,2,1);
                        INSERT INTO system_role_menu (role_id,menu_id,tenant_id) SELECT 23009,id,1 FROM system_menu WHERE permission='jss:workspace:access' AND deleted=b'0';
                        INSERT INTO system_role_menu (role_id,menu_id,tenant_id) SELECT 23010,id,1 FROM system_menu WHERE permission IN ('jss:workspace:access','system:user:query') AND deleted=b'0';
                        INSERT INTO system_user_role (user_id,role_id,tenant_id) VALUES (23009,23009,1),(23009,23010,1);
                        """;
                for (String sql : seed.split(";")) if (!sql.isBlank()) statement.executeUpdate(sql);
            }
            // Normal role configuration includes each granted button's menu ancestors. The
            // permission-info endpoint intentionally filters orphaned or disabled branches.
            long queryMenu;
            try (var statement = connection.createStatement();
                    var rows =
                            statement.executeQuery(
                                    "SELECT id FROM system_menu WHERE permission='system:user:query' AND deleted=b'0'")) {
                if (!rows.next())
                    throw new IllegalStateException("User query permission fixture is missing");
                queryMenu = rows.getLong(1);
            }
            var ancestors = menuBranch(connection, queryMenu);
            ancestors.remove(queryMenu);
            for (long role : new long[] {23001, 23004, 23010}) {
                for (long menu : ancestors) {
                    try (var statement =
                            connection.prepareStatement(
                                    "INSERT INTO system_role_menu (role_id,menu_id,tenant_id) VALUES (?,?,1)")) {
                        statement.setLong(1, role);
                        statement.setLong(2, menu);
                        statement.executeUpdate();
                    }
                }
            }
            var foreignMenus = menuBranch(connection, queryMenu);
            try (var statement = connection.createStatement();
                    var rows =
                            statement.executeQuery(
                                    "SELECT id FROM system_menu WHERE permission IN ('jss:workspace:access','jss:identity:bind','jss:identity:query') AND deleted=b'0'")) {
                while (rows.next()) foreignMenus.add(rows.getLong(1));
            }
            for (long menu : foreignMenus) {
                try (var statement =
                        connection.prepareStatement(
                                "INSERT INTO system_role_menu (role_id,menu_id,tenant_id) VALUES (33001,?,91001)")) {
                    statement.setLong(1, menu);
                    statement.executeUpdate();
                }
            }
            try (var statement =
                    connection.prepareStatement(
                            "UPDATE system_tenant_package SET menu_ids=? WHERE id=91001")) {
                statement.setString(
                        1,
                        "["
                                + String.join(
                                        ",", foreignMenus.stream().map(String::valueOf).toList())
                                + "]");
                statement.executeUpdate();
            }
            try (var statement =
                    connection.prepareStatement(
                            "UPDATE system_users SET username='jssitreviewer',nickname='HTTP reviewer',password=?,status=0,tenant_id=1 WHERE id=1")) {
                statement.setString(1, hash);
                if (statement.executeUpdate() != 1)
                    throw new IllegalStateException("Framework reviewer fixture is missing");
            }
            String[] adminSeeds = {
                "INSERT INTO system_users (id,username,nickname,password,status,tenant_id,mobile) VALUES (23001,'jssitstaff','HTTP staff',?,0,1,'13800009001')",
                "INSERT INTO system_users (id,username,nickname,password,status,tenant_id,mobile) VALUES (23002,'jssitentry','HTTP entry',?,0,1,'13800009002')",
                "INSERT INTO system_users (id,username,nickname,password,status,tenant_id,mobile) VALUES (23004,'jssitmutable','HTTP mutable',?,0,1,'13800009004')",
                "INSERT INTO system_users (id,username,nickname,password,status,tenant_id,mobile) VALUES (23005,'jssitstatus','HTTP status',?,0,1,'13800009005')",
                "INSERT INTO system_users (id,username,nickname,password,status,tenant_id,mobile) VALUES (23007,'jssitbrowser','HTTP browser employee',?,0,1,'13800009007')",
                "INSERT INTO system_users (id,username,nickname,password,status,tenant_id,mobile) VALUES (23008,'jssitmenu','HTTP menu employee',?,0,1,'13800009008')",
                "INSERT INTO system_users (id,username,nickname,password,status,tenant_id,mobile) VALUES (23009,'jssitmulti','HTTP multi employee',?,0,1,'13800009009')",
                "INSERT INTO system_users (id,username,nickname,password,status,tenant_id,mobile) VALUES (23011,'jssitwebsocket','HTTP websocket',?,0,1,'13800009011')",
                "INSERT INTO system_users (id,username,nickname,password,status,tenant_id,mobile) VALUES (33001,'jssitforeign','HTTP foreign',?,0,91001,'13800009001')"
            };
            for (String sql : adminSeeds) {
                try (var statement = connection.prepareStatement(sql)) {
                    statement.setString(1, hash);
                    statement.executeUpdate();
                }
            }
            for (int id :
                    new int[] {
                        23001, 23002, 23003, 23004, 23005, 23006, 23007, 23008, 23009, 23010, 23011
                    }) {
                try (var statement =
                        connection.prepareStatement(
                                "INSERT INTO member_user (id,tenant_id,mobile,password,nickname,status,register_ip,group_id) VALUES (?,1,?,?,?,0,'127.0.0.1',23099)")) {
                    statement.setInt(1, id);
                    statement.setString(2, "13800009%03d".formatted(id - 23000));
                    statement.setString(3, hash);
                    statement.setString(4, "HTTP member " + id);
                    statement.executeUpdate();
                }
            }
            // Dedicated browser targets exercise Long IDs without changing login identities.
            for (long id : new long[] {9007199254740993L, 9007199254740995L}) {
                try (var statement =
                        connection.prepareStatement(
                                "INSERT INTO member_user (id,tenant_id,mobile,password,nickname,status,register_ip,group_id,email,name,sex,area_id,birthday,mark) VALUES (?,1,?,?,?,0,'127.0.0.1',23099,?,'Before',0,110101,'2000-01-02 00:00:00','Before')")) {
                    statement.setLong(1, id);
                    statement.setString(2, id == 9007199254740993L ? "13800009991" : "13800009992");
                    statement.setString(3, hash);
                    statement.setString(4, "HTTP Long " + id);
                    statement.setString(5, "before-" + id + "@example.com");
                    statement.executeUpdate();
                }
            }
            try (var statement =
                    connection.prepareStatement(
                            "INSERT INTO member_user (id,tenant_id,mobile,password,nickname,status,register_ip) VALUES (33001,91001,'13800009101',?,'HTTP tenant member',0,'127.0.0.1')")) {
                statement.setString(1, hash);
                statement.executeUpdate();
            }
        } catch (Exception exception) {
            throw new IllegalStateException("Could not prepare the owned HTTP fixtures", exception);
        }
    }

    static Set<Long> menuBranch(Connection connection, long menu) throws Exception {
        Set<Long> branch = new LinkedHashSet<>();
        while (menu != 0) {
            if (!branch.add(menu)) throw new IllegalStateException("Cyclic menu fixture");
            try (var statement =
                    connection.prepareStatement(
                            "SELECT parent_id,status FROM system_menu WHERE id=? AND deleted=b'0'")) {
                statement.setLong(1, menu);
                try (var rows = statement.executeQuery()) {
                    if (!rows.next() || rows.getInt(2) != 0)
                        throw new IllegalStateException(
                                "Menu fixture has a missing or disabled ancestor");
                    menu = rows.getLong(1);
                }
            }
        }
        return branch;
    }
}
