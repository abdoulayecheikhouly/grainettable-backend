package sn.grainettable;
import org.springframework.web.bind.annotation.*;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.authority.AuthorityUtils;
import org.springframework.security.web.context.SecurityContextRepository;
import org.springframework.security.web.csrf.CsrfToken;
import jakarta.servlet.http.*;
import jakarta.validation.Valid;
import java.util.*;

@RestController @RequestMapping("/api")
public class AuthController {
 final JdbcTemplate db;final PasswordEncoder passwords;final SecurityContextRepository contexts;
 public AuthController(JdbcTemplate db,PasswordEncoder passwords,SecurityContextRepository contexts){this.db=db;this.passwords=passwords;this.contexts=contexts;}
 Map<String,Object> user(String email){return db.queryForMap("SELECT id,name,email,phone,role FROM shop_user WHERE email=?",email);}
 @GetMapping("/session") Map<String,Object> session(Authentication auth,CsrfToken csrf){var result=new HashMap<String,Object>();result.put("user",auth==null?null:user(auth.getName()));result.put("csrf_token",csrf.getToken());return result;}
 void login(String email,String role,HttpServletRequest req,HttpServletResponse res){if(req.getSession(false)!=null)req.changeSessionId();var context=SecurityContextHolder.createEmptyContext();context.setAuthentication(new UsernamePasswordAuthenticationToken(email,null,AuthorityUtils.createAuthorityList("ROLE_"+role.toUpperCase(Locale.ROOT))));SecurityContextHolder.setContext(context);contexts.saveContext(context,req,res);}
 @PostMapping("/auth/register") Map<String,Object> register(@Valid @RequestBody Requests.Signup p,HttpServletRequest req,HttpServletResponse res){String email=p.email().trim().toLowerCase(Locale.ROOT);db.update("INSERT INTO shop_user(name,email,phone,password_hash) VALUES(?,?,?,?)",p.name().trim(),email,p.phone()==null?"":p.phone(),passwords.encode(p.password()));login(email,"customer",req,res);res.setStatus(201);return Map.of("user",user(email));}
 @PostMapping("/auth/login") Map<String,Object> signin(@Valid @RequestBody Requests.Login p,HttpServletRequest req,HttpServletResponse res){String email=p.email().toLowerCase(Locale.ROOT);var rows=db.queryForList("SELECT * FROM shop_user WHERE email=?",email);String hash=rows.isEmpty()?"$2a$12$H1PVK0K4p.bS5CaTW/y3JOGUCXnAvSyv5uHQ5LdE9HMJ3zCNtTLAa":(String)rows.getFirst().get("password_hash");boolean ok=passwords.matches(p.password(),hash);if(rows.isEmpty()||!ok)throw new ApiException(401,"Adresse e-mail ou mot de passe incorrect");login(email,(String)rows.getFirst().get("role"),req,res);return Map.of("user",user(email));}
}
