package cn.omix.module;

import lombok.AllArgsConstructor;
import lombok.Getter;

@Getter
@AllArgsConstructor
public enum Category {
    Combat("Combat"),
    Exploits("Exploit"),
    Move("Move"),
    Player("Player"),
    World("World"),
    Render("Render");

    public final String name;
}
