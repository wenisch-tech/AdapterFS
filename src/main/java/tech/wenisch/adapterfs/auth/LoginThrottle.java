package tech.wenisch.adapterfs.auth;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.time.Duration;
import java.time.Instant;
import java.util.concurrent.ConcurrentHashMap;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

@Component
public class LoginThrottle extends OncePerRequestFilter {
    private final ConcurrentHashMap<String,Attempt> attempts=new ConcurrentHashMap<>();
    void failed(String address){attempts.compute(address,(key,old)->old==null||old.started().isBefore(Instant.now().minus(Duration.ofMinutes(10)))?new Attempt(1,Instant.now()):new Attempt(old.count()+1,old.started()));}
    void succeeded(String address){attempts.remove(address);}
    @Override protected void doFilterInternal(HttpServletRequest request,HttpServletResponse response,FilterChain chain)throws ServletException,IOException{
        Attempt attempt=attempts.get(request.getRemoteAddr());
        if(request.getMethod().equals("POST")&&request.getServletPath().equals("/login")&&attempt!=null&&attempt.count()>=8&&attempt.started().isAfter(Instant.now().minus(Duration.ofMinutes(10)))){
            response.setStatus(429);response.setContentType("text/plain");response.getWriter().write("Too many failed sign-in attempts. Try again later.");return;
        }
        chain.doFilter(request,response);
    }
    private record Attempt(int count,Instant started){}
}
