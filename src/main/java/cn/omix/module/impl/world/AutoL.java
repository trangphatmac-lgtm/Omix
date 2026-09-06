package cn.omix.module.impl.world;

import cn.omix.Client;
import cn.omix.event.base.annotation.EventTarget;
import cn.omix.event.impl.AttackEvent;
import cn.omix.event.impl.UpdateEvent;
import cn.omix.event.impl.WorldEvent;
import cn.omix.module.Category;
import cn.omix.module.Module;
import cn.omix.module.value.impl.BoolValue;
import cn.omix.module.value.impl.ModeValue;
import cn.omix.module.value.impl.TextValue;
import net.minecraft.entity.player.PlayerEntity;

import java.util.Iterator;
import java.util.LinkedHashSet;
import java.util.Set;
import java.util.concurrent.ThreadLocalRandom;

public final class AutoL extends Module {
    private final ModeValue wordPattern = new ModeValue("Word Pattern", "Poem",
            "Poem", "Ma Ma", "Pride Plus", "Ci xiao gui", "Crystal PVP", "Clear", "English", "San Guo",
            "Classic", "Bratty", "Troll", "Custom");
    private final BoolValue nameInFront = new BoolValue("NameInFront", true);
    private final BoolValue sendL = new BoolValue("SendL", false);
    private final TextValue content = new TextValue("Content", "", () -> wordPattern.is("Custom"));
    private final ModeValue targetSource = new ModeValue("Target", "Client Name",
            "Client Name", "Account Name", "Custom");
    private final TextValue targetText = new TextValue("Target Text", "", () -> targetSource.is("Custom"));
    private final Set<PlayerEntity> enemies = new LinkedHashSet<>();

    public AutoL() {
        super("AutoL", Category.World);
    }

    @Override
    public String getSuffix() {
        return wordPattern.getValue();
    }

    @Override
    public void onEnable() {
        enemies.clear();
    }

    @Override
    public void onDisable() {
        enemies.clear();
    }

    @EventTarget
    public void onWorld(WorldEvent event) {
        enemies.clear();
    }

    @EventTarget
    public void onAttack(AttackEvent event) {
        if (!isEnabled() || event.isCancelled() || mc.player == null || mc.world == null) return;
        if (event.getEntity() instanceof PlayerEntity player
                && player != mc.player && player.isAlive() && !player.isRemoved()) {
            enemies.add(player);
        }
    }

    @EventTarget
    public void onUpdate(UpdateEvent event) {
        if (!isEnabled() || mc.player == null || mc.world == null) {
            enemies.clear();
            return;
        }

        Iterator<PlayerEntity> iterator = enemies.iterator();
        while (iterator.hasNext()) {
            PlayerEntity player = iterator.next();
            // Removal alone can mean a disconnect or leaving render distance, not a death.
            if (player.getHealth() <= 0.0F) {
                iterator.remove();
                String message = getRandomMessage().replace("<target>", getTargetText());
                if (nameInFront.getValue()) {
                    message = player.getName().getString() + " " + message;
                }
                if (sendL.getValue() && ThreadLocalRandom.current().nextInt(10) < 7) {
                    message = "Ｌ " + message;
                }
                // Custom text must fit a single vanilla chat packet.
                StringBuilder chat = new StringBuilder();
                for (int i = 0; i < message.length() && chat.length() < 256; i++) {
                    char character = message.charAt(i);
                    if (character >= ' ' && character != '\u007f' && character != '\u00a7') {
                        chat.append(character);
                    }
                }
                if (!chat.toString().isBlank()) {
                    mc.player.networkHandler.sendChatMessage(chat.toString());
                }
            } else if (player.isRemoved() || !mc.world.hasEntity(player)) {
                iterator.remove();
            }
        }
    }

