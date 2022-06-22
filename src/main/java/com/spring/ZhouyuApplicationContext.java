package com.spring;

import java.beans.Introspector;
import java.io.File;
import java.lang.reflect.Field;
import java.lang.reflect.InvocationTargetException;
import java.net.URL;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * @author 周瑜
 */
public class ZhouyuApplicationContext {

    private Class configClass;
    private Map<String, BeanDefinition> beanDefinitionMap = new HashMap<>();
    private Map<String, Object> singletonObjects = new HashMap<>();
    private List<BeanPostProcessor> beanPostProcessorList = new ArrayList<>();

    public ZhouyuApplicationContext(Class configClass) {
        this.configClass = configClass;

        // 扫描
        scan(configClass);

        //扫描之后创建单例Bean
        for (Map.Entry<String, BeanDefinition> entry : beanDefinitionMap.entrySet()) {
            String beanName = entry.getKey();
            BeanDefinition beanDefinition = entry.getValue();
            if (beanDefinition.getScope().equals("singleton")) {
                //创建Bean
                Object bean = createBean(beanName, beanDefinition);
                singletonObjects.put(beanName, bean);
            }
        }

    }

    /**
     * 创建Bean
     *
     * @param beanName
     * @param beanDefinition
     * @return 对象
     */
    private Object createBean(String beanName, BeanDefinition beanDefinition) {
        //根据类型获取Class对象
        Class clazz = beanDefinition.getType();

        Object instance = null;
        try {
            //获得无参构造方法实例化对象
            instance = clazz.getConstructor().newInstance();
            //遍历类所有属性
            for (Field field : clazz.getDeclaredFields()) {
                if (field.isAnnotationPresent(Autowired.class)) {
                    //设置可以访问private变量的变量值
                    field.setAccessible(true);

                    field.set(instance, getBean(field.getName()));
                }
            }

            //判断实列对象是否是
            if (instance instanceof BeanNameAware) {
                ((BeanNameAware) instance).setBeanName(beanName);
            }

            for (BeanPostProcessor beanPostProcessor : beanPostProcessorList) {
                instance = beanPostProcessor.postProcessBeforeInitialization(instance, beanName);
            }
            //判断实列对象是否实现了InitializingBean接口
            if (instance instanceof InitializingBean) {
                ((InitializingBean) instance).afterPropertiesSet();
            }
            //BeanPostProcessor类中存在初始化前和初始化后的方法
            for (BeanPostProcessor beanPostProcessor : beanPostProcessorList) {
                instance = beanPostProcessor.postProcessAfterInitialization(instance, beanName);
            }


        } catch (InstantiationException e) {
            e.printStackTrace();
        } catch (IllegalAccessException e) {
            e.printStackTrace();
        } catch (InvocationTargetException e) {
            e.printStackTrace();
        } catch (NoSuchMethodException e) {
            e.printStackTrace();
        }

        return instance;
    }


    public Object getBean(String beanName) {
        //为什么需要定义beanDefinitionMap？假设通过beanName能够找到UserService类---->还得判断该类是单例得还是原型的,又得去解析,但是在扫描
        //的时候已经把对应扫描路径下的类都解析了一遍,这个时候如果没有Bean定义会显得很麻烦
        //判断传过来的beanName有没有存在在Bean定义的Map中,直接抛出异常,没有定义Bean
        if (!beanDefinitionMap.containsKey(beanName)) {
            throw new NullPointerException();
        }

        BeanDefinition beanDefinition = beanDefinitionMap.get(beanName);
        //判断作用域是单例的还是原型的
        if (beanDefinition.getScope().equals("singleton")) {
            Object singletonBean = singletonObjects.get(beanName);
            //下面这步是因为如果UserService中属性中存在@Autowired注解,通过OrderService的Bean的名字从单列池是拿不到的,所以需要下面这行代码
            if (singletonBean == null) {
                //单例Bean单单创建出来是不行的,因为需要保证每一次创建Bean都是同一个,需要创建一个单例池
                singletonBean = createBean(beanName, beanDefinition);
                singletonObjects.put(beanName, singletonBean);
            }
            return singletonBean;
        } else {
            // 原型,每一次都创建Bean,调用创建Bean的方法
            Object prototypeBean = createBean(beanName, beanDefinition);
            return prototypeBean;
        }

    }


