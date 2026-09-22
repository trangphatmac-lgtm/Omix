void onLoad() {
    commands.register("proxyservices", args -> {
        if (!fisproxy.hasApiKey()) { log("请先在客户端配置 FisProxy"); return; }
        fisproxy.services().whenComplete((services, error) -> {
            if (!script.active()) return;
            tasks.client(() -> log(error == null ? services : error.toString()));
        });
    }, "proxyservices");
}
