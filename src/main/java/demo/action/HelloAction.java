package demo.action;

import demo.service.IHelloService;
import springframe.annotation.ZAutowired;
import springframe.annotation.ZController;
import springframe.annotation.ZRequestMapping;
import springframe.annotation.ZRequestParam;

import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import java.io.IOException;

@ZController
@ZRequestMapping("/test")
public class HelloAction {

    @ZAutowired
    IHelloService helloService;

    @ZRequestMapping("/hello")
    public String sayHello(
            HttpServletResponse response,
            HttpServletRequest request,
            @ZRequestParam("name") String name){
        try {
            response.getWriter().write(helloService.syeHello(name));
        } catch (IOException e) {
            e.printStackTrace();
        }
        return "";
    }

}