    /**
     * 扫描:通过传进来一个配置类,解析得到扫描路径,遍历扫描路径下中每一个class文件,去加载每一个class文件取得到一个class对象,得到class对象看有没
     * 有Component注解,去解析Bean的名字,再判断有无Scope注解进行解析得到BeanDefinition,再把BeanDefinition存到BeanDefinitionMap中
     *
     * @param configClass 配置类
     */
    private void scan(Class configClass) {
        //判断传进来的类是否存在ComponentScan的注解
        if (configClass.isAnnotationPresent(ComponentScan.class)) {
            //获得注解对象拿到扫描路径
            ComponentScan componentScanAnnotation = (ComponentScan) configClass.getAnnotation(ComponentScan.class);
            //如果得到了扫描路径,会对这个路径下的类都给拿出来,去判断类上面有没有Component注解,但是真正要取的是你编译后的class文件
            String path = componentScanAnnotation.value();
            //.转换/
            path = path.replace(".", "/");  //     com/zhouyu/service
            //获取target目录中编译后的class文件,可以使用应用程序加载器
            ClassLoader classLoader = ZhouyuApplicationContext.class.getClassLoader();
            URL resource = classLoader.getResource(path);
            //获取目录
            File file = new File(resource.getFile());
            //判断这个文件是否是个目录
            if (file.isDirectory()) {
                //如果是目录可以遍历目录下的文件
                for (File f : file.listFiles()) {
                    //获取该文件的绝对路径
                    String absolutePath = f.getAbsolutePath();
                    //判断class文件有没有Component注解,首先是把这个类加载进来,得到了Class对象之后就能很好的去判断
                    //需要把绝对路径转换为com.zhouyu.service.UserService,通过截取的方式从com截取到.class结束
                    absolutePath = absolutePath.substring(absolutePath.indexOf("com"), absolutePath.indexOf(".class"));
                    //注意windows系统是\\这个符号
                    absolutePath = absolutePath.replace("\\", ".");

                    try {
                        //根据上面转化后的路径通过类加载器获得Class对象
                        Class<?> clazz = classLoader.loadClass(absolutePath);
                        //判断当前类上面有没有Component注解,如果有可以判断为该类是一个Bean
                        if (clazz.isAnnotationPresent(Component.class)) {
                            //判断完之后下面是做什么操作?创建对象(不对),目前只是判断该类有没有Component注解,并没有判断该类到底是单列还是原型的
                            //如果判断是一个单例Bean,难道就需要创建对象了吗？需要回到getBean(),是传入一个字符串然后返回一个类,从getBean()
                            //的方法中分析出来的步骤为beanName--->UserService--->又得解析一遍,但是在我们自己写得过程中已经解析了一遍,这个
                            //时候就会引出BeanDefinition（什么类型,作用域:单例还是原型,是否是懒加载）
                            //Spring源码中当它发现类中有Component注解后,就会判断该类是没有实现BeanPostProcessor接口
                            if (BeanPostProcessor.class.isAssignableFrom(clazz)) {
                                BeanPostProcessor instance = (BeanPostProcessor) clazz.getConstructor().newInstance();
                                beanPostProcessorList.add(instance);
                            }

                            //Component注解可以定义Bean的名称,如果没有定义Bean的名字,Spring会默认设置一个Bean的名称
                            //获得Component注解信息
                            Component componentAnnotation = clazz.getAnnotation(Component.class);
                            //获取value值,其中value就是bean的名字
                            String beanName = componentAnnotation.value();
                            if ("".equals(beanName)) {
                                //Spring默认获取Bean的名字,buildDefaultBeanName
                                beanName = Introspector.decapitalize(clazz.getSimpleName());
                            }

                            //如果是一个Bean我需要创建Bean定义
                            BeanDefinition beanDefinition = new BeanDefinition();
                            //定义类型
                            beanDefinition.setType(clazz);

                            //判断该类上面有没有Scope原型注解,如果有还需要判断是否有配置为singleton,如果设置为这个,仍然是单列
                            if (clazz.isAnnotationPresent(Scope.class)) {
                                //获取注解信息
                                Scope scopeAnnotation = clazz.getAnnotation(Scope.class);
                                String value = scopeAnnotation.value();
                                //如果存在Scope注解我就把value值设置到Bean定义中
                                beanDefinition.setScope(value);
                            } else {
                                //如果判断没有Scope注解,设置作用域为单例的
                                beanDefinition.setScope("singleton");
                            }
                            //定义一个Bean定义的Map中,在扫描的过程中,每扫描一个Bean生成一个Bean定义后都会存入到Bean定义Map中,BeanName在
                            //哪里？clazz.isAnnotationPresent(Component.class)
                            beanDefinitionMap.put(beanName, beanDefinition);
                        }
                    } catch (ClassNotFoundException | NoSuchMethodException e) {
                        e.printStackTrace();
                    } catch (InvocationTargetException e) {
                        e.printStackTrace();
                    } catch (InstantiationException e) {
                        e.printStackTrace();
                    } catch (IllegalAccessException e) {
                        e.printStackTrace();
                    }


                }
            }
        }
    }
}
