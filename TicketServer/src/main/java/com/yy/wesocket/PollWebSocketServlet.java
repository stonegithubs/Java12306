package com.yy.wesocket;

import com.alibaba.fastjson.JSONObject;
import org.apache.log4j.Logger;
import org.springframework.stereotype.Component;

import javax.websocket.*;
import javax.websocket.server.PathParam;
import javax.websocket.server.ServerEndpoint;
import java.io.IOException;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

@Component
@ServerEndpoint(value="/websocket/{id}")
public class PollWebSocketServlet {

    private static final Logger logger = Logger.getLogger(PollWebSocketServlet.class);
    private static final Map<String,Session> SESSION_MAP = new ConcurrentHashMap<>();

    @OnOpen
    public void onOpen(@PathParam("id") String id, Session session) {

        Session oldSession = SESSION_MAP.get(id);
        if (oldSession!=null){
            try {
                oldSession.close();
            } catch (IOException e) {
                e.printStackTrace();
            }
        }
        SESSION_MAP.put(id,session);
        logger.info("WebSocketOpen: id=" + id);
    }

    @OnMessage
    public void onMessage(String message,@PathParam("id") String id) {
        logger.info("Message WebSocket: id=" + id + " message=" + message);
    }

    @OnClose
    public void onClose(@PathParam("id") String id) {
        SESSION_MAP.remove(id);
        logger.info("Closed WebSocket: id=" + id);
    }

    @OnError
    public void onError(@PathParam("id") String id, Throwable throwable, Session session) {
        logger.error("Error WebSocket: id=" + id + "Caused by " + throwable.getCause());
    }

    public void sendText(String id, String text) {
        Session session = SESSION_MAP.get(id);
        if(session == null) return;
        try {
            session.getBasicRemote().sendText(text);
        } catch (IOException e) {
            e.printStackTrace();
        }
    }

    public void sendJson(String id, JSONObject json) {
        Session session = SESSION_MAP.get(id);
        if(session == null) return;
        try {
            session.getBasicRemote().sendText(json.toJSONString());
        } catch (IOException e) {
            e.printStackTrace();
        }
    }
}
