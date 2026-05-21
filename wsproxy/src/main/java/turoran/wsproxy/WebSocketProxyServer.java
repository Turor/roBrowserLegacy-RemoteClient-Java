package turoran.wsproxy;

import io.micronaut.websocket.WebSocketSession;
import io.micronaut.websocket.annotation.OnClose;
import io.micronaut.websocket.annotation.OnError;
import io.micronaut.websocket.annotation.OnMessage;
import io.micronaut.websocket.annotation.OnOpen;
import io.micronaut.websocket.annotation.ServerWebSocket;
import io.netty.bootstrap.Bootstrap;
import io.netty.buffer.ByteBuf;
import io.netty.buffer.Unpooled;
import io.netty.channel.*;
import io.netty.channel.nio.NioEventLoopGroup;
import io.netty.channel.socket.SocketChannel;
import io.netty.channel.socket.nio.NioSocketChannel;
import jakarta.inject.Inject;
import lombok.extern.slf4j.Slf4j;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentLinkedQueue;

@Slf4j
@ServerWebSocket("/ws/{target}")
public class WebSocketProxyServer {

    private static final int MAX_PENDING = 64;

    private final ProxyConfig proxyConfig;
    private final EventLoopGroup group = new NioEventLoopGroup();
    private final Map<String, ProxyContext> contexts = new ConcurrentHashMap<>();

    @Inject
    public WebSocketProxyServer(ProxyConfig proxyConfig) {
        this.proxyConfig = proxyConfig;
    }

    @OnOpen
    public void onOpen(String target, WebSocketSession session) {
        if (!proxyConfig.isEnabled()) {
            log.warn("WS proxy is disabled, closing connection for target: {}", target);
            session.close();
            return;
        }

        if (!proxyConfig.isTargetAllowed(target)) {
            log.warn("WS proxy blocked: {} (allowed: {})", target, proxyConfig.getAllowedTargets());
            session.close();
            return;
        }

        int colonIdx = target.lastIndexOf(':');
        if (colonIdx == -1) {
            log.warn("WS proxy rejected malformed target: {}", target);
            session.close();
            return;
        }

        String host = target.substring(0, colonIdx);
        int port;
        try {
            port = Integer.parseInt(target.substring(colonIdx + 1));
        } catch (NumberFormatException e) {
            log.warn("WS proxy rejected malformed target port: {}", target);
            session.close();
            return;
        }

        log.info("WS attempt: {}", target);

        ProxyContext context = new ProxyContext(session, target);
        contexts.put(session.getId(), context);

        Bootstrap b = new Bootstrap();
        b.group(group)
         .channel(NioSocketChannel.class)
         .option(ChannelOption.TCP_NODELAY, true)
         .handler(new ChannelInitializer<SocketChannel>() {
             @Override
             public void initChannel(SocketChannel ch) {
                 ch.pipeline().addLast(new TcpHandler(context));
             }
         });

        b.connect(host, port).addListener((ChannelFutureListener) future -> {
            if (future.isSuccess()) {
                log.info("WS proxy: connected to {}", target);
                context.onTcpConnected(future.channel());
            } else {
                log.error("WS proxy: failed to connect to {}: {}", target, future.cause().getMessage());
                cleanup(session.getId(), "TCP connect failed");
            }
        });
    }

    @OnMessage
    public void onMessage(String target, byte[] message, WebSocketSession session) {
        ProxyContext context = contexts.get(session.getId());
        if (context != null) {
            context.sendToTcp(message);
        }
    }

    @OnClose
    public void onClose(String target, WebSocketSession session) {
        cleanup(session.getId(), "client closed");
    }

    @OnError
    public void onError(String target, Throwable error, WebSocketSession session) {
        cleanup(session.getId(), "client error: " + error.getMessage());
    }

    private void cleanup(String sessionId, String reason) {
        ProxyContext context = contexts.remove(sessionId);
        if (context != null) {
            log.info("WS proxy: closed {} ({})", context.target, reason);
            context.close();
        }
    }

    private static class ProxyContext {
        private final WebSocketSession wsSession;
        private final String target;
        private final ConcurrentLinkedQueue<byte[]> pending = new ConcurrentLinkedQueue<>();
        private volatile Channel tcpChannel;
        private volatile boolean connected = false;
        private boolean cleaned = false;

        public ProxyContext(WebSocketSession wsSession, String target) {
            this.wsSession = wsSession;
            this.target = target;
        }

        public void onTcpConnected(Channel channel) {
            this.tcpChannel = channel;
            this.connected = true;
            byte[] data;
            while ((data = pending.poll()) != null) {
                channel.writeAndFlush(Unpooled.wrappedBuffer(data));
            }
        }

        public void sendToTcp(byte[] data) {
            if (connected) {
                tcpChannel.writeAndFlush(Unpooled.wrappedBuffer(data));
            } else if (pending.size() < MAX_PENDING) {
                pending.add(data);
            } else {
                log.warn("WS proxy: pending queue full for {}, dropping message", target);
            }
        }

        public void sendToWs(byte[] data) {
            if (wsSession.isOpen()) {
                wsSession.sendSync(data);
            }
        }

        public synchronized void close() {
            if (cleaned) return;
            cleaned = true;
            if (tcpChannel != null && tcpChannel.isOpen()) {
                tcpChannel.close();
            }
            if (wsSession.isOpen()) {
                wsSession.close();
            }
        }
    }

    private class TcpHandler extends ChannelInboundHandlerAdapter {
        private final ProxyContext context;

        public TcpHandler(ProxyContext context) {
            this.context = context;
        }

        @Override
        public void channelRead(ChannelHandlerContext ctx, Object msg) {
            ByteBuf buf = (ByteBuf) msg;
            try {
                byte[] bytes = new byte[buf.readableBytes()];
                buf.readBytes(bytes);
                context.sendToWs(bytes);
            } finally {
                buf.release();
            }
        }

        @Override
        public void channelInactive(ChannelHandlerContext ctx) {
            cleanup(context.wsSession.getId(), "server closed");
        }

        @Override
        public void exceptionCaught(ChannelHandlerContext ctx, Throwable cause) {
            cleanup(context.wsSession.getId(), "server error: " + cause.getMessage());
        }
    }
}
