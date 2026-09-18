package com.antifraud.entity;

import java.util.ArrayList;
import java.util.List;

/**
 * 统一诈骗类型枚举：湖州 15 种涉诈网址分类 + 现有 9 种文本分类，去重合并为 16 类。
 * 全文统一引用此枚举，避免分类散落多处。
 */
public enum FraudType {
    IMPERSONATE_POLICE("冒充公检法", "安全账户","涉嫌洗钱","公检法","检察院","法院","公安局","逮捕令","通缉令","资金清查","冻结账户","案件"),
    IMPERSONATE_CUSTOMER("冒充客服退款", "客服","退款","理赔","质量问题","赔付","关闭会员","取消订单","加微信退赔","保证金"),
    IMPERSONATE_ACQUAINTANCE("冒充熟人", "我是你","老同学","换号了","急需用钱","借点钱","汇款","亲戚"),
    FAKE_LOAN("虚假贷款", "贷款","无抵押","低息","秒批","放款","手续费","解冻费","额度"),
    BRUSH_ORDER("刷单诈骗", "刷单","返现","佣金","做任务","兼职","垫付","本金","任务单","派单"),
    FAKE_INVESTMENT("虚假投资理财", "投资","理财","高收益","稳赚","内幕","荐股","期货","虚拟币","平台"),
    PIG_BUTCHERING("杀猪盘", "交友","网恋","恋爱","带你赚钱","内部消息","感情","博彩","投资"),
    PORN_DATING("色情交友", "色情交友","约炮","同城交友","色情服务","上门服务","裸聊交友"),
    PORN_GAMBLING("色情博彩", "色情博彩","赌博","押注","百家乐","棋牌","充值返利"),
    FAKE_SHOPPING("虚假购物", "虚假购物","低价","秒杀","海外代购","货到付款","退款链接","购物返利"),
    GAME_TRADE("游戏产品交易", "游戏交易","账号交易","装备","代练","游戏币","交易平台"),
    PHISHING("钓鱼盗号", "点击链接","验证码","改密","账号异常","重新激活","官网升级","短信验证","登录异常"),
    NUDE_EXTORTION("裸聊敲诈", "裸聊","视频","隐私","敲诈","否则公开","截图","通讯录"),
    ANTI_BLOCK("防封系统", "防封","解封","防风控","封号申诉","反侦察"),
    BLACK_GRAY("黑灰产", "黑灰产","洗钱","跑分","刷流水","收卡","租号"),
    ILLEGAL_DISTRIBUTION("非法分发", "非法分发","下载App","推广链接","安装包","破解版","外挂");

    private final String displayName;
    private final List<String> keywords;

    FraudType(String displayName, String... keywords) {
        this.displayName = displayName;
        this.keywords = List.of(keywords);
    }

    public String getDisplayName() { return displayName; }
    public List<String> getKeywords() { return keywords; }

    /** 所有诈骗类型名称（用于 LLM 候选列表 / 前端下拉 / 涉诈网址分类取值） */
    public static List<String> names() {
        List<String> n = new ArrayList<>();
        for (FraudType t : values()) n.add(t.displayName);
        return n;
    }

    /** 判断给定名称是否为合法的诈骗类型（用于收敛 LogEvent.fraudType 取值） */
    public static boolean isKnown(String name) {
        if (name == null) return false;
        for (FraudType t : values()) if (t.displayName.equals(name)) return true;
        return false;
    }
}
