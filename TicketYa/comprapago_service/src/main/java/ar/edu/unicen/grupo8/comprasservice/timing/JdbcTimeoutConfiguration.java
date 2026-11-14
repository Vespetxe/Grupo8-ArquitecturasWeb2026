package ar.edu.unicen.grupo8.comprasservice.timing;
/** Limits only acquisition, connect and lock waits. Never cancels a financial commit. */
@org.springframework.context.annotation.Configuration(proxyBeanMethods=false)
public class JdbcTimeoutConfiguration {
 public static void apply(com.zaxxer.hikari.HikariConfig pool,org.springframework.core.env.Environment env){
  long acquire=env.getProperty("ticketya.jdbc.acquire-ms",Long.class,2000L);
  int max=env.getProperty("ticketya.jdbc.pool-size",Integer.class,10);
  int lock=env.getProperty("ticketya.jdbc.lock-seconds",Integer.class,3);
  int connect=env.getProperty("ticketya.jdbc.connect-ms",Integer.class,1000);
  if(acquire<250||acquire>10000||max<1||max>50||lock<1||lock>30||connect<1||connect>5000)throw new IllegalStateException("Invalid JDBC time limits");
  pool.setConnectionTimeout(acquire);pool.setValidationTimeout(Math.min(1000,acquire));pool.setMaximumPoolSize(max);pool.setMinimumIdle(Math.min(2,max));
  if(pool.getJdbcUrl()!=null&&pool.getJdbcUrl().startsWith("jdbc:mysql:")){
   pool.addDataSourceProperty("connectTimeout",String.valueOf(connect));
   pool.setConnectionInitSql("SET SESSION innodb_lock_wait_timeout="+lock);
  }
 }
 @org.springframework.context.annotation.Bean static org.springframework.beans.factory.config.BeanPostProcessor jdbcTimeouts(org.springframework.core.env.Environment env){
  return new org.springframework.beans.factory.config.BeanPostProcessor(){
   @Override public Object postProcessBeforeInitialization(Object bean,String name){if(bean instanceof com.zaxxer.hikari.HikariDataSource pool)apply(pool,env);return bean;}
  };
 }
}
