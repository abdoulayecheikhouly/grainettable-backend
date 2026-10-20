package sn.grainettable;
import org.springframework.context.annotation.*;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.core.userdetails.*;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.web.csrf.*;
import org.springframework.security.web.context.HttpSessionSecurityContextRepository;
import org.springframework.security.web.context.SecurityContextRepository;
import org.springframework.security.config.http.SessionCreationPolicy;
import java.util.Locale;

@Configuration
public class SecurityConfig {
 @Bean PasswordEncoder encoder(){return new BCryptPasswordEncoder(12);}
 @Bean SecurityContextRepository contexts(){return new HttpSessionSecurityContextRepository();}
 @Bean UserDetailsService users(JdbcTemplate db){return email->{var rows=db.queryForList("SELECT * FROM shop_user WHERE email=?",email.toLowerCase(Locale.ROOT));if(rows.isEmpty())throw new UsernameNotFoundException("Compte inconnu");var u=rows.getFirst();return User.withUsername((String)u.get("email")).password((String)u.get("password_hash")).roles(((String)u.get("role")).toUpperCase(Locale.ROOT)).build();};}
 @Bean SecurityFilterChain security(HttpSecurity http,SecurityContextRepository contexts) throws Exception {
  var csrf=CookieCsrfTokenRepository.withHttpOnlyFalse();csrf.setCookieCustomizer(b->b.sameSite("Strict"));
  http.csrf(c->c.csrfTokenRepository(csrf).csrfTokenRequestHandler(new CsrfTokenRequestAttributeHandler()))
   .securityContext(c->c.securityContextRepository(contexts))
   .sessionManagement(c->c.sessionCreationPolicy(SessionCreationPolicy.IF_REQUIRED))
   .authorizeHttpRequests(a->a.requestMatchers("/api/admin/**").hasRole("ADMIN").requestMatchers("/api/orders/**").authenticated().anyRequest().permitAll())
   .exceptionHandling(e->e.authenticationEntryPoint((req,res,x)->{res.setStatus(401);res.setContentType("application/json");res.getWriter().write("{\"error\":\"Connectez-vous pour continuer\"}");}).accessDeniedHandler((req,res,x)->{res.setStatus(403);res.setContentType("application/json");res.getWriter().write("{\"error\":\"Accès refusé ou session expirée\"}");}))
   .logout(l->l.logoutUrl("/api/auth/logout").invalidateHttpSession(true).deleteCookies("JSESSIONID","XSRF-TOKEN").logoutSuccessHandler((req,res,a)->res.setStatus(204)));
  return http.build();
 }
}
