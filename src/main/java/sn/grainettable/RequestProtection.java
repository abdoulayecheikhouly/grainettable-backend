package sn.grainettable;
import org.springframework.stereotype.Component;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.web.filter.OncePerRequestFilter;
import jakarta.servlet.*;
import jakarta.servlet.http.*;
import java.io.IOException;
import java.util.concurrent.ConcurrentHashMap;
@Component
public class RequestProtection extends OncePerRequestFilter {
 @Value("${shop.origin}") String origin;
 record Window(long start,int count){}
 final ConcurrentHashMap<String,Window> attempts=new ConcurrentHashMap<>();
 @Override protected void doFilterInternal(HttpServletRequest req,HttpServletResponse res,FilterChain chain)throws ServletException,IOException {
  if(req.getRequestURI().startsWith("/api/")&&!java.util.Set.of("GET","HEAD","OPTIONS").contains(req.getMethod())&&!origin.equals(req.getHeader("Origin"))){res.setStatus(403);res.setContentType("application/json");res.getWriter().write("{\"error\":\"Origine refusée\"}");return;}
  if(req.getRequestURI().matches("/api/auth/(login|register)")&&req.getMethod().equals("POST")){long now=System.currentTimeMillis();attempts.entrySet().removeIf(e->now-e.getValue().start()>900000);var window=attempts.compute(req.getRemoteAddr(),(k,w)->w==null||now-w.start()>900000?new Window(now,1):new Window(w.start(),w.count()+1));if(window.count()>20){res.setStatus(429);res.setContentType("application/json");res.getWriter().write("{\"error\":\"Trop de tentatives, réessayez plus tard\"}");return;}}
  chain.doFilter(req,res);
 }
}
