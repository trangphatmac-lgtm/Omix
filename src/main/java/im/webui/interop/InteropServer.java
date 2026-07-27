package im.webui.interop;

import cn.omix.Client;
import com.google.gson.Gson;
import com.google.gson.JsonObject;
import io.netty.bootstrap.ServerBootstrap;
import io.netty.buffer.Unpooled;
import io.netty.channel.Channel;
import io.netty.channel.ChannelHandlerContext;
import io.netty.channel.ChannelInitializer;
import io.netty.channel.ChannelOption;
import io.netty.channel.SimpleChannelInboundHandler;
import io.netty.channel.group.ChannelGroup;
import io.netty.channel.group.DefaultChannelGroup;
import io.netty.channel.nio.NioEventLoopGroup;
import io.netty.channel.socket.SocketChannel;
import io.netty.channel.socket.nio.NioServerSocketChannel;
import io.netty.handler.codec.http.DefaultFullHttpResponse;
import io.netty.handler.codec.http.FullHttpRequest;
import io.netty.handler.codec.http.FullHttpResponse;
import io.netty.handler.codec.http.HttpHeaderNames;
import io.netty.handler.codec.http.HttpHeaderValues;
import io.netty.handler.codec.http.HttpMethod;
import io.netty.handler.codec.http.HttpObjectAggregator;
import io.netty.handler.codec.http.HttpResponseStatus;
import io.netty.handler.codec.http.HttpServerCodec;
import io.netty.handler.codec.http.HttpUtil;
import io.netty.handler.codec.http.HttpVersion;
import io.netty.handler.codec.http.QueryStringDecoder;
import io.netty.handler.codec.http.cookie.DefaultCookie;
import io.netty.handler.codec.http.cookie.ServerCookieDecoder;
import io.netty.handler.codec.http.cookie.ServerCookieEncoder;
import io.netty.handler.codec.http.websocketx.TextWebSocketFrame;
import io.netty.handler.codec.http.websocketx.WebSocketFrame;
import io.netty.handler.codec.http.websocketx.WebSocketServerHandshaker;
import io.netty.handler.codec.http.websocketx.WebSocketServerHandshakerFactory;
import io.netty.util.CharsetUtil;
import io.netty.util.concurrent.GlobalEventExecutor;

import java.net.InetSocketAddress;
import java.net.SocketException;
import java.security.SecureRandom;
import java.util.HexFormat;
import java.util.List;
import java.util.Map;
import java.util.function.Consumer;
import java.util.function.Supplier;

public final class InteropServer {
    public static final String AUTH_COOKIE = "omix_webui_auth";
    public static final String AUTH_PARAM = "omix_code";

    private final Gson gson = new Gson();
    private final InteropRouteRegistry routes = new InteropRouteRegistry();
    private final WebSocketEventBus socketEvents = new WebSocketEventBus();
    private final String authCode = createAuthCode();
    private final ChannelGroup webSockets = new DefaultChannelGroup(GlobalEventExecutor.INSTANCE);
    private final Supplier<String> screenSupplier;
    private final Consumer<String> screenAcknowledgement;
    private NioEventLoopGroup bossGroup;
    private NioEventLoopGroup workerGroup;
    private Channel serverChannel;
    private WebAssetBundle assets;
    private int port;

    public InteropServer(
            Supplier<String> screenSupplier,
            Consumer<String> screenAcknowledgement
    ) {
        this.screenSupplier = screenSupplier;
        this.screenAcknowledgement = screenAcknowledgement;
        registerCoreRoutes();
    }

    public void start() throws Exception {
        assets = new WebAssetBundle();
        bossGroup = new NioEventLoopGroup(1);
        workerGroup = new NioEventLoopGroup();

        ServerBootstrap bootstrap = new ServerBootstrap()
                .group(bossGroup, workerGroup)
                .channel(NioServerSocketChannel.class)
                .childOption(ChannelOption.TCP_NODELAY, true)
                .childHandler(new ChannelInitializer<SocketChannel>() {
                    @Override
                    protected void initChannel(SocketChannel channel) {
                        channel.pipeline().addLast(new HttpServerCodec());
                        channel.pipeline().addLast(new HttpObjectAggregator(4 * 1024 * 1024));
                        channel.pipeline().addLast(new InteropHandler());
                    }
                });

        serverChannel = bootstrap.bind(new InetSocketAddress("127.0.0.1", 0)).sync().channel();
        port = ((InetSocketAddress) serverChannel.localAddress()).getPort();
        Client.logger.info("WebUI interop server listening on {}", getBaseUrl());
    }

    public String getBaseUrl() {
        return "http://127.0.0.1:" + port;
    }

    public String getAuthenticatedBaseUrl() {
        return getBaseUrl() + "/?" + AUTH_PARAM + "=" + authCode;
    }

    public InteropRouteRegistry getRoutes() {
        return routes;
    }

    public WebSocketEventBus getSocketEvents() {
        return socketEvents;
    }