    private String getRandomMessage() {
        if (wordPattern.is("Custom")) {
            return content.getValue().isBlank() ? "Custom message not set!" : content.getValue();
        }
        if (wordPattern.is("Classic")) {
            return randomMessage(CLASSIC_PREFIXES) + "，" + randomMessage(CLASSIC_SUFFIXES);
        }
        String[] messages = switch (wordPattern.getValue()) {
            case "Ma Ma" -> MAMA;
            case "Pride Plus" -> PRIDEPLUS;
            case "Ci xiao gui" -> CIXIAOGUI;
            case "Crystal PVP" -> CRYSTAL_PVP;
            case "Clear" -> CLEAR;
            case "English" -> ENGLISH;
            case "San Guo" -> SAN_GUO;
            case "Bratty" -> BRATTY;
            case "Troll" -> TROLL;
            default -> POEMS;
        };
        return randomMessage(messages);
    }

    private String getTargetText() {
        return switch (targetSource.getValue()) {
            case "Account Name" -> mc.getSession().getUsername();
            case "Custom" -> targetText.getValue();
            default -> Client.name;
        };
    }

    private static String randomMessage(String[] messages) {
        return messages[ThreadLocalRandom.current().nextInt(messages.length)];
    }

    private static final String[] CLASSIC_PREFIXES = {
            "你玩的很强",
            "兄弟这波可惜了",
            "有点意思",
            "我说实话这个客户端挺好的",
            "差点了这波",
            "兄弟有点东西啊",
            "这波操作确实可以",
            "说实话你差一点就赢了",
            "这波我还是认可你的",
            "你这个手法已经很不错了",
            "兄弟别急下一把还有机会",
            "刚才这波确实挺帅",
            "你玩的没什么问题",
            "这把只能说有来有回",
            "我承认你还是有实力的"
    };

    private static final String[] CLASSIC_SUFFIXES = {
            "但是你打不过<target>",
            "打不过<target>也是笑了",
            "<target>是无敌懂了吗。？",
            "我<target>都笑了",
            "可惜<target>启动了",
            "但是遇到<target>就结束了",
            "我开<target>你拿什么打",
            "但是<target>不允许你赢",
            "可惜你对面是<target>",
            "但是<target>还是更有实力",
            "我<target>甚至还没认真",
            "只能说<target>确实无敌",
            "但是你还没理解<target>",
            "碰到<target>输掉也正常",
            "下次记得绕着<target>走"
    };

    private static final String[] BRATTY = {
            "杂鱼杂鱼～连<target>都打不过呢♡",
            "这么快就倒下啦，真是无可救药呢♡<target>",
            "不会吧～你真的碰不到<target>呀♡",
            "杂鱼的操作被<target>看穿啦♡",
            "才坚持这么一会儿呀，<target>还没尽兴呢♡",
            "又输给<target>啦，杂鱼要好好反省哦♡",
            "嘴上那么厉害，结果还是被<target>拿下啦♡",
            "欸～杂鱼不会以为自己差点赢了吧♡<target>",
            "连<target>的一下都接不住，太弱啦♡",
            "杂鱼杂鱼～你的胜利被<target>藏起来啦♡",
            "这么努力还是输了，真可怜呢♡<target>",
            "不要急着躺下嘛，<target>还想再玩一会儿♡",
            "又被<target>欺负啦，杂鱼真没办法呢♡",
            "刚才的自信去哪里啦～被<target>打掉了吗♡",
            "想赢<target>？杂鱼还要再练一百年哦♡",
            "不会连反抗都做不到吧～<target>都失望啦♡",
            "杂鱼认真起来也只有这种程度吗♡<target>",
            "欸呀～又让<target>轻松得分啦♡",
            "被<target>盯上就不会动啦，真是杂鱼呢♡",
            "下一局也要来送给<target>哦，杂鱼♡",
            "还想跑呀～<target>可不会放过杂鱼哦♡",
            "输得这么可爱，<target>都不忍心笑啦♡",
            "杂鱼不会还没明白差距吧♡这可是<target>哦",
            "再怎么挣扎也赢不了<target>，真遗憾呢♡",
            "只会倒下的杂鱼～<target>记住你啦♡",
            "这么简单就被<target>骗到啦，笨笨的♡",
            "杂鱼的攻击软绵绵的～<target>完全没感觉♡",
            "欸～这就结束了吗，真是没用的杂鱼呢♡<target>",
            "被<target>连续拿下还不服气呀，杂鱼真可爱♡",
            "好弱好弱～<target>闭着眼都能赢呢♡",
            "杂鱼～杂鱼～连<target>都碰不到吗？♡",
            "诶～哥哥该不会真以为<target>会夸你吧？♡",
            "略略略～笨蛋才会在<target>面前说大话呢！♡",
            "呜哇～好逊哦，遇到<target>就满足了吗？♡",
            "诶嘿～被<target>说中了吧？都不敢动了呢～♡",
            "笨蛋哥哥又在做打赢<target>的白日梦吗？♡",
            "噗～这种程度就想让<target>认输？♡",
            "啊啦～输给<target>就生气了吗？真小气～♡",
            "哼！<target>才不会承认你很厉害呢！♡",
            "诶～这么容易就上当，果然是<target>的小杂鱼呢～♡",
            "呜～<target>才不是故意捣乱，只是觉得好玩嘛～♡",
            "略～笨蛋才会相信<target>会让你呢！♡",
            "啊呀～被发现了吗？但你就是拿<target>没办法～♡",
            "嘻嘻，哥哥输给<target>时的表情好有趣哦～♡",
            "咦哈哈哈哈～你该不会真觉得能赢<target>吧？♡",
            "哼！就算哥哥求我，<target>也不会道歉的！♡",
            "略略略～有本事来抓<target>呀～杂鱼哥哥～♡"
    };

