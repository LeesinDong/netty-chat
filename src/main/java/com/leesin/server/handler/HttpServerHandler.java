package com.leesin.server.handler;

import com.leesin.protocol.IMMessage;
import io.netty.channel.*;
import io.netty.handler.codec.http.*;
import lombok.extern.slf4j.Slf4j;

import java.io.File;
import java.io.RandomAccessFile;
import java.net.URL;

/**
 * @description:
 * @author: Leesin.Dong
 * @date: Created in 2020/4/8 12:17
 * @version: ${VERSION}
 * @modified By:
 */
@Slf4j
public class HttpServerHandler extends SimpleChannelInboundHandler<FullHttpRequest> {

    // 获取class路径
    private URL baseURL = HttpServerHandler.class.getResource("");
    private final String webroot = "webroot";

    private File getResource(String fileName) throws Exception {
        String basePath = baseURL.toURI().toString();
        int start = basePath.indexOf("classes/");
        basePath = (basePath.substring(0, start) + "/" + "classes/").replaceAll("/+", "/");

        String path = basePath + webroot + "/" + fileName;
//        log.info("BaseURL:" + basePath);
        path = !path.contains("file:") ? path : path.substring(5);
        path = path.replaceAll("//", "/");
        return new File(path);
    }


    @Override
    protected void channelRead0(ChannelHandlerContext ctx, FullHttpRequest request) throws Exception {
        String uri = request.getUri();
        //读取文件内容
        RandomAccessFile file = null;
        try {
            String page = uri.equals("/") ? "chat.html" : uri;
            //r只读
            file = new RandomAccessFile(getResource(page), "r");
        } catch (Exception e) {
            //保留数据并传递到下一个ChannelHandler
            ctx.fireChannelRead(request.retain());
            return;
        }

        HttpResponse response = new DefaultHttpResponse(request.getProtocolVersion(), HttpResponseStatus.OK);
        String contextType = "text/html;";
        //根据不同uri 发送不同协议
        if (uri.endsWith(".css")) {
            contextType = "text/css;";
        } else if (uri.endsWith(".js")) {
            contextType = "text/javascript;";
        } else if (uri.toLowerCase().matches(".*\\.(jpg|png|gif)$")) {
            String ext = uri.substring(uri.lastIndexOf("."));
            contextType = "image/" + ext;
        }

        response.headers().set(HttpHeaders.Names.CONTENT_TYPE, contextType + "charset=utf-8;");

        boolean keepAlive = HttpHeaders.isKeepAlive(request);

        //在页面上支持websocket，设置长连接
        if (keepAlive) {
            response.headers().set(HttpHeaders.Names.CONTENT_LENGTH, file.length());
            response.headers().set(HttpHeaders.Names.CONNECTION, HttpHeaders.Values.KEEP_ALIVE);
        }
        //写回
        ctx.write(response);
        //零拷贝
        //getChannel 此方法返回与此文件关联的文件通道
        //写回chat页面
        ctx.write(new DefaultFileRegion(file.getChannel(), 0, file.length()));

        ChannelFuture future = ctx.writeAndFlush(LastHttpContent.EMPTY_LAST_CONTENT);//告诉netty结束响应
        if (!keepAlive) {
            future.addListener(ChannelFutureListener.CLOSE);// 已经不连接了，关闭通道
        }

        file.close();
    }

    @Override
    public void exceptionCaught(ChannelHandlerContext ctx, Throwable cause) throws Exception {
        Channel client = ctx.channel();
        log.info("Client:" + client.remoteAddress() + "异常");
        //出现异常就关闭连接
        cause.printStackTrace();
        ctx.close();
    }
}
