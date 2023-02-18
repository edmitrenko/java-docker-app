package com.example.springdockersimple;

import org.springframework.web.bind.annotation.GetMapping;  
import org.springframework.web.bind.annotation.RestController;  
  
@RestController  
public class HelloDockerController {  
  
    @GetMapping("/hello")  
    public String hello() {  
        return "Hello Docker v0.0.2!";  
    }  
}  