    private static final String[] TROLL = {
            "你怎么又在偷玩电脑？我没有啊爷爷，我在学习。那这个人脱衣服干什么？我真的在学习啊。我靠，我怎么没有关语音输入。",
            "爷爷叫你写作业，你又在偷玩电脑了。哎哟我去，我刚才语音输入忘记关了。",
            "反对奶酪客户瑞，之前我花了70找人帮忙安装水印，（注：电脑是我爷爷的） 安装完后我爷爷的电脑就蓝屏了，然后被我爷爷打了一顿，",
            "国服hyp谁来。我用水印b72汉花版，带飞。",
            "g?是的话求",
            "来你先不要动，教你们一个绝招，假如队友吸铁石，或不捡钻石，你可以当熊孩子",
            "我再问最后一遍，水印中文版的配置到底该放在哪个文件夹",
            "小萌新想问你们为什么没被刚毅ban？？？？？？？？？？？？？？？？？？？？？？？？？？？？？？？？",
            "玩了好几把了每把都有挂。气死我了。成年人的崩溃只在一瞬间。希望这把没挂。南无啊弥陀佛。普萨保佑。",
            "英伟达今年在中国就一张卡也别想卖出去。就在前几天。华为发布了升腾950。全方位吊打RTX 5090。",
            "我再问最后一遍，<target>的配置到底放哪个文件夹里",
            "小萌新想问你们为什么可以玩得这么自然？？？？？？",
            "看着有点像某个熟悉的版本，好像又不是，这是啥呀",
            "没有飞起来的动画差评，我五十买的版本都有",
            "没有飞行差评，我50买的水影中文版都有飞行。",
            "不是哥们，你是挂呀，开局直接冲别人家，他们不就完了吗，为啥还打的跟绿的一样谨慎？",
            "这是新版吗？现在还开源不？？",
            "我想知道手机怎么装jvav版[doge][doge][doge]",
            "为啥有自动打方块啊？我靠，我说我怎么打不过你[辣眼睛]",
            "今天打起床遇到一把 开挂给我和朋友乱杀",
            "正常玩家：你这个老六。",
            "不是哥们，你开局直接冲别人家，他们不就结束了吗",
            "给我这个刚转一个月的新触控小萌新看呆了[疑惑]",
            "三星手机怎么玩[doge]",
            "哇，我要气死了呀，原来这个版本是免费的，我还花了五十",
            "秒人斧说：别低估了霸道的力量",
            "卡卡，不要试图衡量代价，因为你的一生本身就是神话",
            "卡卡，你把剑丢在泥里，那是留给这个世界最后的怜悯",
            "卡卡，真正的战士不需要剑",
            "卡卡，你要像水一样平静",
            "卡卡，像你这样可怕的存在也会留下眼泪吗",
            "卡卡，你想活出怎样的人生",
            "卡卡，你落的泪是否得到答案了呢",
            "卡卡，海的那边是什么",
            "卡卡，放下剑比拿起剑更需要勇气",
            "卡卡，你所浪费的今天，是昨日之人奢望的明天",
            "卡卡，作为地球上的神，你需要不断原谅每一个人",
            "卡卡，为了这个世界，振作起来",
            "不听卡卡言，吃亏在眼前，我89块钱的号开桂被ben了，早知道听卡卡学长的了，不开桂了",
            "我是中立玩家，周末有一起玩四十人相亲房的关注我",
            "我觉得可以在水印中文版里弄一个云原神",
            "怎么解开奶酪",
            "我电脑中奶酪毒了怎么办",
            "这个花雨停我见过你，好像叫watchdog",
            "哥们拿我做开头是什么意思？",
            "我上镜了，聪明的卡卡已经飞走了",
            "这么好，为什么不火？",
            "这谁拉我共创，我不认识你啊，兄弟们给他点点赞",
            "请问房间号是多少呀，我也想来和你们玩我的世界",
            "刚才这个人见到卡卡，吓得直接掉入虚空",
            "兄弟们，开武魂",
            "这人才是正常思路，知道什么时候应该飞起来",
            "这人太坏了，看着挺老实的原来坏事做尽",
            "空岛里不好打，起床里好打，四对四还是得看配合",
            "不是哥们，<target>都启动了你怎么还这么谨慎",
            "这个也没多强啊，来刚毅联机房918496，信不信你打不过我？？"
    };

