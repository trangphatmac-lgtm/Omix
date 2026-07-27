package cn.omix.management;

import cn.omix.util.IMinecraft;
import lombok.Getter;

import java.util.concurrent.CopyOnWriteArrayList;

public class FriendManager implements IMinecraft {
    @Getter
    private static final CopyOnWriteArrayList<String> friends = new CopyOnWriteArrayList<>();

    public FriendManager() {
        instance.getEventManager().register(this);
    }

    public void addFriend(String name) {
        if (!friends.contains(name)) {
            friends.add(name);
        }
    }

    public void removeFriend(String name) {
        friends.remove(name);
    }

    public boolean isFriend(String name) {
        return friends.contains(name);
    }
}