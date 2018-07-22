package demo.service.impl;

import demo.service.IHelloService;
import springframe.annotation.ZService;

@ZService
public class HelloService implements IHelloService {

    public String syeHello(String name) {
        return "Hello!"+name;
    }

}