    private static final String[] POEMS = {
            "立志用功如种树然，方其根芽，犹未有干；及其有干，尚未有枝；枝而后叶，叶而后花。",
            "骐骥一跃，不能十步；驽马十驾，功在不舍；锲而舍之，朽木不折；锲而不舍，金石可镂。",
            "天见其明，地见其光，君子贵其全也。",
            "只有功夫深，铁杵磨成针。",
            "一言既出，驷马难追。",
            "为一身谋则愚，而为天下则智。",
            "处其厚，不居其薄，处其实，不居其华。",
            "白沙在涅，与之俱黑。",
            "如果永远是晴天，土地也会布满裂痕。",
            "只有知识之海，才能载起成才之舟。",
            "谬论从门缝钻进，真理立于门前。",
            "自其变者而观之，则天地曾不能以一瞬；自其不变者而观之，则物与我皆无尽也。"
    };

    private static final String[] SAN_GUO = {
            "一破：卧龙出山，你已被SouthSide客户端击毙",
            "双连：一战成名，你已被SilenceFix客户端击毙",
            "三连：举世皆惊，你已被Myau客户端击毙",
            "四连：天下无敌，你已被Leader客户端击毙",
            "五连：诛天灭地，你已被Augustus客户端击毙"
    };

    private static final String[] MAMA = {"妈妈"};
    private static final String[] CLEAR = {"L"};

    private static final String[] PRIDEPLUS = {
            "嗨，我是风动，这是我的neibu神器，3000收neibu是我的秘密武器，花钱一分钟，赚钱两个月，不要告诉别人哦",
            "嗨，我是Pro，这是我的neibu神器，200整30个conf是我的秘密武器，花钱一分钟，赚钱两年半，不要告诉别人哦",
            "嗨，我是回想，这是我的fix神器，fix各种端是我的秘密武器，fix一小时，高兴一个月，不要告诉别人哦",
            "嗨，我是小职，这是我的cookies神器，3000+cookies是我的秘密武器，获取一秒钟，游戏一小时，不要告诉小手哦",
            "嗨，我是原批，这是我的启动神器，你说的对，但是原神启动是我的秘密武器，启动十分钟，充电五小时，不要告诉别人哦",
            "嗨，我是风动，这是我的抽烟神器，3000买下锐刻114514代是我的秘密武器，花钱一秒钟，抽烟一辈子，不要告诉丁真哦",
            "嗨，我是小手冰凉,这是我的Cherish,Cherish是我的秘密武器，出击一分钟，殴打两小时，不要告诉小手哦",
            "嗨，我是瓦瓦，这是我的pride+神器，是我的秘密武器，vel一分钟，死号两小时，不要告诉瓦瓦哦"
    };