    public void broadcast(String name, JsonObject event) {
        JsonObject packet = new JsonObject();
        packet.addProperty("name", name);
        packet.add("event", event);
        webSockets.writeAndFlush(new TextWebSocketFrame(gson.toJson(packet)));
    }

    public void stop() {
        webSockets.close();
        if (serverChannel != null) {
            serverChannel.close();
        }
        if (workerGroup != null) {
            workerGroup.shutdownGracefully();
        }
        if (bossGroup != null) {
            bossGroup.shutdownGracefully();
        }
    }

    private final class InteropHandler extends SimpleChannelInboundHandler<Object> {
        private WebSocketServerHandshaker handshaker;

        @Override
        protected void channelRead0(ChannelHandlerContext context, Object message) {
            if (message instanceof FullHttpRequest request) {
                handleHttp(context, request);
            } else if (message instanceof WebSocketFrame frame) {
                handleWebSocket(context, frame);
            }
        }

        private void handleHttp(ChannelHandlerContext context, FullHttpRequest request) {
            QueryStringDecoder decoder = new QueryStringDecoder(request.uri());
            if (request.method().equals(HttpMethod.OPTIONS)) {
                send(context, request, response(HttpResponseStatus.NO_CONTENT, "", "text/plain"));
                return;
            }
            if ("/ws".equals(decoder.path()) && isWebSocketUpgrade(request)) {
                if (!isAuthenticated(request, decoder)) {
                    send(context, request, response(HttpResponseStatus.UNAUTHORIZED, "Authentication required", "text/plain"));
                    return;
                }
                WebSocketServerHandshakerFactory factory = new WebSocketServerHandshakerFactory(
                        getBaseUrl().replace("http", "ws") + "/ws",
                        null,
                        true,
                        4 * 1024 * 1024
                );
                handshaker = factory.newHandshaker(request);
                if (handshaker == null) {
                    WebSocketServerHandshakerFactory.sendUnsupportedVersionResponse(context.channel());
                } else {
                    handshaker.handshake(context.channel(), request);
                    webSockets.add(context.channel());
                }
                return;
            }

            boolean authenticated = isAuthenticated(request, decoder);
            if (!authenticated) {
                send(context, request, response(HttpResponseStatus.UNAUTHORIZED, "Authentication required", "text/plain"));
                return;
            }

            FullHttpResponse response;
            String path = decoder.path();
            if (path.startsWith("/api/")) {
                response = handleApi(request, decoder);
            } else if (request.method().equals(HttpMethod.GET)) {
                response = handleAsset(path);
            } else {
                response = response(HttpResponseStatus.METHOD_NOT_ALLOWED, "Method not allowed", "text/plain");
            }

            List<String> codes = decoder.parameters().get(AUTH_PARAM);
            if (codes != null && codes.contains(authCode)) {
                DefaultCookie cookie = new DefaultCookie(AUTH_COOKIE, authCode);
                cookie.setPath("/");
                cookie.setHttpOnly(true);
                cookie.setSameSite(io.netty.handler.codec.http.cookie.CookieHeaderNames.SameSite.Strict);
                response.headers().add(HttpHeaderNames.SET_COOKIE, ServerCookieEncoder.STRICT.encode(cookie));
            }
            send(context, request, response);
        }

        private FullHttpResponse handleApi(FullHttpRequest request, QueryStringDecoder decoder) {
            InteropResponse result = routes.dispatch(new InteropRequest(
                    request.method().name(),
                    decoder.path(),
                    decoder.parameters(),
                    parseJson(request)
            ));
            return response(result.status(), result.body(), result.contentType());
        }

        private FullHttpResponse handleAsset(String path) {
            String name = path.equals("/") ? "index.html" : path.substring(1);
            byte[] data = assets.get(name);
            if (data == null && !name.contains(".")) {
                data = assets.get("index.html");
            }
            if (data == null) {
                return response(HttpResponseStatus.NOT_FOUND, "Not found", "text/plain");
            }
            return response(HttpResponseStatus.OK, data, contentType(name));
        }

        private void handleWebSocket(ChannelHandlerContext context, WebSocketFrame frame) {
            if (!(frame instanceof TextWebSocketFrame text)) {
                return;
            }
            JsonObject packet;
            try {
                packet = gson.fromJson(text.text(), JsonObject.class);
            } catch (Exception ignored) {
                return;
            }
            if (packet != null && packet.has("name")) {
                JsonObject event = packet.has("event") && packet.get("event").isJsonObject()
                        ? packet.getAsJsonObject("event")
                        : new JsonObject();
                socketEvents.dispatch(
                        packet.get("name").getAsString(),
                        event,
                        (name, replyEvent) -> sendSocketEvent(context, name, replyEvent)
                );
            }
        }

        @Override
        public void channelInactive(ChannelHandlerContext context) {
            webSockets.remove(context.channel());
        }

        @Override
        public void exceptionCaught(ChannelHandlerContext context, Throwable cause) {
            if (!(cause instanceof SocketException)
                    || !"Connection reset".equals(cause.getMessage())) {
                Client.logger.error("WebUI interop connection failed", cause);
            }
            context.close();
        }
    }

