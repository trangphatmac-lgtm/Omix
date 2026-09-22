package cn.omix.script.api;

/** Typed return-value hook for built-in behavior that is called outside the event bus. */
public record ModeHook<I, O>(String name, Class<I> inputType, Class<O> outputType) {}
