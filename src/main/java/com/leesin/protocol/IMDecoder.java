package com.leesin.protocol;

import io.netty.buffer.ByteBuf;
import io.netty.channel.ChannelHandlerContext;
import io.netty.handler.codec.ByteToMessageDecoder;
import org.msgpack.MessagePack;
import org.msgpack.MessageTypeException;

import java.util.List;
import java.util.concurrent.ExecutionException;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * @description: 解码器
 * @author: Leesin.Dong
 * @date: Created in 2020/4/8 7:43
 * @version: ${VERSION}
 * @modified By:
 */
public class IMDecoder extends ByteToMessageDecoder {

    //解析IM写一下请求内容的正则
    private Pattern pattern = Pattern.compile("^\\[(.*)\\](\\s\\-\\s(.*))?");

    @Override
    protected void decode(ChannelHandlerContext ctx, ByteBuf in, List<Object> out) throws Exception {
        try {
            //先获取可读字节数
            final int length = in.readableBytes();
            final byte[] array = new byte[length];
            //?这里没代码？
            //网络传输过来的内容，变成一个字符串
            //这里不理解，好像没有读进去把?不过不影响，因为下面的read没有用到这里。
            String content = new String(array, in.readerIndex(), length);

            //空消息不解析
            if (!(null == content || "".equals(content.trim()))) {
                if (!IMP.isIMP(content)) {
                    ctx.channel().pipeline().remove(this);
                }
            }

            //把字符串变成一个我们能够识别的IMMessage对象
            //把byteBUf中可读的读到array中
            in.getBytes(in.readerIndex(), array, 0, length);
            //利用序列化对象框架，将网络流信息直接转化成IMMessage对象
            out.add(new MessagePack().read(array, IMMessage.class));
            in.clear();
        } catch (MessageTypeException e) {
            ctx.channel().pipeline().remove(this);
        }
    }

    /**
     * 字符串解析成自定义即时通信协议
     * <p>
     * 返回对象IMMessage
     *
     * @param
     * @return
     */
    //系统命令，例如[命令][命令发送时间][接收人] - 系统提示内容例如：
    //：[SYSTEM][124343423123][Tom老师] – Student加入聊天室
    public IMMessage decode(String msg) {
        if (null == msg || "".equals(msg.trim())) {
            return null;
        }
        try {
            Matcher m = pattern.matcher(msg);
            String header = "";
            String content = "";
            if (m.matches()) {
                //^\[(.*)\](\s\-\s(.*))?
                //1 3 说的是括号里面的，数左括号的位置 ，是几就是几
                header = m.group(1);
                content = m.group(3);
            }

            //[][][]-content
            //分解[][][]
            String[] heards = header.split("\\]\\[");
            long time = 0;
            time = Long.parseLong(heards[1]);
            String nickName = heards[2];
            //昵称最多十个字
            nickName = nickName.length() < 10 ? nickName : nickName.substring(0, 9);

            //根据消息，封装成不同IMMessage对象
            if (msg.startsWith("[" + IMP.LOGIN.getName() + "]")) {
                return new IMMessage(heards[0], heards[3], time, nickName);
            } else if (msg.startsWith("[" + IMP.CHAT.getName() + "]")) {
                return new IMMessage(heards[0], time, nickName, content);
            } else if (msg.startsWith("[" + IMP.FLOWER.getName() + "]")) {
                return new IMMessage(heards[0], heards[3], time, nickName);
            } else {
                return null;
            }
        } catch (Exception e) {
            e.printStackTrace();
            return null;
        }
    }
}
