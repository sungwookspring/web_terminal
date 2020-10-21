package com.ssh.ssh.websocket;


import com.ssh.ssh.sshclient.SshHandler;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;
import org.springframework.web.socket.*;

import javax.websocket.server.ServerEndpoint;

@Component
@ServerEndpoint(value = "/sock")
@RequiredArgsConstructor
@Slf4j
public class WebsocketHandler implements WebSocketHandler {
    private final SshHandler ssh;

    @Override
    public void afterConnectionEstablished(WebSocketSession session) throws Exception {
        ssh.init(session);
        ssh.connect_sshserver();
        log.info("[*] websocket connecting is succesfully connected");
    }

    @Override
    public void handleMessage(WebSocketSession session, WebSocketMessage<?> message) throws Exception {
        if(message instanceof TextMessage){
            String command = message.getPayload().toString();
            ssh.sendCommand(command);
        }
    }

    @Override
    public void handleTransportError(WebSocketSession session, Throwable exception) throws Exception {
        log.error("[*] websocket error");
    }

    @Override
    public void afterConnectionClosed(WebSocketSession session, CloseStatus closeStatus) throws Exception {
        ssh.close();
    }

    @Override
    public boolean supportsPartialMessages() {
        return false;
    }
}
