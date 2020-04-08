package com.leesin.client;

import com.leesin.client.handler.ChatClientHandler;
import com.leesin.protocol.IMDecoder;
import com.leesin.protocol.IMEncoder;
import com.leesin.server.ChatServer;
import com.leesin.server.handler.HttpServerHandler;
import com.leesin.server.handler.TerminalServerHandler;
import com.leesin.server.handler.WebSocketServerHandler;
import io.netty.bootstrap.Bootstrap;
import io.netty.bootstrap.ServerBootstrap;
import io.netty.channel.*;
import io.netty.channel.nio.NioEventLoopGroup;
import io.netty.channel.socket.SocketChannel;
import io.netty.channel.socket.nio.NioServerSocketChannel;
import io.netty.channel.socket.nio.NioSocketChannel;
import io.netty.handler.codec.http.HttpObjectAggregator;
import io.netty.handler.codec.http.HttpServerCodec;
import io.netty.handler.codec.http.websocketx.WebSocketServerProtocolHandler;
import io.netty.handler.stream.ChunkedWriteHandler;
import lombok.extern.slf4j.Slf4j;

import java.io.IOException;

/**
 * @description:
 * @author: Leesin.Dong
 * @date: Created in 2020/4/8 15:26
 * @version: ${VERSION}
 * @modified By:
 */
@Slf4j
public class ChatClient {
    private ChatClientHandler clientHandler;
    private String host;
    private int port;

    public ChatClient(String nickName){
        this.clientHandler = new ChatClientHandler(nickName);
    }


    public void connect(String host, int port) {
        this.host = host;
        this.port = port;

        EventLoopGroup workGroup = new NioEventLoopGroup();
        try {
            Bootstrap b = new Bootstrap();
            b.group(workGroup)
                    .channel(NioSocketChannel.class)
                    .option(ChannelOption.SO_KEEPALIVE, true)
                    .handler(new ChannelInitializer<SocketChannel>() {
                        @Override
                        protected void initChannel(SocketChannel ch) throws Exception {
                            ChannelPipeline pipeline = ch.pipeline();
                            pipeline.addLast(new IMDecoder());
                            pipeline.addLast(new IMEncoder());
                            ch.pipeline().addLast(clientHandler);
                        }
                    });
            ChannelFuture f = b.connect(this.host, this.port).sync();
            f.channel().closeFuture().sync();
        } catch (Exception e) {
            e.printStackTrace();
        } finally {
            workGroup.shutdownGracefully();
        }
    }

    public static void main(String[] args) {
        new ChatClient("Tom").connect("127.0.0.1", 8080);
        String url = "http://localhost:8080/images/a.png";
        System.out.println(url.toLowerCase().matches(".*\\.(gif|png|jpg)$"));
    }
}
