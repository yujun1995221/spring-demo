package com.zhouyu.service;

import com.spring.Component;
import com.spring.InitializingBean;

/**
 * @author 周瑜
 */
@Component
public class UserService1 implements InitializingBean {

    public void test() {
        System.out.println(0);
    }

    @Override
    public void afterPropertiesSet() {
        System.out.println("初始化");
    }
}

