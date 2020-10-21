package com.ssh.ssh.sshclient;

import com.jcraft.jsch.ChannelShell;
import com.jcraft.jsch.JSch;
import com.jcraft.jsch.JSchException;
import com.jcraft.jsch.Session;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;
import org.springframework.web.socket.TextMessage;
import org.springframework.web.socket.WebSocketSession;

import java.io.*;

@Component
@Slf4j
public class SshHandler {
    private WebSocketSession webSocketSession;
    private Session session;
    private PipedInputStream pinWrapper = new PipedInputStream(4096);
    private PipedOutputStream pout;
    private JSch jsch;
    private String lastCommand;
    private String prompt;
    PrintStream ps;


    public void init(WebSocketSession session) {
        jsch = new JSch();
        this.webSocketSession = session;
    }

    public void connect_sshserver() {
        try {
            this.session = set_session();
            ChannelShell channelShell = set_channel(this.session);
            
            // == 초기화 작업 == //
            first_login_message(); // 첫 번째 로그인 메세지
            get_prompt(); // 프롬프트 저장

            // 명령어 수신 쓰레드 시작(프로그램 종료시 때까지 실행)
            Thread thread = new Thread(() -> {
                while (true) {
                    try {
                        if (pinWrapper.available() != 0) {
                            String response = readResponse();
                            send_to_client_bypass_filter(response);
                        }
                        if(channelShell.isClosed()) channelShell.disconnect();
                        Thread.sleep(100);
                    } catch (Exception e) {
                        e.printStackTrace();
                    }
                }
            });
            thread.setDaemon(true);
            thread.start();

        } catch(Exception e) {
            e.printStackTrace();
        }
    }

    public void sendCommand(String command){
        lastCommand = command;
        ps.println(command);
        ps.flush();
    }

    public String readResponse() throws IOException {
        final StringBuilder s = new StringBuilder();
        while(pinWrapper.available() > 0) {
            s.append((char) pinWrapper.read());
        }

        return s.toString();
    }

    /***
     * 첫 번째 접속 메세지 출력(배너 등)
     */
    private void first_login_message() throws IOException, InterruptedException {
        // bad code: 버퍼에 데이터가 찰 때까지 대기
        Thread.sleep(1000);
        while (pinWrapper.available() > 0) {
            String response = readResponse();
            send_to_client(response);
        }
        log.info("[*] first login message is printed");
    }

    /***
     * 프롬프트 검색과 웹소켓 전달
     */
    private void get_prompt() throws IOException, InterruptedException {
        String prefix = "<prompt_start>";
        String endfix = "<prompt_end>";
        // 쉘 프롬프트를 얻기 위해 빈 명령어 실행
        ps.println("");
        ps.flush();
        Thread.sleep(500);
        
        // bad code: 버퍼에 데이터가 찰 때까지 대기
        while (pinWrapper.available() > 0) {
            String r = readResponse();
            prompt = r;
        }

        prompt = prompt.split("\r\n")[1];
        send_to_client(prefix + prompt + endfix);

        log.info("[*] prompt: " + prompt);
    }

    /***
     * 웹소켓에 보낼 메세지 필터
     * @param to_send_message
     * @return
     */
    private boolean message_filter(String to_send_message){
        // 마지막으로 입련한 커맨드는 전송 X
        if(to_send_message.equals(lastCommand)){
            return false;
        }
        // 프롬프트 전송 X
        else if(to_send_message.equals(prompt)){
            return false;
        }

        return true;
    }

    // 필터 없이 바로 응답 전송(주로 상태코드 전달에 사용)
    private void send_to_client(String message){
        try {
            webSocketSession.sendMessage(new TextMessage(message));
        } catch (IOException e) {
            e.printStackTrace();
        }

    }

    // 필터를 통화한 응답 전송
    private void send_to_client_bypass_filter(String message){
        try {
            String[] messages = message.split("\r\n");

            // 제일 마지막과 마지막 응답은 프롬프트이므로 전송 X
            for(int idx=0; idx<messages.length; idx++){
                if(message_filter(messages[idx])){
                    webSocketSession.sendMessage(new TextMessage(messages[idx]));
                }
            }
        } catch (IOException e) {
            e.printStackTrace();
        }
    }

    public void close(){
        if(this.session != null) this.session.disconnect();
    }

    private Session set_session() throws JSchException {
        UserConfig userConfig = new UserConfig();
        Session session = jsch.getSession(userConfig.getUser(), userConfig.getHost(), userConfig.getPort());
        session.setPassword(userConfig.getPassword());

        java.util.Properties config = new java.util.Properties();
        config.put("StrictHostKeyChecking", "no");
        session.setConfig(config);
        session.connect();

        return session;
    }

    private ChannelShell set_channel(Session session) throws JSchException, IOException {
        ChannelShell shellChannel = (ChannelShell) session.openChannel("shell");

        // 입출력 설정
        pout = new PipedOutputStream(pinWrapper);
        shellChannel.setOutputStream(pout);
        OutputStream ops = shellChannel.getOutputStream();
        ps = new PrintStream(ops);

        shellChannel.connect();

        return shellChannel;
    }
}
