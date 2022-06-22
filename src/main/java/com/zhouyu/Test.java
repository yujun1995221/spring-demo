package com.zhouyu;


import com.spring.ZhouyuApplicationContext;

/**
 * @author 周瑜
 */
public class Test {

    public static void main(String[] args) {

        // 扫描--->创建单例Bean BeanDefinition BeanPostPRocess
        ZhouyuApplicationContext applicationContext = new ZhouyuApplicationContext(AppConfig.class);
        System.out.println(applicationContext.getBean("userService1"));
        System.out.println(applicationContext.getBean("userService1"));
        System.out.println(applicationContext.getBean("userService1"));
      /*  UserInterface userService = (UserInterface) applicationContext.getBean("userService");
        userService.test();*/
    }
}
