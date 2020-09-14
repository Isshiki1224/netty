package com.xm.nio.nio;

import com.xm.nio.util.CodecUtil;
import lombok.extern.slf4j.Slf4j;

import java.io.IOException;
import java.net.InetSocketAddress;
import java.nio.ByteBuffer;
import java.nio.channels.ClosedChannelException;
import java.nio.channels.SelectionKey;
import java.nio.channels.Selector;
import java.nio.channels.ServerSocketChannel;
import java.nio.channels.SocketChannel;
import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;

@Slf4j
public class NioServer {

    private final Selector selector;

    public NioServer() throws Exception {
        //打开 serverSocketChannel
        ServerSocketChannel serverSocketChannel = ServerSocketChannel.open();
        serverSocketChannel.configureBlocking(false);//设置非阻塞
        serverSocketChannel.socket().bind(new InetSocketAddress(9001));//绑定端口
        selector = Selector.open();//创建selector
        serverSocketChannel.register(selector, SelectionKey.OP_ACCEPT);//注册到selector中，并对accept（接受新连接）感兴趣
        log.info("server 启动成功");
        handleKeys();

    }

    private void handleKeys() throws Exception {
        while (true) {
            int select = selector.select(30 * 1000L);
            if (select == 0) {
                continue;
            }
            log.info("channel数量: " + select);
            Iterator<SelectionKey> iterator = selector.selectedKeys().iterator();
            while (iterator.hasNext()) {
                SelectionKey key = iterator.next();
                iterator.remove();//移除下面要处理的selectionKey
                if (!key.isValid()) {
                    continue;
                }
                handleKey(key);
            }
        }
    }

    private void handleKey(SelectionKey key) throws Exception {
        if (key.isAcceptable()) {//接受连接就绪
            handleAcceptableKey(key);
        }
        if (key.isReadable()) {//读就绪
            handleReadableKey(key);
        }
        if (key.isWritable()) {//写就绪
            handleWriteableKey(key);
        }
    }

    private void handleAcceptableKey(SelectionKey key) throws Exception {
        //接受clientSocketChannel
        SocketChannel clientChannel = ((ServerSocketChannel) key.channel()).accept();
        clientChannel.configureBlocking(false);
        log.info("接受新的channel");
        clientChannel.register(selector, SelectionKey.OP_READ, new ArrayList<String>());//注册
    }

    @SuppressWarnings(value = "unchecked")
    private void handleReadableKey(SelectionKey key) throws ClosedChannelException {
        SocketChannel clientChannel = (SocketChannel) key.channel();
        //读取数据
        ByteBuffer readBuffer = CodecUtil.read(clientChannel);
        //处理连接已经断开的情况
        if (readBuffer == null) {
            log.info("连接断开");
            clientChannel.register(selector, 0);
            return;
        }
        //打印数据
        if (readBuffer.position() > 0) {
            String content = CodecUtil.newString(readBuffer);
            log.info("读取数据" + content);
            List<String> respQueue = (ArrayList<String>) key.attachment();
            respQueue.add("响应:" + content);
            //注册 client socket channel 到 selector
            clientChannel.register(selector, SelectionKey.OP_WRITE, key.attachment());
        }
    }

    @SuppressWarnings(value = {"Duplicates", "unchecked"})
    public void handleWriteableKey(SelectionKey key) throws ClosedChannelException {
        //client socket channel
        SocketChannel clientSocketChannel = (SocketChannel) key.channel();

        //遍历响应队列
        List<String> responseQueue = (ArrayList<String>) key.attachment();
        for (String content : responseQueue) {
            //打印数据
            log.info("写入数据:" + content);
            //返回
            CodecUtil.write(clientSocketChannel, content);
        }
        responseQueue.clear();

        //注册client socket channel 到 selector
        clientSocketChannel.register(selector, SelectionKey.OP_READ, responseQueue);
    }

    public static void main(String[] args) throws Exception {
        NioServer server = new NioServer();
    }


}
