void onLoad() {
    commands.register("scripthello [name]", args -> log("Hello " + (args.length == 0 ? "Omix" : args[0])), "scripthello", "shello")
        .completions(args -> java.util.List.of("Omix", "world"));
}
