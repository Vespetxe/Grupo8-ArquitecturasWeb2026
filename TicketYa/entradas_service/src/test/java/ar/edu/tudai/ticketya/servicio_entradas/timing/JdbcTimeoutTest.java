package ar.edu.tudai.ticketya.servicio_entradas.timing;
import org.junit.jupiter.api.Test;
import org.springframework.mock.env.MockEnvironment;
import static org.junit.jupiter.api.Assertions.*;
class JdbcTimeoutTest {
 @Test void mysqlConnectAndLockSettingsAreAppliedWithoutOpeningADatabase(){
  var config=new com.zaxxer.hikari.HikariConfig();config.setJdbcUrl("jdbc:mysql://isolated.invalid/never_connected");
  JdbcTimeoutConfiguration.apply(config,new MockEnvironment().withProperty("ticketya.jdbc.acquire-ms","750").withProperty("ticketya.jdbc.lock-seconds","2"));
  assertEquals(750,config.getConnectionTimeout());assertEquals("1000",config.getDataSourceProperties().getProperty("connectTimeout"));
  assertEquals("SET SESSION innodb_lock_wait_timeout=2",config.getConnectionInitSql());assertNull(config.getDataSourceProperties().getProperty("socketTimeout"));
 }
 @Test void hikariAcquisitionReallyExpiresAndDoesNotAlterSchema()throws Exception{
  var config=new com.zaxxer.hikari.HikariConfig();config.setJdbcUrl("jdbc:h2:mem:timing-"+java.util.UUID.randomUUID());
  JdbcTimeoutConfiguration.apply(config,new MockEnvironment().withProperty("ticketya.jdbc.acquire-ms","300").withProperty("ticketya.jdbc.pool-size","1"));
  assertNull(config.getConnectionInitSql());try(var pool=new com.zaxxer.hikari.HikariDataSource(config);var held=pool.getConnection()){
   long start=System.nanoTime();assertThrows(java.sql.SQLTransientConnectionException.class,pool::getConnection);
   long elapsed=(System.nanoTime()-start)/1_000_000;assertTrue(elapsed>=200&&elapsed<3000);assertEquals(1,pool.getMaximumPoolSize());
   assertTrue(held.isValid(1));
  }
 }
 @Test void invalidLimitsFailClosed(){var config=new com.zaxxer.hikari.HikariConfig();assertThrows(IllegalStateException.class,()->JdbcTimeoutConfiguration.apply(config,new MockEnvironment().withProperty("ticketya.jdbc.acquire-ms","0")));}
}