    private void sendSocketEvent(ChannelHandlerContext context, String name, JsonObject event) {
        JsonObject response = new JsonObject();
        response.addProperty("name", name);
        response.add("event", event);
        context.writeAndFlush(new TextWebSocketFrame(gson.toJson(response)));
    }

    private boolean isAuthenticated(FullHttpRequest request, QueryStringDecoder decoder) {
        List<String> codes = decoder.parameters().get(AUTH_PARAM);
        if (codes != null && codes.contains(authCode)) {
            return true;
        }
        String cookieHeader = request.headers().get(HttpHeaderNames.COOKIE);
        if (cookieHeader == null) {
            return false;
        }
        return ServerCookieDecoder.STRICT.decode(cookieHeader).stream()
                .anyMatch(cookie -> AUTH_COOKIE.equals(cookie.name()) && authCode.equals(cookie.value()));
    }

    private static boolean isWebSocketUpgrade(FullHttpRequest request) {
        return HttpHeaderValues.WEBSOCKET.contentEqualsIgnoreCase(request.headers().get(HttpHeaderNames.UPGRADE));
    }

    private JsonObject parseJson(FullHttpRequest request) {
        try {
            return gson.fromJson(request.content().toString(CharsetUtil.UTF_8), JsonObject.class);
        } catch (Exception ignored) {
            return new JsonObject();
        }
    }

    private void registerCoreRoutes() {
        routes.get("/api/v1/client/info", ignored -> {
            JsonObject info = new JsonObject();
            info.addProperty("name", Client.name);
            info.addProperty("version", Client.version);
            info.addProperty("framework", "LiquidBounce-compatible WebUI");
            return InteropResponse.json(HttpResponseStatus.OK, info);
        });
        routes.get("/api/v1/client/virtualScreen", ignored -> {
            JsonObject screen = new JsonObject();
            screen.addProperty("name", screenSupplier.get());
            return InteropResponse.json(HttpResponseStatus.OK, screen);
        });
        routes.post("/api/v1/client/virtualScreen", request -> {
            JsonObject body = request.body();
            String name = body.has("name") ? body.get("name").getAsString() : "";
            if (!screenSupplier.get().equals(name)) {
                return InteropResponse.text(HttpResponseStatus.FORBIDDEN, "Wrong virtual screen");
            }
            screenAcknowledgement.accept(name);
            return InteropResponse.noContent();
        });
    }

    private static FullHttpResponse response(HttpResponseStatus status, String value, String contentType) {
        return response(status, value.getBytes(CharsetUtil.UTF_8), contentType);
    }

    private static FullHttpResponse response(HttpResponseStatus status, byte[] value, String contentType) {
        FullHttpResponse response = new DefaultFullHttpResponse(
                HttpVersion.HTTP_1_1,
                status,
                Unpooled.wrappedBuffer(value)
        );
        response.headers().set(HttpHeaderNames.CONTENT_TYPE, contentType);
        response.headers().setInt(HttpHeaderNames.CONTENT_LENGTH, value.length);
        response.headers().set(HttpHeaderNames.CACHE_CONTROL, "no-store");
        return response;
    }

    private static void send(ChannelHandlerContext context, FullHttpRequest request, FullHttpResponse response) {
        String origin = request.headers().get(HttpHeaderNames.ORIGIN);
        if (origin != null) {
            response.headers().set(HttpHeaderNames.ACCESS_CONTROL_ALLOW_ORIGIN, origin);
            response.headers().set(HttpHeaderNames.ACCESS_CONTROL_ALLOW_CREDENTIALS, "true");
            response.headers().set(HttpHeaderNames.VARY, HttpHeaderNames.ORIGIN);
        }
        response.headers().set(
                HttpHeaderNames.ACCESS_CONTROL_ALLOW_METHODS,
                "GET, POST, PUT, DELETE, OPTIONS"
        );
        response.headers().set(
                HttpHeaderNames.ACCESS_CONTROL_ALLOW_HEADERS,
                "Content-Type, Authorization"
        );
        boolean keepAlive = HttpUtil.isKeepAlive(request);
        if (keepAlive) {
            response.headers().set(HttpHeaderNames.CONNECTION, HttpHeaderValues.KEEP_ALIVE);
            context.writeAndFlush(response);
        } else {
            context.writeAndFlush(response).addListener(io.netty.channel.ChannelFutureListener.CLOSE);
        }
    }

    private static String contentType(String name) {
        if (name.endsWith(".html")) return "text/html; charset=UTF-8";
        if (name.endsWith(".js")) return "text/javascript; charset=UTF-8";
        if (name.endsWith(".css")) return "text/css; charset=UTF-8";
        if (name.endsWith(".json")) return "application/json; charset=UTF-8";
        if (name.endsWith(".svg")) return "image/svg+xml";
        if (name.endsWith(".png")) return "image/png";
        return "application/octet-stream";
    }

    private static String createAuthCode() {
        byte[] bytes = new byte[24];
        new SecureRandom().nextBytes(bytes);
        return HexFormat.of().formatHex(bytes);
    }
}
