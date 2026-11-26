package ar.edu.unicen.grupo8.comprasservice.feign;
/** Explicit per-destination child configuration; deliberately outside component scanning annotations. */
public class TargetFeignConfiguration {
 @org.springframework.context.annotation.Bean public SafeFeignCapability safeFailures(){return new SafeFeignCapability();}
 private final String target;protected TargetFeignConfiguration(String target){this.target=target;}
 @org.springframework.context.annotation.Bean public feign.Client transport(ar.edu.unicen.grupo8.comprasservice.security.ServiceTokens tokens){return tokens.transport(target);}
 @org.springframework.context.annotation.Bean public feign.Retryer retryer(){return feign.Retryer.NEVER_RETRY;}
 @org.springframework.context.annotation.Bean public feign.codec.Encoder encoder(){return FeignSupport.encoder();}
 @org.springframework.context.annotation.Bean public feign.codec.Decoder decoder(){return FeignSupport.decoder();}
 @org.springframework.context.annotation.Bean public feign.codec.ErrorDecoder errors(){return FeignSupport.errors();}
 @org.springframework.context.annotation.Bean public feign.Logger.Level logLevel(){return feign.Logger.Level.NONE;}
 public static class Inventario extends TargetFeignConfiguration{public Inventario(){super("inventario");}}
 public static class Entradas extends TargetFeignConfiguration{public Entradas(){super("entradas");}}
 public static class Compras extends TargetFeignConfiguration{public Compras(){super("compras");}}
 public static class Eventos extends TargetFeignConfiguration{public Eventos(){super("eventos");}}
}
