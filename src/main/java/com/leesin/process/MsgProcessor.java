package com.leesin.process;

import com.alibaba.fastjson.JSONObject;
import com.leesin.protocol.IMDecoder;
import com.leesin.protocol.IMEncoder;
import com.leesin.protocol.IMMessage;
import com.leesin.protocol.IMP;
import io.netty.channel.Channel;
import io.netty.channel.group.ChannelGroup;
import io.netty.channel.group.DefaultChannelGroup;
import io.netty.handler.codec.http.websocketx.TextWebSocketFrame;
import io.netty.util.AttributeKey;
import io.netty.util.concurrent.GlobalEventExecutor;

/**
 * @description:
 * @author: Leesin.Dong
 * @date: Created in 2020/4/8 10:43
 * @version: ${VERSION}
 * @modified By:
 */
public class MsgProcessor {
    //记录在线用户
    //用来广播消息
    //就是一个set
    private static ChannelGroup onlineUsers = new DefaultChannelGroup(GlobalEventExecutor.INSTANCE);

    //定义一些扩展属性
    //AttributeKey.valueOf("nickName");  key的内容
    public static final AttributeKey<String> NICK_NAME = AttributeKey.valueOf("nickName");
    public static final AttributeKey<String> IP_ADDR = AttributeKey.valueOf("ipAddr");
    public static final AttributeKey<JSONObject> ATTRS = AttributeKey.valueOf("attrs");
    public static final AttributeKey<String> FROM = AttributeKey.valueOf("from");

    //自定义解码器
    private IMDecoder decoder = new IMDecoder();
    //自定义编码器
    private IMEncoder encoder = new IMEncoder();

    /*
     * 获取用户昵称
     * */
    public String getNickName(Channel client) {
        // attr获取的是attr的attribute，get获取的是里面的内容
        return client.attr(NICK_NAME).get();
    }

    /**
     * 获取用户远程IP地址
     *
     * @param client
     * @return
     */
    public String getAddress(Channel client) {
        return client.remoteAddress().toString().replaceFirst("/", "");
    }

    /**
     * 获取扩展属性
     *
     * @param client
     * @return
     */
    //get返回的是泛型，什么都可以
    public JSONObject getAttrs(Channel client) {
        try {
            return client.attr(ATTRS).get();
        } catch (Exception e) {
            return null;
        }
    }

    /**
     * 设置扩展属性
     *
     * @param client
     * @return
     */
    private void setAttrs(Channel client, String key, Object value) {
        try {
            JSONObject json = client.attr(ATTRS).get();
            json.put(key, value);
            client.attr(ATTRS).set(json);
        } catch (Exception e) {
            JSONObject json = new JSONObject();
            json.put(key, value);
            client.attr(ATTRS).set(json);
        }
    }

    /**
     * 获取系统时间
     *
     * @return
     */
    private Long sysTime() {
        return System.currentTimeMillis();
    }

    /*
     * 登出通知
     * */
    public void logout(Channel client) {
        //如果nickName为null，没有遵从聊天协议的连接，表示未非法登录
        if (getNickName(client) == null) {
            return;
        }

        for (Channel channel : onlineUsers) {
            IMMessage request = new IMMessage(IMP.SYSTEM.getName(), sysTime(), onlineUsers.size(), getNickName(client) + "离开");
            //编码成自定义的协议字符串
            String context = encoder.encode(request);
            //将字符串发送出去
            channel.writeAndFlush(new TextWebSocketFrame(context));
        }
    }

    /**
     * @description: 发送消息
     * @name: sendMsg
     * @param: client
     * @param: msg
     * @return: void
     * @date: 2020/4/8 11:41
     * @auther: Administrator
     **/

    public void sendMsg(Channel client, IMMessage msg) {
        sendMsg(client, encoder.encode(msg));
    }

    /**
     * 发送消息
     *
     * @param client
     * @param msg
     */
    public void sendMsg(Channel client, String msg) {
        //先解码成对象，对对象进行处理
        IMMessage request = decoder.decode(msg);
        if (null == request) {
            return;
        }

        String addr = getAddress(client);

        //如果是登录类型的话
        if (request.getCmd().equals(IMP.LOGIN.getName())) {
            client.attr(NICK_NAME).getAndSet(request.getSender());
            client.attr(IP_ADDR).getAndSet(addr);
            client.attr(FROM).getAndSet(request.getTerminal());
//			System.out.println(client.attr(FROM).get());
            onlineUsers.add(client);
            for (Channel channel : onlineUsers) {
                boolean isself = (channel == client);
                //set中没有保存当前的这个channel，说明没有登录进来
                if (!isself) {
                    // 是的话，就加入
                    request = new IMMessage(IMP.SYSTEM.getName(), sysTime(), onlineUsers.size(), getNickName(client) + "加入");
                } else {
                    //否则，说明已经建立了连接
                    //怎么算建立连接？ channel加到set中
                    request = new IMMessage(IMP.SYSTEM.getName(), sysTime(), onlineUsers.size(), "已与服务器建立连接！");
                }
                //如果是从terminal过来的，不做处理。
                if ("Console".equals(channel.attr(FROM).get())) {
                    channel.writeAndFlush(request);
                    continue;
                }
                //编码成String
                String content = encoder.encode(request);
                //以websockt发送出去
                channel.writeAndFlush(new TextWebSocketFrame(content));
            }
        } else if (request.getCmd().equals(IMP.CHAT.getName())) {
            //如果是聊天类型的请求
            for (Channel channel : onlineUsers) {
                boolean isself = (channel == client);
                if (isself) {
                    // 如果是自己的这个channel，就发给自己
                    request.setSender("you");
                } else {
                    //不是自己的channel，轮询发给其他人，添加名字
                    request.setSender(getNickName(client));
                }
                request.setTime(sysTime());

                if ("Console".equals(channel.attr(FROM).get()) & !isself) {
                    channel.writeAndFlush(request);
                    continue;
                }
                //编码成字符串
                String content = encoder.encode(request);
                //发送出去
                channel.writeAndFlush(new TextWebSocketFrame(content));
            }
        } else if (request.getCmd().equals(IMP.FLOWER.getName())) {
            //撒花类型
            JSONObject attrs = getAttrs(client);
            long currTime = sysTime();
            if (null != attrs) {
                //最长撒花时间
                long lastTime = attrs.getLongValue("lastFlowerTime");
                //60秒之内不允许重复刷鲜花
                int secends = 10;
                long sub = currTime - lastTime;
                if (sub < 1000 * secends) {
                    //一秒撒一次
                    //发给自己
                    request.setSender("you");
                    request.setCmd(IMP.SYSTEM.getName());
                    request.setContent("您送鲜花太频繁," + (secends - Math.round(sub / 1000)) + "秒后再试");

                    String content = encoder.encode(request);
                    client.writeAndFlush(new TextWebSocketFrame(content));
                    return;
                }
            }

            //正常送花
            for (Channel channel : onlineUsers) {
                //自己的channel
                if (channel == client) {
                    //发给自己
                    request.setSender("you");
                    request.setContent("你给大家送了一波鲜花雨");
                    setAttrs(client, "lastFlowerTime", currTime);
                } else {
                    //其他人的channel
                    //给其他人送鲜花
                    request.setSender(getNickName(client));
                    request.setContent(getNickName(client) + "送来一波鲜花雨");
                }
                request.setTime(sysTime());

                String content = encoder.encode(request);
                channel.writeAndFlush(new TextWebSocketFrame(content));
            }
        }


    }
}
