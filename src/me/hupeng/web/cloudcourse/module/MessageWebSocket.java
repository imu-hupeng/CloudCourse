package me.hupeng.web.cloudcourse.module;

import java.io.IOException;
import java.util.ArrayList;
import java.util.Date;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

import javax.servlet.http.HttpSession;
import javax.websocket.CloseReason;
import javax.websocket.EndpointConfig;
import javax.websocket.OnClose;
import javax.websocket.OnError;
import javax.websocket.OnMessage;
import javax.websocket.OnOpen;
import javax.websocket.Session;
import javax.websocket.server.ServerEndpoint;

import me.hupeng.web.cloudcourse.MainSetup;
import me.hupeng.web.cloudcourse.bean.Message;
import me.hupeng.web.cloudcourse.bean.MessageJsonModule;
import me.hupeng.web.cloudcourse.bean.User;

import org.nutz.dao.Cnd;
import org.nutz.dao.Dao;
import org.nutz.ioc.loader.annotation.Inject;
import org.nutz.ioc.loader.annotation.IocBean;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;

/**
 * 处理房间的消息的Socket服务类.<br>
 * 注意:部署的时候JDK版本必须大于等于1.8,Tomcat版本必须大于等于8.
 * @author HUPENG
 * */
@ServerEndpoint(value = "/websocket", configurator=GetHttpSessionConfigurator.class)
@IocBean
public class MessageWebSocket {
	
    /**
     * 存放Websocket Session Id --> Session 的映射关系
     */
    protected static ConcurrentHashMap<String, Session> sessions = new ConcurrentHashMap<>();
	
    /**
     * 存放Websocket Session Id --> Course 的映射关系
     * */
    private static ConcurrentHashMap<String, Integer> courses = new ConcurrentHashMap<>();
    
    
    /**
     * 存放Websocket Session Id --> Session 的映射关系
     * */
    private static ConcurrentHashMap<String, HttpSession>httpSessions  = new ConcurrentHashMap<>();
    

    Dao dao = MainSetup.ioc.get(Dao.class);
    
	@OnOpen
	public void onOpen(Session session, EndpointConfig config) {
		sessions.put(session.getId(), session);
		
		HttpSession httpSession= (HttpSession) config.getUserProperties().get(HttpSession.class.getName()); 
		httpSessions.put(session.getId(), httpSession);
		
		System.out.println("会话：" + session.getId() + " 连入服务器");
    }
	
	@OnClose
	public void onClose(Session session, CloseReason closeReason){
		sessions.remove(session.getId());
		courses.remove(session.getId());
		httpSessions.remove(session.getId());
		System.out.println("会话："+ session.getId() + " 离开服务器");
	}
	
    /**
     * WebSocket会话出错时调用,默认调用onClose.
     */
    public void onError(Session session, java.lang.Throwable throwable) {
    	System.out.println("会话："+ session.getId() + " 发生错误");
    	onClose(session, null);
    }
   
    /**
     * 收到消息之后的调用此方法向房间内的其他主机发送消息.<br>
     * 暂定上传的消息格式类似于:<br>
     * 主机进入房间：{"action": "in_course","course_id":1}
     * 主机发送消息：{"action":"send_msg","course_id":1,"msg":"this is a test message","video_time":3}<br>
     * 消息格式为Json格式,其中time字段为发送此信息时视频的时间轴信息,以秒计.<br>
     * @author		HUPENG
     * @version		0.0.1
     * @param		message	浏览器端上传的消息
     * @param		session	标识浏览器端的session对象
     * @exception	IOException 在发送消息的时候可能发生的输入输出异常
     * @return		void
     * */
	@OnMessage
	public void onMessage(String message, Session session) throws IOException,
			InterruptedException {
		System.out.println("收到 会话: " + session.getId() + " 的消息（" + message + "）");
		
		JsonParser parse =new JsonParser();
		JsonObject json=(JsonObject) parse.parse(message);
		String action = json.get("action").getAsString();
		
		switch (action) {
			case "in_course":
				int courseId = json.get("course_id").getAsInt();
				courses.put(session.getId(), courseId);
				break;
			case "send_msg":
//				try {
//					int userId = (Integer)httpSessions.get(session.getId()).getAttribute("user_id");
//				} catch (Exception e) {
//					return;
//				}
				
				courseId = json.get("course_id").getAsInt();
				String msg = json.get("msg").getAsString();
				int videoTime = json.get("video_time").getAsInt();
//				Message messageBean = new Message(courseId, userId, videoTime, message, sendTime)
				Message messageBean = null;
				try {
					messageBean = new Message(courseId, (Integer)httpSessions.get(session.getId()).getAttribute("user_id"), videoTime, msg, new Date(System.currentTimeMillis()));
				} catch (Exception e) {
					messageBean = new Message(courseId, 1, videoTime, msg, new Date(System.currentTimeMillis()));
				}
				dao.insert(messageBean);
				
			    Gson gson = new GsonBuilder()  
	            // 设置日期时间格式，另有2个重载方法  
	            // 在序列化和反序化时均生效  
	            .setDateFormat("yyyy-MM-dd HH:mm:ss")  
	            // 禁此序列化内部类  
	            .disableInnerClassSerialization()  
	            //禁止转义html标签  
	            .disableHtmlEscaping()  
	            //格式化输出  
	            .setPrettyPrinting()  
	            .create();  
				for(String key: courses.keySet()){
					if (courses.get(key).equals(courseId) && !key.equals(session.getId())) {
						Map<String, Object>msgMap = new LinkedHashMap<>();
						msgMap.put("action", "receive_msg");
						msgMap.put("message", new MessageJsonModule(messageBean));
						try {
							
							User user = dao.fetch(User.class, Cnd.where("id", "=",(Integer)httpSessions.get(session.getId()).getAttribute("user_id")));
//							User user = dao.fetch(User.class, Cnd.where("id", "=",1));
							user.setPassword(null);
							msgMap.put("user", user);
						} catch (Exception e) {
							// TODO: handle exception
							msgMap.put("user", null);
						}
//						System.out.println("**************\n" + gson.toJson(msgMap));
						sessions.get(key).getBasicRemote().sendText(gson.toJson(msgMap));
					}
				}
				break;
			default:
				break;
		}
	}
}