    private static final String[] CIXIAOGUI = {
            "呐呐~杂鱼哥哥不会这样就被捉弄的不会说话了吧♡",
            "嘻嘻~杂鱼哥哥不会以为竖个大拇哥就能欺负我了吧~不会吧♡不会吧♡",
            "杂鱼哥哥怎么可能欺负得了别人呢~只能欺负自己哦♡~",
            "哥哥真是好欺负啊♡嘻嘻~",
            "哎♡~杂鱼说话就是无趣唉~",
            "呐呐~杂鱼哥哥发这个是想教育我吗~嘻嘻~怎么可能啊♡",
            "什么嘛~废柴哥哥会想这种事情啊~唔呃",
            "把你肮脏的目光拿开啦~很恶心哦♡",
            "咱的期待就是被你这样的笨蛋破坏了~♡",
            "诶~这么快就认输了？咱还没开始认真呢♡",
            "哥哥的操作破绽比芝士奶酪的洞还多哦~嘻嘻♡",
            "不会吧不会吧~这就是传说中的‘高手’吗？真是有够好笑的♡",
            "建议哥哥把游戏ID改成‘易推倒’呢~简直太合适了♡",
            "啊啦~这么简单的连招都接不住，哥哥是闭着眼睛在玩吗？",
            "需要咱放点水吗？毕竟欺负残疾人是不好的呢~唔噗♡",
            "哥哥的失败数据咱会好好收藏的~这可是珍贵的杂鱼样本呢♡",
            "知道为什么输得这么惨吗？因为从开始到现在的每一步都在咱计算中哦~",
            "快去论坛发帖‘被美少女暴打怎么办’吧~咱会去给你点赞的♡"
    };

    private static final String[] CRYSTAL_PVP = {
            "鼠标明天到，触摸板打的", "转人工", "收徒", "不收徒", "有真人吗", "墨镜上车", "素材局", "不接单", "接单",
            "征婚", "4399?", "暂时不考虑打职业", "bot?", "叫你家大人来打", "假肢上门安装", "浪费我的网费",
            "不收残疾人", "下课", "自己找差距", "不接代", "代+", "这样的治好了也流口水", "人机", "人机怎么调难度啊",
            "只收不被0封的", "Bot吗这是", "领养", "纳亲", "正视差距", "近亲繁殖?", "我玩的是新手教程?",
            "来调灵敏度的", "来调参数的", "小号", "不是本人别加", "下次记得晚点玩", "随便玩玩,不带妹", "扣1上车"
    };

    private static final String[] ENGLISH = {
            "Good fight! Well played.",
            "Nice one — keep it up!",
            "GG, that was fun.",
            "Close one! Wanna rematch?",
            "Not bad, you surprised me.",
            "Sweet move! Respect.",
            "That was intense, great game!",
            "Careful next time — watch your back!",
            "Wow, that was clean. Props.",
            "You got lucky — nice try!",
            "Play again sometime, champ!",
            "Who taught you that combo? Impressive.",
            "Nice aim — for a stormtrooper.",
            "Blink twice if you need a tutorial.",
            "I’d explain what you did wrong, but it’s more fun watching.",
            "Do you want a medal or just the respawn?",
            "Save that strategy for casual mode, okay?",
            "I thought I queued into hard mode. Turns out it was you.",
            "You’re the plot twist nobody asked for.",
            "Legend says your K/D is a myth.",
            "I’d call you lucky, but that would be generous.",
            "Is your controller plugged in? Asking for science.",
            "Cute try — did it come with instructions?",
            "Next time bring snacks; this is getting embarrassing."
    };
}
