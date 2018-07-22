package springframe.servlet;

import org.omg.CORBA.BAD_CONTEXT;
import org.omg.CORBA.INITIALIZE;
import springframe.annotation.*;

import javax.servlet.ServletConfig;
import javax.servlet.ServletException;
import javax.servlet.http.HttpServlet;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.lang.annotation.Annotation;
import java.lang.reflect.Array;
import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.net.URL;
import java.util.*;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public class DispatcherServlet extends HttpServlet {

    //加载的配置文件
    private Properties properties=new Properties();

    //存储所有的类名
    private List<String> names=new ArrayList<String>();

    //ioc容器
    private Map<String,Object> ioc=new HashMap<String,Object>();

    //handlermapping url和method的映射
//    private Map<String,Method> handlerMapping=new HashMap<String, Method>();

    List<Handler> handlers=new ArrayList<Handler>();//

    @Override
    protected void doGet(HttpServletRequest req, HttpServletResponse resp) throws ServletException, IOException {
        doPost(req, resp);
    }

    @Override
    protected void doPost(HttpServletRequest req, HttpServletResponse resp) throws ServletException, IOException {

        //6.调用自己写的dispatcherServlet方法
        try {
//            doDispatcherServlet(req,resp);
            doDispathcer(req,resp);
        } catch (Exception e) {
            e.printStackTrace();
            resp.getWriter().write("500 Execption:"+Arrays.toString(e.getStackTrace()));
        }
    }

    private void doDispathcer(HttpServletRequest req, HttpServletResponse resp) throws Exception {
        Handler handler=getHandler(req);
        if(null == handler){
            resp.getWriter().write("404 NOT FOUND");
            return;
        }
        //获取方法的参数列表
        Class<?>[] parameterTypes=handler.method.getParameterTypes();
        //保存所有需要自动赋值的参数值
        Object[] paramValues=new Object[parameterTypes.length];
        //获取传入的参数
        Map<String,String[]> paramMap=req.getParameterMap();
        for(Map.Entry<String,String[]> param:paramMap.entrySet()){
            String value=Arrays.toString(param.getValue()).replaceAll("\\[|\\]","").replaceAll(",\\s",",");
            //找到匹配的参数，进行填充
            if(!handler.paramIndexMap.containsKey(param.getKey())){continue;}
            int index=handler.paramIndexMap.get(param.getKey());
            paramValues[index]=convert(parameterTypes[index],value);
        }
        //设置方法中的request和response对象
        int requestIndex=handler.paramIndexMap.get(HttpServletRequest.class.getName());
        paramValues[requestIndex]=req;
        int responseIndex=handler.paramIndexMap.get(HttpServletResponse.class.getName());
        paramValues[responseIndex]=resp;
        handler.method.invoke(handler.controller,paramValues);

    }

    private Object convert(Class<?> clazzType,String value){
        if(clazzType==Integer.class){
            return Integer.valueOf(value);
        }
        return value;
    }

    private void doDispatcherServlet(HttpServletRequest req, HttpServletResponse resp) throws Exception {

//        String url=req.getRequestURI();
//        url=url.replace(req.getContextPath(),"").replaceAll("/+","/");
//
//        if(!handlerMapping.containsKey(url)) {
//            resp.getWriter().write("404 NOT FOUND");
//        }
//
//        Method method=handlerMapping.get(url);
////        Map<String,String[]> maps=req.getParameterMap();
////        String beanName=lowerFirstName(method.getDeclaringClass().getSimpleName());
////        method.invoke(ioc.get(beanName),)
//        System.out.println(method);
    }


    //根据url找到封装类对应的method的handler
    private Handler getHandler(HttpServletRequest request){
        if(handlers.isEmpty()){return null;}

        String url=request.getRequestURI();
        url=url.replace(request.getContextPath(),"").replaceAll("/+","/");
        for(Handler handler:handlers){
            Matcher matcher=handler.pattern.matcher(url);
            //如果没有匹配的继续下一个
            if(!matcher.matches()) continue;
            return handler;
        }
        return null;
    }

    @Override
    public void init(ServletConfig config) throws ServletException {
        //1.加载配置文件
        doLoadedConfig(config.getInitParameter("contextConfigLocation"));
        //2.扫描所有的类
        doScanner(properties.getProperty("scanpackage"));
        //3.初始化刚扫描的类，并存入IOC容器
        doIinstance();
        //4.自动注入
        doAutowired();
        //5.初始化HandlerMapping
        initHandlerMapping();
        System.out.println("Spring is  init");
    }

    //进行url和method的关联
    private void initHandlerMapping() {
        if(ioc.isEmpty()) {return;}
        for(Map.Entry<String,Object> entry:ioc.entrySet()){
            Class<?> clazz=entry.getValue().getClass();//拿到实例对象
            if(!clazz.isAnnotationPresent(ZController.class)){continue;}

            String baseurl="";
            if(clazz.isAnnotationPresent(ZRequestMapping.class)){
                ZRequestMapping requestMapping=clazz.getAnnotation(ZRequestMapping.class);
                baseurl=requestMapping.value();
            }
            Method[] methods=clazz.getMethods();
            for(Method m:methods){
                if(!m.isAnnotationPresent(ZRequestMapping.class)){return;}
                ZRequestMapping requestMapping=m.getAnnotation(ZRequestMapping.class);
                baseurl=(baseurl+'/'+requestMapping.value()).replaceAll("/+","/");
                Handler handler=new Handler(entry.getValue(),m,Pattern.compile(baseurl));
                handlers.add(handler);
                System.out.println("Mapped:"+baseurl+","+m);
            }
        }
    }

    //依赖注入，就是赋值
    private void doAutowired() {

        if(ioc.isEmpty()) return;
        for (Map.Entry<String,Object> entry:ioc.entrySet()){
            //拿到所有的字段
            Field[] fields=entry.getValue().getClass().getDeclaredFields();
            for(Field field:fields){
                if(!field.isAnnotationPresent(ZAutowired.class)) continue;
                //初始化字段
                ZAutowired zAutowired=field.getAnnotation(ZAutowired.class);
                String beanName=zAutowired.value();
                if("".equals(beanName.trim())){
                    beanName=field.getType().getName();
                }
                //赋于修改权限
                field.setAccessible(true);
                    try {
                        //对象，字段示例
                        field.set(entry.getValue(),ioc.get(beanName));
                        continue;
                    } catch (IllegalAccessException e) {
                        e.printStackTrace();
                }
            }
        }
    }

    //实例化刚才扫描的类，并加入到IOC容器中
    private void doIinstance() {
        if(names.isEmpty()) return;
        try {
            for (String name : names) {
                Class<?> clazz=Class.forName(name);
                //利用反射实例化类
                if(clazz.isAnnotationPresent(ZController.class)){
                    Object obj=clazz.newInstance();
                    //类的名字首字母小写
                    String beanName=lowerFirstName(clazz.getSimpleName());
                    ioc.put(beanName,obj);
                }else if(clazz.isAnnotationPresent(ZService.class)){
                    //默认的类名首字母小写
                    //如果有自定义的beanname，则使用自定义的
                    ZService service=clazz.getAnnotation(ZService.class);
                    String beanName=service.value();
                    if("".equals(beanName)){
                        beanName=lowerFirstName(clazz.getSimpleName());
                    }
                    Object instance = clazz.newInstance();
                    ioc.put(beanName, instance);
                    //如果注入的是接口，自动注入它的实现类
                    Class<?>[] interfaces=clazz.getInterfaces();
                    for (Class<?> i:interfaces){
                        ioc.put(i.getName(),instance);
                    }
                }else{
                    continue;
                }
            }
        }catch (Exception e){
            e.printStackTrace();
        }
    }

    //将首字母小写
    private String lowerFirstName(String simpleName) {
        char[] chars=simpleName.toCharArray();
        chars[0]+=32;
        return String.valueOf(chars);
    }

    //扫描所有的类
    private void doScanner(String scanpackage) {
        URL url=this.getClass().getClassLoader().getResource(scanpackage.replaceAll("\\.","/"));
        File classPathDir=new File(url.getFile());
        for(File file:classPathDir.listFiles()){
            if(file.isDirectory()){
                doScanner(scanpackage+"."+file.getName());
            }else{
                String name=scanpackage+"."+file.getName().replaceAll(".class","");
                names.add(name);
            }
        }
    }

    //加载配置文件
    private void doLoadedConfig(String servletConfig) {
        InputStream is=this.getClass().getClassLoader().getResourceAsStream(servletConfig);
        try {
            properties.load(is);
        } catch (IOException e) {
            e.printStackTrace();
        }finally {
            if(null!=is){
                try {
                    is.close();
                } catch (IOException e) {
                    e.printStackTrace();
                }
            }
        }
    }

    //记录controller中的handlermapping和method的对应关系
    private class Handler{
        protected Object controller;//保存方法对应的实例
        protected Method method;//对应的method方法
        protected Pattern pattern;//对应url的正则
        protected Map<String,Integer> paramIndexMap;//参数顺序，方法参数以及对应的位置

        public Handler(Object controller, Method method, Pattern pattern) {
            this.controller = controller;
            this.method = method;
            this.pattern = pattern;

            paramIndexMap = new HashMap<String, Integer>();
            putParamIndexMapping(method);
        }

        private void putParamIndexMapping(Method method) {
            Annotation[][] params=method.getParameterAnnotations();
            //提取所有有注解的参数
            for(int i=0;i<params.length;i++){
                for(Annotation param:params[i]){
                    if(param instanceof ZRequestParam){
                        String paramName=((ZRequestParam) param).value();
                        if(!"".equals(paramName.trim()))
                        paramIndexMap.put(paramName,i);
                    }
                }
            }
            //提取方法中的request和response参数
            Class<?>[] parameterTypes=method.getParameterTypes();
            for(int i=0;i<parameterTypes.length;i++){
                Class<?> parameterType=parameterTypes[i];
                if(parameterType == HttpServletRequest.class
                        || parameterType ==HttpServletResponse.class){
                    paramIndexMap.put(parameterType.getName(),i);
                }
            }

        }

    }
}
