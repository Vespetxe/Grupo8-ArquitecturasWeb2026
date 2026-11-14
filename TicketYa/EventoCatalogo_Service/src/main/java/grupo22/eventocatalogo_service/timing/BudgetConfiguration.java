package grupo22.eventocatalogo_service.timing;
@org.springframework.context.annotation.Configuration(proxyBeanMethods=false)
public class BudgetConfiguration {
 @org.springframework.context.annotation.Bean org.springframework.boot.web.servlet.FilterRegistrationBean<org.springframework.web.filter.OncePerRequestFilter> requestBudgetFilter(org.springframework.core.env.Environment env){
  long millis=env.getProperty("ticketya.time.budget-ms",Long.class,12000L);if(millis<100||millis>120000)throw new IllegalStateException("Invalid request budget");
  var filter=new org.springframework.web.filter.OncePerRequestFilter(){
   @Override protected void doFilterInternal(jakarta.servlet.http.HttpServletRequest request,jakarta.servlet.http.HttpServletResponse response,jakarta.servlet.FilterChain chain)throws java.io.IOException,jakarta.servlet.ServletException{
    RequestBudget.start(request,millis);chain.doFilter(request,response);
   }
  };
  var registration=new org.springframework.boot.web.servlet.FilterRegistrationBean<org.springframework.web.filter.OncePerRequestFilter>(filter);
  registration.setOrder(org.springframework.core.Ordered.HIGHEST_PRECEDENCE+1);registration.setAsyncSupported(true);
  return registration;
 }
}
