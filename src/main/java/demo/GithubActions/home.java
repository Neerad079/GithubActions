package demo.GithubActions;

import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController

public class home {
    @RequestMapping("/")
    public String homepage(){
        return "Hello and Welcome to the test server , changes";
    }
}
