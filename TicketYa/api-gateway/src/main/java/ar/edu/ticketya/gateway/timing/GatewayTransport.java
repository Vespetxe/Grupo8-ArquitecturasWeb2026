package ar.edu.ticketya.gateway.timing;
import org.springframework.context.annotation.*;
import org.springframework.cloud.gateway.config.*;
import org.springframework.boot.web.server.autoconfigure.ServerProperties;
import org.springframework.core.env.Environment;
import reactor.netty.resources.ConnectionProvider;
import java.time.Duration;
/** Keep SCG TLS/observations/customizers, replace only unbounded pool defaults. */
@Configuration(proxyBeanMethods=false)
public class GatewayTransport {
 @Bean HttpClientCustomizer noAutomaticReplay(){return client->client.disableRetry(true);}
 @Bean HttpClientFactory boundedGatewayClient(HttpClientProperties properties,ServerProperties server,HttpClientSslConfigurer ssl,java.util.List<HttpClientCustomizer> customizers,Environment env){
  int max=env.getProperty("ticketya.gateway.pool-size",Integer.class,50),pending=env.getProperty("ticketya.gateway.pending-max",Integer.class,100);
  long acquire=env.getProperty("ticketya.gateway.acquire-ms",Long.class,500L);
  if(max<1||max>500||pending<1||pending>1000||acquire<1||acquire>2000)throw new IllegalStateException("Invalid Gateway pool limits");
  return new HttpClientFactory(properties,server,ssl,customizers){
   @Override protected ConnectionProvider buildConnectionProvider(HttpClientProperties ignored){return ConnectionProvider.builder("ticketya-bounded")
    .maxConnections(max).pendingAcquireMaxCount(pending).pendingAcquireTimeout(Duration.ofMillis(acquire)).build();}
  };
 }
}
